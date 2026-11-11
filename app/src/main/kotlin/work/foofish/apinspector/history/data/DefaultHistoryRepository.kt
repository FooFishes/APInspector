package work.foofish.apinspector.history.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import work.foofish.apinspector.core.db.StorageJson
import work.foofish.apinspector.custom.CustomRequestId
import work.foofish.apinspector.environment.EnvironmentId
import work.foofish.apinspector.history.HistoryEntry
import work.foofish.apinspector.history.HistoryEntryId
import work.foofish.apinspector.history.HistoryRepository
import work.foofish.apinspector.history.HistorySummary
import work.foofish.apinspector.history.NetworkError
import work.foofish.apinspector.history.NewHistoryEntry
import work.foofish.apinspector.history.Outcome
import work.foofish.apinspector.history.OutcomeKind
import work.foofish.apinspector.history.ReceivedResponse
import work.foofish.apinspector.history.ResponseCache
import work.foofish.apinspector.history.StoredBody
import work.foofish.apinspector.history.Timings
import work.foofish.apinspector.openapi.HttpMethod
import work.foofish.apinspector.openapi.OperationKey
import work.foofish.apinspector.openapi.request.Field
import work.foofish.apinspector.openapi.request.HttpRequest
import work.foofish.apinspector.request.RequestRef
import work.foofish.apinspector.request.data.decodeInput
import work.foofish.apinspector.request.data.encodeInput
import work.foofish.apinspector.spec.SpecId
import java.io.File
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultHistoryRepository
    @Inject
    constructor(
        private val dao: HistoryDao,
        private val bodies: ResponseBodyStore,
    ) : HistoryRepository,
        ResponseCache {
        override fun summaries(
            specId: SpecId?,
            ref: RequestRef?,
        ): Flow<List<HistorySummary>> {
            val rows =
                when (ref) {
                    null -> dao.observeSummaries(specId?.value)
                    is RequestRef.Operation -> dao.observeOperationSummaries(ref.specId.value, ref.key.method.name, ref.key.path)
                    is RequestRef.Custom -> dao.observeCustomSummaries(ref.id.value)
                }
            return rows.map { list -> list.mapNotNull { it.toSummary() } }
        }

        override fun entry(id: HistoryEntryId): Flow<HistoryEntry?> = dao.observe(id.value).map { it?.toEntry() }

        override fun latest(ref: RequestRef): Flow<HistoryEntry?> =
            when (ref) {
                is RequestRef.Operation -> dao.observeLatest(ref.specId.value, ref.key.method.name, ref.key.path)
                is RequestRef.Custom -> dao.observeLatestCustom(ref.id.value)
            }.map { it?.toEntry() }

        override suspend fun record(entry: NewHistoryEntry): HistoryEntryId {
            val (id, evicted) = dao.insertAndTrim(entry.toEntity(), MAX_ENTRIES_PER_SPEC)
            bodies.delete(evicted)
            return HistoryEntryId(id)
        }

        override suspend fun clear(specId: SpecId?) {
            val files = dao.bodyFiles(specId?.value)
            dao.clear(specId?.value)
            bodies.delete(files)
        }

        override suspend fun bodyFile(body: StoredBody): File? = bodies.existing(body.fileName)

        override suspend fun size(): Long = bodies.size()

        override suspend fun clear() {
            bodies.clear()
        }

        private companion object {
            /** 每个 API 文档最多保留的记录数（PRD 7.1）。 */
            const val MAX_ENTRIES_PER_SPEC = 500
        }
    }

private val HeadersSerializer = ListSerializer(Field.serializer())
private const val OPERATION_PATH_PREFIX = "/"

private fun HistorySummaryRow.toSummary(): HistorySummary? =
    HttpMethod.of(method)?.let { method ->
        HistorySummary(
            id = HistoryEntryId(id),
            method = method,
            path = path,
            custom = customId != null,
            sentAt = Instant.ofEpochMilli(sentAt),
            duration = Duration.ofMillis(durationMillis),
            statusCode = statusCode,
            outcome = outcome,
            size = bodyTotal,
        )
    }

