package work.foofish.apinspector.history

import work.foofish.apinspector.environment.EnvironmentId
import work.foofish.apinspector.openapi.OperationKey
import work.foofish.apinspector.openapi.request.Field
import work.foofish.apinspector.openapi.request.HttpRequest
import work.foofish.apinspector.openapi.request.RequestInput
import work.foofish.apinspector.spec.SpecId
import java.time.Duration
import java.time.Instant

/** 请求记录的标识。 */
@JvmInline
value class HistoryEntryId(
    val value: Long,
)

/** 请求从哪里发出（PRD 7.1）。 */
enum class RequestSource {
    OPERATION,
    PRESET,
    REPLAY,
}

/**
 * 请求记录（PRD 7.1）。
 *
 * @property operation 发出请求的接口。
 * @property environmentId 所用环境；环境被删除后仍保留，但不再能找到。
 * @property input 发送时的当前参数，用于"恢复到当前参数"。
 */
data class HistoryEntry(
    val id: HistoryEntryId,
    val specId: SpecId,
    val operation: OperationKey,
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

/** 列表中显示的请求记录摘要。 */
data class HistorySummary(
    val id: HistoryEntryId,
    val operation: OperationKey,
    val sentAt: Instant,
    val duration: Duration,
    /** 收到响应时的状态码。 */
    val statusCode: Int?,
    val outcome: OutcomeKind,
    /** 响应 Body 的完整大小。 */
    val size: Long?,
)

enum class OutcomeKind {
    RECEIVED,
    FAILED,
    CANCELLED,
}

/** 请求的结果。 */
sealed interface Outcome {
    data class Received(
        val response: ReceivedResponse,
    ) : Outcome

    data class Failed(
        val error: NetworkError,
    ) : Outcome

    data object Cancelled : Outcome
}

/**
 * 收到的响应。
 *
 * @property finalUrl 跟随重定向后的地址。
 * @property body 无 Body 时为 `null`。
 */
data class ReceivedResponse(
    val statusCode: Int,
    val statusMessage: String,
    val protocol: String,
    val finalUrl: String,
    val headers: List<Field>,
    val contentType: String?,
    val body: StoredBody?,
    val timings: Timings,
)

/**
 * 保存在缓存目录中的响应 Body（PRD 7.1）。
 *
 * @property totalBytes 完整大小；超过保存上限时只保存前 [storedBytes] 字节。
 */
data class StoredBody(
    val fileName: String,
    val totalBytes: Long,
    val storedBytes: Long,
) {
    val truncated: Boolean get() = storedBytes < totalBytes
}

/** 耗时分解（PRD 7.2）；未经历的阶段（如复用连接时的 DNS）为 `null`。 */
data class Timings(
    val dns: Duration?,
    val connect: Duration?,
    val tls: Duration?,
    val firstByte: Duration?,
)

data class NetworkError(
    val kind: NetworkErrorKind,
    /** 底层异常的说明。 */
    val detail: String?,
)

enum class NetworkErrorKind {
    TIMEOUT,
    UNKNOWN_HOST,
    CONNECTION_FAILED,
    TLS,

    /** 请求 Body 引用的文件无法读取。 */
    FILE_UNAVAILABLE,
    OTHER,
}
