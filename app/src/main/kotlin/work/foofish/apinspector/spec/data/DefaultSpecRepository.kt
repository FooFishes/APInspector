package work.foofish.apinspector.spec.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import work.foofish.apinspector.core.coroutines.DefaultDispatcher
import work.foofish.apinspector.environment.data.toEntity
import work.foofish.apinspector.history.HistoryRepository
import work.foofish.apinspector.openapi.HttpMethod
import work.foofish.apinspector.openapi.OpenApiParseResult
import work.foofish.apinspector.openapi.OpenApiParser
import work.foofish.apinspector.openapi.OperationDiff
import work.foofish.apinspector.openapi.OperationKey
import work.foofish.apinspector.openapi.request.alignedTo
import work.foofish.apinspector.openapi.request.environmentTemplates
import work.foofish.apinspector.request.data.decodeInput
import work.foofish.apinspector.request.data.encodeInput
import work.foofish.apinspector.spec.DocumentLoad
import work.foofish.apinspector.spec.FavoriteOperationRepository
import work.foofish.apinspector.spec.ImportFailure
import work.foofish.apinspector.spec.ImportOutcome
import work.foofish.apinspector.spec.ImportSource
import work.foofish.apinspector.spec.LoadedDocument
import work.foofish.apinspector.spec.RefreshPreparation
import work.foofish.apinspector.spec.RefreshPreview
import work.foofish.apinspector.spec.SpecId
import work.foofish.apinspector.spec.SpecOrigin
import work.foofish.apinspector.spec.SpecRepository
import work.foofish.apinspector.spec.SpecSummary
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultSpecRepository
    @Inject
    constructor(
        private val dao: SpecDao,
        private val store: SpecContentStore,
        private val reader: SpecSourceReader,
        private val history: HistoryRepository,
        @DefaultDispatcher private val parsing: CoroutineDispatcher,
        private val clock: Clock,
    ) : SpecRepository,
        FavoriteOperationRepository {
        private val cache = DocumentCache(CACHED_DOCUMENTS)

        override val specs: Flow<List<SpecSummary>> = dao.observeAll().map { specs -> specs.map(SpecEntity::toSummary) }

        override fun spec(id: SpecId): Flow<SpecSummary?> = dao.observe(id.value).map { it?.toSummary() }

        override suspend fun import(source: ImportSource): ImportOutcome {
            val read =
                when (val result = reader.read(source)) {
                    is SpecSourceReader.Result.Failed -> return ImportOutcome.Failed(result.failure)
                    is SpecSourceReader.Result.Read -> result
                }
            val content =
                when (val parsed = parse(read.text)) {
                    is Parsed.Failed -> return ImportOutcome.Failed(parsed.failure)
                    is Parsed.Succeeded -> parsed.content
                }
            val id = SpecId(UUID.randomUUID().toString())
            val environments = content.document.environmentTemplates().mapIndexed { position, template -> template.toEntity(id, position) }
            val entity = newEntity(id, content, read.origin, clock.millis()).copy(currentEnvironmentId = environments.firstOrNull()?.id)
            store.write(id, read.text)
            var inserted = false
            try {
                dao.insertImported(entity, environments)
                inserted = true
            } finally {
                // 元数据写入失败（含取消）时不留下孤立的原文文件。
                if (!inserted) withContext(NonCancellable) { store.delete(id) }
            }
            cache.put(id, entity.updatedAt, content)
            return ImportOutcome.Imported(id, entity.title, content.warnings)
        }

        override suspend fun document(id: SpecId): DocumentLoad {
            val entity = dao.find(id.value) ?: return DocumentLoad.Missing
            return cache.get(id, entity.updatedAt)?.let(DocumentLoad::Loaded) ?: loadStored(id, entity.updatedAt)
        }

        /** 缓存未命中时读取并解析原文，成功后写入缓存。 */
        private suspend fun loadStored(
            id: SpecId,
            version: Long,
        ): DocumentLoad {
            val text = store.read(id) ?: return DocumentLoad.Failed(ImportFailure.StoredContentMissing)
            return when (val parsed = parse(text)) {
                is Parsed.Failed -> DocumentLoad.Failed(parsed.failure)
                is Parsed.Succeeded -> DocumentLoad.Loaded(parsed.content).also { cache.put(id, version, parsed.content) }
            }
        }

        override suspend fun prepareRefresh(id: SpecId): RefreshPreparation {
            val entity = dao.find(id.value) ?: return RefreshPreparation.Failed(ImportFailure.StoredContentMissing)
            val url = checkNotNull(entity.originValue?.takeIf { entity.originType == SpecOriginType.URL }) { "只有 URL 来源的 API 文档可以刷新" }
            val read =
                when (val result = reader.read(ImportSource.Url(url))) {
                    is SpecSourceReader.Result.Failed -> return RefreshPreparation.Failed(result.failure)
                    is SpecSourceReader.Result.Read -> result
                }
            val content =
                when (val parsed = parse(read.text)) {
                    is Parsed.Failed -> return RefreshPreparation.Failed(parsed.failure)
                    is Parsed.Succeeded -> parsed.content
                }
            // 当前内容无法读取时（如存储损坏），把新文档中的接口都视为新增。
            val current = (document(id) as? DocumentLoad.Loaded)?.content?.document
            val diff =
                current?.let { OperationDiff.between(it, content.document) }
                    ?: OperationDiff(added = content.document.operations.map { it.key }, removed = emptyList(), modified = emptyList())
            val withPresets =
                dao.allPresets(id.value).mapNotNullTo(HashSet()) { preset ->
                    HttpMethod.of(preset.method)?.let {
                        OperationKey(it, preset.path)
                    }
                }
            return RefreshPreparation.Ready(
                RefreshPreview(
                    specId = id,
                    diff = diff,
                    removedWithPresets = diff.removed.count { it in withPresets },
                    serversChanged = current != null && current.servers != content.document.servers,
                    source = read.text,
                    content = content,
                ),
            )
        }

        override suspend fun applyRefresh(preview: RefreshPreview) {
            val entity = dao.find(preview.specId.value) ?: return
            val document = preview.content.document
            val updated =
                entity.copy(
                    title = document.info.title,
                    version = document.info.version,
                    operationCount = document.operations.size,
                    updatedAt = clock.millis(),
                )
            val storedWorking = dao.allWorking(preview.specId.value)
            val storedPresets = dao.allPresets(preview.specId.value)
            val (working, presets) =
                withContext(parsing) {
                    val operations = document.operations.associateBy { it.key }

                    // 已消失的接口返回 null：它们的数据随刷新删除。
                    fun align(
                        method: String,
                        path: String,
                        input: String,
                    ): String? {
                        val operation = HttpMethod.of(method)?.let { operations[OperationKey(it, path)] } ?: return null
                        return encodeInput(decodeInput(input).alignedTo(operation, document))
                    }
                    storedWorking.mapNotNull { stored ->
                        align(stored.method, stored.path, stored.input)?.let { stored.copy(input = it) }
                    } to
                        storedPresets.mapNotNull { stored ->
                            align(stored.method, stored.path, stored.input)?.let { stored.copy(input = it) }
                        }
                }
            store.write(preview.specId, preview.source)
            dao.applyRefresh(updated, preview.diff.removed.map { it.toFavoriteEntity(preview.specId) }, working, presets)
            cache.put(preview.specId, updated.updatedAt, preview.content)
        }

        override suspend fun setPinned(
            id: SpecId,
            pinned: Boolean,
        ) {
            dao.setPinnedAt(id.value, if (pinned) clock.millis() else null)
        }

        override suspend fun delete(id: SpecId) {
            // 记录随外键级联删除，但缓存目录中的 Body 文件需要另行删除。
            history.clear(id)
            dao.delete(id.value)
            store.delete(id)
            cache.remove(id)
        }

        override fun favorites(specId: SpecId): Flow<Set<OperationKey>> =
            dao.observeFavorites(specId.value).map { favorites ->
                favorites.mapNotNullTo(HashSet()) { favorite -> HttpMethod.of(favorite.method)?.let { OperationKey(it, favorite.path) } }
            }

        override suspend fun setFavorite(
            specId: SpecId,
            key: OperationKey,
            favorite: Boolean,
        ) {
            if (favorite) {
                dao.insertFavorite(key.toFavoriteEntity(specId))
            } else {
                dao.deleteFavorite(specId.value, key.method.name, key.path)
            }
        }

        private suspend fun parse(text: String): Parsed =
            when (val result = withContext(parsing) { OpenApiParser.parse(text) }) {
                is OpenApiParseResult.Failure -> Parsed.Failed(ImportFailure.Unparseable(result.error))
                is OpenApiParseResult.Success -> Parsed.Succeeded(LoadedDocument(result.document, result.warnings))
            }

        private sealed interface Parsed {
            data class Succeeded(
                val content: LoadedDocument,
            ) : Parsed

            data class Failed(
                val failure: ImportFailure,
            ) : Parsed
        }

        private companion object {
            /** 解析大型文档较慢，缓存最近打开的几份。 */
            const val CACHED_DOCUMENTS = 4
        }
    }

