package work.foofish.apinspector.openapi.request

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import work.foofish.apinspector.openapi.Example
import work.foofish.apinspector.openapi.MediaTypeContent
import work.foofish.apinspector.openapi.OpenApiDocument
import work.foofish.apinspector.openapi.Operation
import work.foofish.apinspector.openapi.Parameter
import work.foofish.apinspector.openapi.Resolvable
import work.foofish.apinspector.openapi.Schema

/** 接口中可以编辑的参数：已解析的声明参数，按文档顺序。 */
public val Operation.editableParameters: List<Parameter>
    get() = parameters.mapNotNull { (it as? Resolvable.Resolved)?.value }

/** 接口可选的 Body content type 及其描述，按文档顺序；未解析的请求体没有可选项。 */
public val Operation.bodyContents: Map<String, MediaTypeContent?>
    get() =
        (requestBody as? Resolvable.Resolved)
            ?.value
            ?.content
            ?.mapValues { (_, content) -> (content as? Resolvable.Resolved)?.value }
            .orEmpty()

/**
 * 接口的文档默认值（PRD 4.1）：必填参数勾选，非必填参数预填但不勾选；Body 选第一个 content type。
 */
public fun Operation.defaultInput(document: OpenApiDocument): RequestInput =
    RequestInput(
        parameters = editableParameters.map { it.defaultInput(document) },
        extras = emptyList(),
        body = bodyContents.entries.firstOrNull()?.let { (contentType, content) -> defaultBody(contentType, content, document) },
    )

public fun Parameter.defaultInput(document: OpenApiDocument): ParameterInput =
    ParameterInput(location, name, defaultValue(document), enabled = required && !isIgnoredHeader)

/**
 * 参数的文档默认值：依次取参数的 `example`、`examples` 的第一个、schema 的 `default`、
 * schema `examples` 的第一个、唯一的 enum 值；都没有则为空。
 */
public fun Parameter.defaultValue(document: OpenApiDocument): ParameterValue {
    val definition = schema?.let(document::dereference) as? Schema.Definition
    val contentSample = content.values.firstNotNullOfOrNull { (it as? Resolvable.Resolved)?.value?.declaredSample() }
    val sample =
        example
            ?: examples.values.firstNotNullOfOrNull { it.sampleValue() }
            ?: contentSample
            ?: definition?.defaultValue
            ?: definition?.examples?.firstOrNull()
            ?: definition?.enumValues?.singleOrNull()
    return when (shape(document)) {
        ParameterShape.TEXT -> {
            ParameterValue.Text(sample?.text().orEmpty())
        }

        ParameterShape.ITEMS -> {
            ParameterValue.Items(
                when (sample) {
                    null, JsonNull -> emptyList()
                    is JsonArray -> sample.map { it.text() }
                    else -> listOf(sample.text())
                },
            )
        }

        ParameterShape.FIELDS -> {
            ParameterValue.Fields((sample as? JsonObject)?.map { (name, value) -> Field(name, value.text()) }.orEmpty())
        }
    }
}

/** 某个 content type 的默认 Body：取 `example`，否则按 schema 生成（PRD 4.1）。 */
public fun defaultBody(
    contentType: String,
    content: MediaTypeContent?,
    document: OpenApiDocument,
): BodyInput {
    val sampler = SchemaSampler(document)
    val sample = content?.declaredSample() ?: content?.schema?.let(sampler::sample)
    return when (BodyKind.of(contentType)) {
        BodyKind.JSON -> {
            BodyInput.Json(contentType, sample?.let { JsonTemplate.format(JsonTemplate.fromElement(it)) }.orEmpty())
        }

        BodyKind.FORM -> {
            BodyInput.Form(contentType, (sample as? JsonObject)?.map { (name, value) -> Field(name, value.text()) }.orEmpty())
        }

        BodyKind.MULTIPART -> {
            BodyInput.Multipart(contentType, multipartParts(content?.schema, sample as? JsonObject, document))
        }

        BodyKind.BINARY -> {
            BodyInput.Binary(contentType, file = null)
        }

        BodyKind.TEXT -> {
            val serialized = content?.examples?.values?.firstNotNullOfOrNull { (it as? Resolvable.Resolved)?.value?.serializedValue }
            BodyInput.Text(contentType, serialized ?: sample?.text().orEmpty())
        }
    }
}

/** 每个属性一个字段：`format: binary` 的属性为文件，其余为文本。 */
private fun multipartParts(
    schema: Schema?,
    sample: JsonObject?,
    document: OpenApiDocument,
): List<MultipartPart> {
    val properties = (schema?.let(document::dereference) as? Schema.Definition)?.properties.orEmpty()
    val names = properties.keys + sample?.keys.orEmpty()
    return names.map { name ->
        val property = properties[name]?.let(document::dereference) as? Schema.Definition
        val value = if (property?.isBinary() == true) PartValue.File(file = null) else PartValue.Text(sample?.get(name)?.text().orEmpty())
        MultipartPart(name, value)
    }
}

/** 文件类型的属性（`format: binary`）：multipart 中以文件发送。 */
public fun Schema.Definition.isBinary(): Boolean = format == BINARY_FORMAT

/** Media Type 上直接给出的示例：`example`，否则 `examples` 的第一个。 */
private fun MediaTypeContent.declaredSample(): JsonElement? = example ?: examples.values.firstNotNullOfOrNull { it.sampleValue() }

private fun Resolvable<Example>.sampleValue(): JsonElement? = (this as? Resolvable.Resolved)?.value?.let { it.dataValue ?: it.value }

/** 参数值、表单字段使用的文本形式：字符串不带引号，`null` 为空，对象与数组为紧凑的 JSON。 */
internal fun JsonElement.text(): String =
    when (this) {
        JsonNull -> ""
        is JsonPrimitive -> content
        else -> toString()
    }

private const val BINARY_FORMAT = "binary"
