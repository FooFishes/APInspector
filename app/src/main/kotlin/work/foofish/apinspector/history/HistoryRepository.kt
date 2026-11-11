package work.foofish.apinspector.history

import kotlinx.coroutines.flow.Flow
import work.foofish.apinspector.environment.EnvironmentId
import work.foofish.apinspector.openapi.request.HttpRequest
import work.foofish.apinspector.openapi.request.RequestInput
import work.foofish.apinspector.request.RequestRef
import work.foofish.apinspector.spec.SpecId
import java.io.File
import java.time.Duration
import java.time.Instant

/** 请求记录的存取（PRD 7.1）。 */
interface HistoryRepository {
    /**
     * 从新到旧。[ref] 不为 `null` 时只列出该请求的记录，否则列出 [specId] 的全部记录（`null` 为临时请求）。
     */
    fun summaries(
        specId: SpecId?,
        ref: RequestRef?,
    ): Flow<List<HistorySummary>>

    fun entry(id: HistoryEntryId): Flow<HistoryEntry?>

    /** 某个请求最近的一条记录（"响应" Tab）。 */
    fun latest(ref: RequestRef): Flow<HistoryEntry?>

    /** 保存一条记录，并淘汰超出上限的最旧记录。 */
    suspend fun record(entry: NewHistoryEntry): HistoryEntryId

    /** 清空 API 文档（`null` 为临时请求）的全部记录及其 Body 文件。 */
    suspend fun clear(specId: SpecId?)

    /** Body 文件；已被清理时为 `null`。 */
    suspend fun bodyFile(body: StoredBody): File?
}

/** 待保存的请求记录。 */
data class NewHistoryEntry(
    val ref: RequestRef,
    val environmentId: EnvironmentId?,
    val environmentName: String?,
    val source: RequestSource,
    val presetName: String?,
    val sentAt: Instant,
    val duration: Duration,
    val request: HttpRequest,
    val input: RequestInput?,
    val outcome: Outcome,
)

/** 响应缓存（PRD 6 设置页）。 */
interface ResponseCache {
    /** 已用空间（字节）。 */
    suspend fun size(): Long

    /** 删除全部响应 Body 文件；请求记录保留。 */
    suspend fun clear()
}
