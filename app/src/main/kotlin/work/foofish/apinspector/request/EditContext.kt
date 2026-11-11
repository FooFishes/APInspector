package work.foofish.apinspector.request

import work.foofish.apinspector.custom.CustomRequest
import work.foofish.apinspector.openapi.OpenApiDocument
import work.foofish.apinspector.openapi.Operation
import work.foofish.apinspector.openapi.OperationKey
import work.foofish.apinspector.openapi.request.BodyInput
import work.foofish.apinspector.openapi.request.FileRef
import work.foofish.apinspector.openapi.request.HttpBody
import work.foofish.apinspector.openapi.request.HttpPart
import work.foofish.apinspector.openapi.request.MultipartPart
import work.foofish.apinspector.openapi.request.PartValue
import work.foofish.apinspector.spec.DocumentLoad
import work.foofish.apinspector.spec.ImportFailure
import work.foofish.apinspector.spec.SpecId

/** 自定义请求的加载状态。 */
internal sealed interface CustomState {
    /** 编辑的是接口，不是自定义请求。 */
    data object None : CustomState

    data object Unknown : CustomState

    /** 自定义请求已被删除。 */
    data object Missing : CustomState

    data class Known(
        val request: CustomRequest,
    ) : CustomState
}

/** 编辑所需的文档与接口。 */
internal sealed interface EditContext {
    data object Missing : EditContext

    data class Failed(
        val failure: ImportFailure,
    ) : EditContext

    /**
     * @property operation 接口；编辑自定义请求时是由它生成的接口（见 [asOperation]）。
     * @property custom 编辑的是自定义请求时给出。
     */
    data class Ready(
        val document: OpenApiDocument,
        val operation: Operation,
        val custom: CustomRequest? = null,
    ) : EditContext {
        /** 所属的 API 文档；[operationSpec] 为编辑接口时它所在的文档。 */
        fun specId(operationSpec: SpecId?): SpecId? = if (custom == null) operationSpec else custom.specId

        /** 发送槽位与请求记录的归属。 */
        fun ref(operationSpec: SpecId?): RequestRef =
            custom?.ref ?: RequestRef.Operation(checkNotNull(operationSpec) { "接口必须属于某份 API 文档" }, operation.key)
    }

    companion object {
        fun of(
            load: DocumentLoad,
            key: OperationKey,
        ): EditContext =
            when (load) {
                DocumentLoad.Missing -> {
                    Missing
                }

                is DocumentLoad.Failed -> {
                    Failed(load.failure)
                }

                is DocumentLoad.Loaded -> {
                    load.content.document
                        .operations
                        .firstOrNull { it.key == key }
                        ?.let { Ready(load.content.document, it) } ?: Missing
                }
            }

        /**
         * 自定义请求的编辑上下文；仍在加载时为 `null`。
         *
         * @param load 所属 API 文档的内容；临时请求不属于任何文档，为 `null`。
         */
        fun of(
            state: CustomState,
            load: DocumentLoad?,
        ): EditContext? {
            val request =
                when (state) {
                    CustomState.None, CustomState.Unknown -> return null
                    CustomState.Missing -> return Missing
                    is CustomState.Known -> state.request
                }
            if (request.specId == null) return Ready(EmptyDocument, request.asOperation(EmptyDocument), request)
            return when (load) {
                null -> null
                DocumentLoad.Missing -> Missing
                is DocumentLoad.Failed -> Failed(load.failure)
                is DocumentLoad.Loaded -> Ready(load.content.document, request.asOperation(load.content.document), request)
            }
        }
    }
}

internal fun BodyInput.withFile(
    target: FileTarget,
    file: FileRef,
): BodyInput =
    when {
        this is BodyInput.Binary && target == FileTarget.Body -> {
            copy(file = file)
        }

        this is BodyInput.Multipart && target is FileTarget.Part -> {
            copy(
                parts =
                    parts.mapIndexed { index, part ->
                        if (index ==
                            target.index
                        ) {
                            MultipartPart(part.name, PartValue.File(file))
                        } else {
                            part
                        }
                    },
            )
        }

        else -> {
            this
        }
    }

internal fun HttpBody?.files(): List<FileRef> =
    when (this) {
        is HttpBody.File -> listOf(file)
        is HttpBody.Multipart -> parts.filterIsInstance<HttpPart.File>().map { it.file }
        is HttpBody.Text, null -> emptyList()
    }