/** 损坏的记录（方法名不合法、接口记录缺少所属文档）忽略。 */
private fun HistoryEntity.toEntry(): HistoryEntry? {
    val method = HttpMethod.of(method) ?: return null
    val spec = specId?.let(::SpecId)
    val ref =
        when {
            customId != null -> RequestRef.Custom(CustomRequestId(customId), spec)
            spec != null && path.startsWith(OPERATION_PATH_PREFIX) -> RequestRef.Operation(spec, OperationKey(method, path))
            else -> return null
        }
    return HistoryEntry(
        id = HistoryEntryId(id),
        ref = ref,
        method = method,
        path = path,
        environmentId = environmentId?.let(::EnvironmentId),
        environmentName = environmentName,
        source = source,
        presetName = presetName,
        sentAt = Instant.ofEpochMilli(sentAt),
        duration = Duration.ofMillis(durationMillis),
        request = StorageJson.decodeFromString(HttpRequest.serializer(), request),
        input = input?.let(::decodeInput),
        outcome = outcome(),
    )
}

private fun HistoryEntity.outcome(): Outcome =
    when (outcome) {
        OutcomeKind.CANCELLED -> {
            Outcome.Cancelled
        }

        OutcomeKind.FAILED -> {
            Outcome.Failed(NetworkError(checkNotNull(errorKind) { "失败的记录缺少原因：$id" }, errorDetail))
        }

        OutcomeKind.RECEIVED -> {
            Outcome.Received(
                ReceivedResponse(
                    statusCode = checkNotNull(statusCode) { "响应记录缺少状态码：$id" },
                    statusMessage = statusMessage.orEmpty(),
                    protocol = protocol.orEmpty(),
                    finalUrl = finalUrl.orEmpty(),
                    headers = responseHeaders?.let { StorageJson.decodeFromString(HeadersSerializer, it) }.orEmpty(),
                    contentType = contentType,
                    body = bodyFile?.let { StoredBody(it, bodyTotal ?: 0, bodyStored ?: 0) },
                    timings = Timings(dnsMillis.duration(), connectMillis.duration(), tlsMillis.duration(), firstByteMillis.duration()),
                ),
            )
        }
    }

private fun Long?.duration(): Duration? = this?.let(Duration::ofMillis)

private fun NewHistoryEntry.toEntity(): HistoryEntity {
    val response = (outcome as? Outcome.Received)?.response
    val error = (outcome as? Outcome.Failed)?.error
    return HistoryEntity(
        specId = ref.specId?.value,
        customId = (ref as? RequestRef.Custom)?.id?.value,
        method = request.method.name,
        path =
            when (ref) {
                is RequestRef.Operation -> ref.key.path
                is RequestRef.Custom -> request.url
            },
        environmentId = environmentId?.value,
        environmentName = environmentName,
        source = source,
        presetName = presetName,
        sentAt = sentAt.toEpochMilli(),
        durationMillis = duration.toMillis(),
        request = StorageJson.encodeToString(HttpRequest.serializer(), request),
        input = input?.let(::encodeInput),
        outcome =
            when (outcome) {
                is Outcome.Received -> OutcomeKind.RECEIVED
                is Outcome.Failed -> OutcomeKind.FAILED
                Outcome.Cancelled -> OutcomeKind.CANCELLED
            },
        statusCode = response?.statusCode,
        statusMessage = response?.statusMessage,
        protocol = response?.protocol,
        finalUrl = response?.finalUrl,
        responseHeaders = response?.headers?.let { StorageJson.encodeToString(HeadersSerializer, it) },
        contentType = response?.contentType,
        bodyFile = response?.body?.fileName,
        bodyTotal = response?.body?.totalBytes,
        bodyStored = response?.body?.storedBytes,
        dnsMillis = response?.timings?.dns?.toMillis(),
        connectMillis = response?.timings?.connect?.toMillis(),
        tlsMillis = response?.timings?.tls?.toMillis(),
        firstByteMillis = response?.timings?.firstByte?.toMillis(),
        errorKind = error?.kind,
        errorDetail = error?.detail,
    )
}