private fun newEntity(
    id: SpecId,
    content: LoadedDocument,
    origin: SpecOrigin,
    now: Long,
): SpecEntity =
    SpecEntity(
        id = id.value,
        title = content.document.info.title,
        version = content.document.info.version,
        originType =
            when (origin) {
                is SpecOrigin.File -> SpecOriginType.FILE
                is SpecOrigin.Url -> SpecOriginType.URL
                SpecOrigin.Pasted -> SpecOriginType.PASTED
            },
        originValue =
            when (origin) {
                is SpecOrigin.File -> origin.displayName
                is SpecOrigin.Url -> origin.url
                SpecOrigin.Pasted -> null
            },
        operationCount = content.document.operations.size,
        pinnedAt = null,
        updatedAt = now,
    )

private fun SpecEntity.toSummary(): SpecSummary =
    SpecSummary(
        id = SpecId(id),
        title = title,
        version = version,
        origin =
            when (originType) {
                SpecOriginType.FILE -> SpecOrigin.File(originValue)
                SpecOriginType.URL -> SpecOrigin.Url(checkNotNull(originValue) { "URL 来源缺少地址：$id" })
                SpecOriginType.PASTED -> SpecOrigin.Pasted
            },
        operationCount = operationCount,
        pinned = pinnedAt != null,
        updatedAt = Instant.ofEpochMilli(updatedAt),
    )

private fun OperationKey.toFavoriteEntity(specId: SpecId): FavoriteOperationEntity =
    FavoriteOperationEntity(specId.value, method.name, path)

/** 最近使用的已解析文档；以更新时间区分版本，刷新后旧版本自然失效。 */
private class DocumentCache(
    private val capacity: Int,
) {
    private data class Entry(
        val version: Long,
        val content: LoadedDocument,
    )

    private val mutex = Mutex()
    private val entries = LinkedHashMap<SpecId, Entry>(capacity, LOAD_FACTOR, true)

    suspend fun get(
        id: SpecId,
        version: Long,
    ): LoadedDocument? = mutex.withLock { entries[id]?.takeIf { it.version == version }?.content }

    suspend fun put(
        id: SpecId,
        version: Long,
        content: LoadedDocument,
    ) {
        mutex.withLock {
            entries[id] = Entry(version, content)
            while (entries.size > capacity) entries.remove(entries.keys.first())
        }
    }

    suspend fun remove(id: SpecId) {
        mutex.withLock { entries.remove(id) }
    }

    private companion object {
        const val LOAD_FACTOR = 0.75f
    }
}
