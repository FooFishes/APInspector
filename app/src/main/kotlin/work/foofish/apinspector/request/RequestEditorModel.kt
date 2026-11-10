package work.foofish.apinspector.request

import android.net.Uri
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.serialization.json.JsonPrimitive
import work.foofish.apinspector.environment.Environment
import work.foofish.apinspector.environment.EnvironmentId
import work.foofish.apinspector.openapi.OpenApiDocument
import work.foofish.apinspector.openapi.Operation
import work.foofish.apinspector.openapi.Parameter
import work.foofish.apinspector.openapi.ParameterLocation
import work.foofish.apinspector.openapi.Resolvable
import work.foofish.apinspector.openapi.Schema
import work.foofish.apinspector.openapi.request.AssemblyProblem
import work.foofish.apinspector.openapi.request.BodyInput
import work.foofish.apinspector.openapi.request.ExtraParameter
import work.foofish.apinspector.openapi.request.Field
import work.foofish.apinspector.openapi.request.FileRef
import work.foofish.apinspector.openapi.request.FormText
import work.foofish.apinspector.openapi.request.JsonNode
import work.foofish.apinspector.openapi.request.JsonTemplate
import work.foofish.apinspector.openapi.request.JsonTemplateParse
import work.foofish.apinspector.openapi.request.MultipartPart
import work.foofish.apinspector.openapi.request.ParameterShape
import work.foofish.apinspector.openapi.request.ParameterValue
import work.foofish.apinspector.openapi.request.RequestInput
import work.foofish.apinspector.openapi.request.bodyContents
import work.foofish.apinspector.openapi.request.choices
import work.foofish.apinspector.openapi.request.editableParameters
import work.foofish.apinspector.openapi.request.isIgnoredHeader
import work.foofish.apinspector.openapi.request.shape
import work.foofish.apinspector.operationdoc.TypeLabel
import work.foofish.apinspector.operationdoc.typeLabel
import work.foofish.apinspector.request.body.FieldSchema
import work.foofish.apinspector.request.body.JsonPath
import work.foofish.apinspector.request.body.JsonRow
import work.foofish.apinspector.request.body.JsonTreeBuilder

/** 环境选择中的一项。 */
data class EnvironmentOption(
    val id: EnvironmentId,
    val name: String,
    /** baseUrl 不完整，不能发送（PRD 5）。 */
    val incomplete: Boolean,
    /** 跳过证书校验，以警示色标识（PRD 6）。 */
    val insecure: Boolean,
)

fun Environment.toOption(): EnvironmentOption = EnvironmentOption(id, name, isBaseUrlIncomplete, network.skipTlsVerification)

data class PresetOption(
    val id: PresetId,
    val name: String,
)

/**
 * 预设选择（PRD 4.1）。
 *
 * @property modified 当前参数相对 [current] 有改动（逐项比较得出）。
 */
data class PresetState(
    val presets: ImmutableList<PresetOption>,
    val current: PresetOption?,
    val modified: Boolean,
)

/** 一个声明的参数的编辑行。 */
data class ParameterField(
    val location: ParameterLocation,
    val name: String,
    val type: TypeLabel?,
    val required: Boolean,
    val deprecated: Boolean,
    val description: String?,
    /** 按规范默认忽略的 Header（PRD 4.1）。 */
    val ignored: Boolean,
    /** 总是发送，不显示勾选框。 */
    val alwaysSent: Boolean,
    val shape: ParameterShape,
    /** enum 或布尔的可选值。 */
    val choices: ImmutableList<String>,
    val value: ParameterValue,
    val enabled: Boolean,
)

data class ParameterGroup(
    val location: ParameterLocation,
    val fields: ImmutableList<ParameterField>,
)

/** 原文与可视化两种编辑方式（PRD 4.3）。 */
enum class BodyMode {
    VISUAL,
    RAW,
}

/**
 * Body 编辑区（PRD 4.3）。
 *
 * @property selected 选中的 content type；`null` 为"无 Body"。
 */
data class BodyEditor(
    val contentTypes: ImmutableList<String>,
    val optional: Boolean,
    val selected: String?,
    val content: BodyContent?,
)

