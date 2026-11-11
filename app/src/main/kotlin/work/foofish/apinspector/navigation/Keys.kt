package work.foofish.apinspector.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import work.foofish.apinspector.custom.CustomRequestId
import work.foofish.apinspector.environment.EnvironmentId
import work.foofish.apinspector.history.HistoryEntryId
import work.foofish.apinspector.openapi.HttpMethod
import work.foofish.apinspector.openapi.OperationKey
import work.foofish.apinspector.request.RequestRef
import work.foofish.apinspector.spec.SpecId

/** 首页：API 文档列表。 */
@Serializable
data object HomeKey : NavKey

/** 设置（PRD 6），全屏打开。 */
@Serializable
data object SettingsKey : NavKey

/** 一份 API 文档的接口列表。 */
@Serializable
data class SpecKey(
    val specId: SpecId,
) : NavKey

/** 接口详情：文档、请求与响应三个 Tab（PRD 3）。方法以名称保存，以便序列化。 */
@Serializable
data class OperationPageKey(
    val specId: SpecId,
    val method: String,
    val path: String,
) : NavKey {
    constructor(specId: SpecId, operation: OperationKey) : this(specId, operation.method.name, operation.path)

    val operation: OperationKey get() = operationKey(method, path)
}

/**
 * 请求记录列表（PRD 7.1）：[specId] 的全部记录，`null` 为临时请求的记录。
 * [method] 与 [path] 给出时只列出该接口的记录，[customId] 给出时只列出该自定义请求的记录。
 */
@Serializable
data class HistoryKey(
    val specId: SpecId?,
    val method: String? = null,
    val path: String? = null,
    val customId: CustomRequestId? = null,
) : NavKey {
    constructor(specId: SpecId, operation: OperationKey?) : this(specId, operation?.method?.name, operation?.path)

    /** 只列出它的记录；为 `null` 时列出全部。 */
    val ref: RequestRef?
        get() =
            when {
                customId != null -> RequestRef.Custom(customId, specId)
                specId != null && method != null && path != null -> RequestRef.Operation(specId, operationKey(method, path))
                else -> null
            }
}

/** 一条请求记录的详情。 */
@Serializable
data class HistoryEntryKey(
    val entryId: Long,
) : NavKey {
    val entry: HistoryEntryId get() = HistoryEntryId(entryId)
}

/** 临时请求列表（PRD 4.2）：首页固定置顶的入口。 */
@Serializable
data object ScratchKey : NavKey

/** 一个自定义请求：请求与响应两个 Tab（PRD 4.2）。 */
@Serializable
data class CustomRequestKey(
    val id: CustomRequestId,
) : NavKey

/** 一份 API 文档的环境列表（PRD 5"管理环境"）。 */
@Serializable
data class EnvironmentsKey(
    val specId: SpecId,
) : NavKey

/** 编辑一个环境。 */
@Serializable
data class EnvironmentKey(
    val specId: SpecId,
    val environmentId: EnvironmentId,
) : NavKey

private fun operationKey(
    method: String,
    path: String,
): OperationKey = OperationKey(checkNotNull(HttpMethod.of(method)) { "非法的 HTTP 方法：$method" }, path)
