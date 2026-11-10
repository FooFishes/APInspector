package work.foofish.apinspector.openapi.request

import work.foofish.apinspector.openapi.OpenApiDocument
import work.foofish.apinspector.openapi.Parameter
import work.foofish.apinspector.openapi.ParameterLocation
import work.foofish.apinspector.openapi.ParameterStyle
import work.foofish.apinspector.openapi.Schema
import work.foofish.apinspector.openapi.SchemaType

/** 参数值的输入形态（PRD 4.1）。 */
public enum class ParameterShape {
    /** 单个文本框：基本类型、用 `content` 描述的参数，以及无法结构化输入的对象。 */
    TEXT,

    /** 可增删的值列表：数组。 */
    ITEMS,

    /** 键值列表：`form` 或 `deepObject` 风格的对象。 */
    FIELDS,
}

/** 按参数的 schema 与序列化方式决定输入形态。 */
public fun Parameter.shape(document: OpenApiDocument): ParameterShape {
    val serialization = serialization ?: return ParameterShape.TEXT
    val definition = schema?.let(document::dereference) as? Schema.Definition ?: return ParameterShape.TEXT
    return when {
        SchemaType.ARRAY in definition.types -> {
            ParameterShape.ITEMS
        }

        definition.isObject() && serialization.style in STRUCTURED_OBJECT_STYLES -> {
            ParameterShape.FIELDS
        }

        else -> {
            ParameterShape.TEXT
        }
    }
}

/** 数组元素或参数本身可选的枚举值（文本形式）。 */
public fun Parameter.choices(document: OpenApiDocument): List<String> {
    val definition = schema?.let(document::dereference) as? Schema.Definition ?: return emptyList()
    val target =
        if (SchemaType.ARRAY in definition.types) {
            definition.items?.let(document::dereference) as? Schema.Definition ?: return emptyList()
        } else {
            definition
        }
    return when {
        target.enumValues.isNotEmpty() -> target.enumValues.map { it.text() }
        SchemaType.BOOLEAN in target.types -> listOf(true.toString(), false.toString())
        else -> emptyList()
    }
}

/**
 * 名为 `Accept`、`Content-Type`、`Authorization` 的 Header 参数：规范规定忽略，
 * 因为它们分别由响应声明、Body 类型与鉴权决定（PRD 4.1）。这里默认不勾选，手动勾选后才发送。
 */
public val Parameter.isIgnoredHeader: Boolean
    get() = location == ParameterLocation.HEADER && name.lowercase() in IGNORED_HEADERS

/** 是否发送：必填参数总是发送（按规范忽略的 Header 除外），其余看是否勾选。 */
public fun Parameter.isSent(input: ParameterInput): Boolean = (required && !isIgnoredHeader) || input.enabled

/** Body 编辑器的种类（PRD 4.3）。 */
public enum class BodyKind {
    JSON,
    FORM,
    MULTIPART,

    /** 以一个文件作为 Body。 */
    BINARY,
    TEXT,
    ;

    public companion object {
        /** 按 content type 选择编辑器；不区分大小写，忽略参数。 */
        public fun of(contentType: String): BodyKind {
            val mediaType = MediaType.essence(contentType)
            val type = mediaType.substringBefore('/')
            val subtype = mediaType.substringAfter('/', missingDelimiterValue = "")
            return when {
                subtype == JSON_SUBTYPE || subtype.endsWith(JSON_SUFFIX) -> JSON
                mediaType == MediaType.FORM_URLENCODED -> FORM
                type == MULTIPART_TYPE -> MULTIPART
                mediaType in BINARY_TYPES || type in BINARY_TOP_LEVEL_TYPES -> BINARY
                else -> TEXT
            }
        }

        private const val JSON_SUBTYPE = "json"
        private const val JSON_SUFFIX = "+json"
        private const val MULTIPART_TYPE = "multipart"
        private val BINARY_TYPES = setOf("application/octet-stream", "application/pdf", "application/zip")
        private val BINARY_TOP_LEVEL_TYPES = setOf("image", "audio", "video")
    }
}

internal object MediaType {
    const val FORM_URLENCODED = "application/x-www-form-urlencoded"
    const val TEXT_PLAIN = "text/plain"
    const val OCTET_STREAM = "application/octet-stream"
    const val MULTIPART_FORM_DATA = "multipart/form-data"
    const val ANY = "*/*"

    /** 去掉参数、转为小写后的 `type/subtype`。 */
    fun essence(contentType: String): String = contentType.substringBefore(';').trim().lowercase()

    /** 含通配符 `*`，不能直接作为请求的 Content-Type。 */
    fun isWildcard(contentType: String): Boolean = '*' in essence(contentType)
}

private val STRUCTURED_OBJECT_STYLES = setOf(ParameterStyle.FORM, ParameterStyle.DEEP_OBJECT)

private val IGNORED_HEADERS = setOf("accept", "content-type", "authorization")

internal fun Schema.Definition.isObject(): Boolean = SchemaType.OBJECT in types || (types.isEmpty() && properties.isNotEmpty())
