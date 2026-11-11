package work.foofish.apinspector.request

import work.foofish.apinspector.custom.CustomRequestId
import work.foofish.apinspector.openapi.OperationKey
import work.foofish.apinspector.spec.SpecId

/**
 * 一个可以发送的请求：接口，或自定义请求（PRD 4.2）。
 *
 * 发送槽位（同一请求同时只有一个在途，PRD 6）与请求记录的归属都以它标识。
 */
sealed interface RequestRef {
    /** 所属的 API 文档；临时请求为 `null`。 */
    val specId: SpecId?

    data class Operation(
        override val specId: SpecId,
        val key: OperationKey,
    ) : RequestRef

    data class Custom(
        val id: CustomRequestId,
        override val specId: SpecId?,
    ) : RequestRef
}

/** 是否指向同一个请求：自定义请求只比较标识，它归属的 API 文档可能已经改变（移动）。 */
fun RequestRef.isSameRequest(other: RequestRef): Boolean =
    when (this) {
        is RequestRef.Operation -> this == other
        is RequestRef.Custom -> other is RequestRef.Custom && other.id == id
    }
