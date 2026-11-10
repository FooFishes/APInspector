package work.foofish.apinspector.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import work.foofish.apinspector.environment.EnvironmentId
import work.foofish.apinspector.history.HistoryEntryId
import work.foofish.apinspector.openapi.HttpMethod
import work.foofish.apinspector.openapi.OperationKey
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

/** 一份 API 文档的请求记录列表（PRD 7.1）；[method] 与 [path] 给出时只列出该接口的记录。 */
@Serializable
data class HistoryKey(
    val specId: SpecId,
    val method: String? = null,
    val path: String? = null,
) : NavKey {
    constructor(specId: SpecId, operation: OperationKey?) : this(specId, operation?.method?.name, operation?.path)

    val operation: OperationKey? get() = if (method != null && path != null) operationKey(method, path) else null
}

/** 一条请求记录的详情。 */
@Serializable
data class HistoryEntryKey(
    val specId: SpecId,
    val entryId: Long,
) : NavKey {
    val entry: HistoryEntryId get() = HistoryEntryId(entryId)
}

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
