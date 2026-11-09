package work.foofish.apinspector.testing

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import work.foofish.apinspector.openapi.OperationKey
import work.foofish.apinspector.spec.DocumentLoad
import work.foofish.apinspector.spec.FavoriteOperationRepository
import work.foofish.apinspector.spec.ImportOutcome
import work.foofish.apinspector.spec.ImportSource
import work.foofish.apinspector.spec.LoadedDocument
import work.foofish.apinspector.spec.RefreshPreparation
import work.foofish.apinspector.spec.RefreshPreview
import work.foofish.apinspector.spec.SpecId
import work.foofish.apinspector.spec.SpecRepository
import work.foofish.apinspector.spec.SpecSummary

/** 内存中的仓库。导入与刷新拉取的结果由测试通过 [CompletableDeferred] 控制完成时机。 */
class FakeSpecRepository :
    SpecRepository,
    FavoriteOperationRepository {
    private val summaries = MutableStateFlow(emptyList<SpecSummary>())
    private val contents = mutableMapOf<SpecId, DocumentLoad>()
    private val favoriteKeys = MutableStateFlow(emptyMap<SpecId, Set<OperationKey>>())

    val imports = mutableListOf<ImportSource>()
    var importResult = CompletableDeferred<ImportOutcome>()
    var refreshResult = CompletableDeferred<RefreshPreparation>()
    val appliedRefreshes = mutableListOf<RefreshPreview>()
    var documentLoads = 0
        private set

    fun put(
        summary: SpecSummary,
        content: LoadedDocument? = null,
    ) {
        content?.let { contents[summary.id] = DocumentLoad.Loaded(it) }
        summaries.update { list -> list.filterNot { it.id == summary.id } + summary }
    }

    fun putLoad(
        id: SpecId,
        load: DocumentLoad,
    ) {
        contents[id] = load
    }

    override val specs: Flow<List<SpecSummary>> = summaries

    override fun spec(id: SpecId): Flow<SpecSummary?> = summaries.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun import(source: ImportSource): ImportOutcome {
        imports += source
        return importResult.await()
    }

    override suspend fun document(id: SpecId): DocumentLoad {
        documentLoads++
        return contents[id] ?: DocumentLoad.Missing
    }

    override suspend fun prepareRefresh(id: SpecId): RefreshPreparation = refreshResult.await()

    override suspend fun applyRefresh(preview: RefreshPreview) {
        appliedRefreshes += preview
        contents[preview.specId] = DocumentLoad.Loaded(preview.content)
        summaries.update { list ->
            list.map { if (it.id == preview.specId) it.copy(updatedAt = it.updatedAt.plusSeconds(1)) else it }
        }
    }

    override suspend fun setPinned(
        id: SpecId,
        pinned: Boolean,
    ) {
        summaries.update { list -> list.map { if (it.id == id) it.copy(pinned = pinned) else it } }
    }

    override suspend fun delete(id: SpecId) {
        contents.remove(id)
        summaries.update { list -> list.filterNot { it.id == id } }
    }

    override fun favorites(specId: SpecId): Flow<Set<OperationKey>> = favoriteKeys.map { it[specId].orEmpty() }

    override suspend fun setFavorite(
        specId: SpecId,
        key: OperationKey,
        favorite: Boolean,
    ) {
        favoriteKeys.update { all ->
            val current = all[specId].orEmpty()
            all + (specId to if (favorite) current + key else current - key)
        }
    }
}
