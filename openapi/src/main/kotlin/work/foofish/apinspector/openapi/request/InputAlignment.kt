package work.foofish.apinspector.openapi.request

import work.foofish.apinspector.openapi.OpenApiDocument
import work.foofish.apinspector.openapi.Operation
import work.foofish.apinspector.openapi.Parameter
import work.foofish.apinspector.openapi.ParameterLocation

/**
 * 刷新 API 文档后，把当前参数或预设与新的接口定义对齐（PRD 4.1）：
 *
 * - 参数按"位置 + 名称"匹配：仍存在的保留值与勾选状态，新增的取文档默认值；
 * - 不再声明的 Header / Query 参数转为额外参数，其余（路径、Cookie）丢弃；
 * - Body 保持原样；原 content type 已不存在时改为第一个新的 content type 并取文档默认值。
 */
public fun RequestInput.alignedTo(
    operation: Operation,
    document: OpenApiDocument,
): RequestInput {
    val declared = operation.editableParameters
    val parameters =
        declared.map { parameter ->
            parameter(parameter.location, parameter.name)
                ?.let { old -> old.copy(value = old.value.reshaped(parameter, document)) }
                ?: parameter.defaultInput(document)
        }
    val declaredKeys = declared.mapTo(HashSet()) { it.location to it.name }
    val existingExtras = extras.mapTo(HashSet()) { it.location to it.name }
    val orphaned =
        this.parameters
            .filter { (it.location to it.name) !in declaredKeys }
            .mapNotNull { old -> old.location.extraLocation()?.let { ExtraParameter(it, old.name, old.value.asText(), old.enabled) } }
            .filter { (it.location to it.name) !in existingExtras }
    val contents = operation.bodyContents
    val alignedBody =
        when {
            body == null -> null
            contents.isEmpty() -> null
            body.contentType in contents -> body
            else -> contents.entries.first().let { (contentType, content) -> defaultBody(contentType, content, document) }
        }
    return RequestInput(parameters, extras + orphaned, alignedBody)
}

/** 参数的输入形态变了（如字符串改为数组）时尽量保留原值，无法转换的取文档默认值。 */
private fun ParameterValue.reshaped(
    parameter: Parameter,
    document: OpenApiDocument,
): ParameterValue =
    when (parameter.shape(document)) {
        ParameterShape.TEXT -> {
            this as? ParameterValue.Text ?: ParameterValue.Text(asText())
        }

        ParameterShape.ITEMS -> {
            when (this) {
                is ParameterValue.Items -> this
                is ParameterValue.Text -> ParameterValue.Items(listOf(text).filter { it.isNotEmpty() })
                is ParameterValue.Fields -> parameter.defaultValue(document)
            }
        }

        ParameterShape.FIELDS -> {
            this as? ParameterValue.Fields ?: parameter.defaultValue(document)
        }
    }

private fun ParameterLocation.extraLocation(): ExtraLocation? =
    when (this) {
        ParameterLocation.QUERY -> ExtraLocation.QUERY
        ParameterLocation.HEADER -> ExtraLocation.HEADER
        ParameterLocation.PATH, ParameterLocation.QUERY_STRING, ParameterLocation.COOKIE -> null
    }