sealed interface BodyContent {
    /**
     * @property parse 原文的解析结果；原文有语法错误时不能切换到可视化。
     * @property rows 可视化编辑的行；只在可视化模式下、且原文可以解析时给出。
     */
    data class Json(
        val text: String,
        val mode: BodyMode,
        val parse: JsonTemplateParse,
        val rows: ImmutableList<JsonRow>?,
    ) : BodyContent

    /**
     * @property missing schema 中声明但尚未填写的字段。
     */
    data class Form(
        val fields: ImmutableList<Field>,
        val missing: ImmutableList<String>,
        val schemas: ImmutableMap<String, FieldSchema>,
        val mode: BodyMode,
        val text: String,
    ) : BodyContent

    data class Multipart(
        val parts: ImmutableList<MultipartPart>,
        val missing: ImmutableList<String>,
        val schemas: ImmutableMap<String, FieldSchema>,
    ) : BodyContent

    data class Binary(
        val file: FileRef?,
    ) : BodyContent

    data class Text(
        val text: String,
    ) : BodyContent
}

/** 阻止发送的原因（PRD 4、5）。 */
sealed interface SendProblem {
    data class Assembly(
        val problem: AssemblyProblem,
    ) : SendProblem

    /** 文件已被删除或授权失效（PRD 4.3）。 */
    data class FileUnavailable(
        val name: String,
    ) : SendProblem

    data object NoEnvironment : SendProblem
}

/** 构建请求编辑区的显示模型。 */
class RequestEditorBuilder(
    private val document: OpenApiDocument,
    private val operation: Operation,
) {
    fun groups(input: RequestInput): ImmutableList<ParameterGroup> =
        operation.editableParameters
            .groupBy { it.location }
            .toSortedMap(compareBy(LOCATION_ORDER::indexOf))
            .map { (location, parameters) -> ParameterGroup(location, parameters.map { field(it, input) }.toImmutableList()) }
            .toImmutableList()

    private fun field(
        parameter: Parameter,
        input: RequestInput,
    ): ParameterField {
        val value = checkNotNull(input.parameter(parameter.location, parameter.name)) { "当前参数未与接口对齐：${parameter.name}" }
        val schema = parameter.schema ?: parameter.content.values.firstNotNullOfOrNull { (it as? Resolvable.Resolved)?.value?.schema }
        return ParameterField(
            location = parameter.location,
            name = parameter.name,
            type = schema?.let(::typeLabel),
            required = parameter.required,
            deprecated = parameter.deprecated,
            description = parameter.description ?: schema?.let { (document.dereference(it) as? Schema.Definition)?.description },
            ignored = parameter.isIgnoredHeader,
            alwaysSent = parameter.required && !parameter.isIgnoredHeader,
            shape = parameter.shape(document),
            choices = parameter.choices(document).toImmutableList(),
            value = value.value,
            enabled = value.enabled,
        )
    }

    fun body(
        input: RequestInput,
        mode: BodyMode,
        collapsed: Set<JsonPath>,
    ): BodyEditor? {
        val requestBody = (operation.requestBody as? Resolvable.Resolved)?.value ?: return null
        val contents = operation.bodyContents
        if (contents.isEmpty()) return null
        val body = input.body
        return BodyEditor(
            contentTypes = contents.keys.toImmutableList(),
            optional = !requestBody.required,
            selected = body?.contentType,
            content = body?.let { content(it, contents[it.contentType]?.schema, mode, collapsed) },
        )
    }

    private fun content(
        body: BodyInput,
        schema: Schema?,
        mode: BodyMode,
        collapsed: Set<JsonPath>,
    ): BodyContent =
        when (body) {
            is BodyInput.Json -> {
                val parse = JsonTemplate.parse(body.text)
                val visual = mode == BodyMode.VISUAL && parse is JsonTemplateParse.Success
                BodyContent.Json(
                    text = body.text,
                    mode = if (visual) BodyMode.VISUAL else BodyMode.RAW,
                    parse = parse,
                    rows =
                        (parse as? JsonTemplateParse.Success)?.takeIf { visual }?.let {
                            JsonTreeBuilder(
                                document,
                                collapsed,
                            ).build(it.node, schema)
                        },
                )
            }

            is BodyInput.Form -> {
                val properties = properties(schema)
                BodyContent.Form(
                    fields = body.fields.toImmutableList(),
                    missing = missing(properties, body.fields.map { it.name }),
                    schemas = properties.schemas,
                    mode = mode,
                    text = FormText.format(body.fields),
                )
            }

            is BodyInput.Multipart -> {
                val properties = properties(schema)
                BodyContent.Multipart(
                    parts = body.parts.toImmutableList(),
                    missing = missing(properties, body.parts.map { it.name }),
                    schemas = properties.schemas,
                )
            }

            is BodyInput.Binary -> {
                BodyContent.Binary(body.file)
            }

            is BodyInput.Text -> {
                BodyContent.Text(body.text)
            }
        }

    /** 表单 schema 的属性（按声明顺序，不含只读属性）。 */
    private fun properties(schema: Schema?): Properties {
        val definition =
            schema?.let(document::dereference) as? Schema.Definition
                ?: return Properties(emptyList(), emptyMap<String, FieldSchema>().toImmutableMap())
        val writable = definition.properties.filterValues { (document.dereference(it) as? Schema.Definition)?.readOnly != true }
        val schemas =
            writable.mapValues { (name, property) -> FieldSchema.of(property, name in definition.required, document) }
        return Properties(writable.keys.toList(), schemas.toImmutableMap())
    }

    private fun missing(
        properties: Properties,
        present: List<String>,
    ): ImmutableList<String> = properties.names.filterNot { it in present }.toImmutableList()

    private data class Properties(
        val names: List<String>,
        val schemas: ImmutableMap<String, FieldSchema>,
    )

    private companion object {
        val LOCATION_ORDER =
            listOf(
                ParameterLocation.PATH,
                ParameterLocation.QUERY,
                ParameterLocation.QUERY_STRING,
                ParameterLocation.HEADER,
                ParameterLocation.COOKIE,
            )
    }
}

