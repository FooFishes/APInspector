package work.foofish.apinspector.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import work.foofish.apinspector.openapi.HttpMethod
import work.foofish.apinspector.openapi.OperationKey
import work.foofish.apinspector.spec.SpecId

/** 首页：API 文档列表。 */
@Serializable
data object HomeKey : NavKey

/** 一份 API 文档的接口列表。 */
@Serializable
data class SpecKey(
    val specId: SpecId,
) : NavKey

/** 一个接口的文档。方法以名称保存，以便序列化。 */
@Serializable
data class OperationDocKey(
    val specId: SpecId,
    val method: String,
    val path: String,
) : NavKey {
    constructor(specId: SpecId, operation: OperationKey) : this(specId, operation.method.name, operation.path)

    val operation: OperationKey
        get() = OperationKey(checkNotNull(HttpMethod.of(method)) { "非法的 HTTP 方法：$method" }, path)
}