/** "请求" Tab 顶部的环境与预设操作（PRD 4.1、5）。 */
interface RequestHeaderActions {
    fun selectEnvironment(id: EnvironmentId)

    /** 加载预设，覆盖当前参数。 */
    fun loadPreset(id: PresetId)

    /** 用当前参数覆盖正在使用的预设。 */
    fun saveToPreset()

    fun saveAsPreset(name: String)

    fun renamePreset(
        id: PresetId,
        name: String,
    )

    fun deletePreset(id: PresetId)

    /** 当前参数重置为文档默认值。 */
    fun resetToDefaults()
}

/** "请求" Tab 的全部操作（PRD 4）。 */
interface RequestEditorActions :
    RequestHeaderActions,
    ParameterActions,
    BodyActions,
    JsonBodyActions {
    fun send()

    fun cancel()

    fun dismissProblems()
}

/** 编辑参数与额外参数（PRD 4.1）。 */
interface ParameterActions {
    fun setParameterValue(
        location: ParameterLocation,
        name: String,
        value: ParameterValue,
    )

    fun setParameterEnabled(
        location: ParameterLocation,
        name: String,
        enabled: Boolean,
    )

    fun addExtra(extra: ExtraParameter)

    fun updateExtra(
        index: Int,
        extra: ExtraParameter,
    )

    fun removeExtra(index: Int)
}

/** 编辑 Body（PRD 4.3）。 */
interface BodyActions {
    /** `null` 为"无 Body"。 */
    fun selectContentType(contentType: String?)

    fun setBody(body: BodyInput)

    fun setBodyMode(mode: BodyMode)

    fun formatJson()

    fun pickFile(
        target: FileTarget,
        uri: Uri,
    )
}

/** 可视化编辑 JSON Body（PRD 4.3）。 */
interface JsonBodyActions {
    fun toggleJsonNode(path: JsonPath)

    fun setJsonNode(
        path: JsonPath,
        node: JsonNode,
    )

    fun removeJsonNode(path: JsonPath)

    fun addJsonMember(
        parent: JsonPath,
        name: String,
    )

    fun addJsonItem(parent: JsonPath)

    fun selectJsonVariant(
        path: JsonPath,
        index: Int,
    )
}
