package work.foofish.apinspector.openapi.request

import work.foofish.apinspector.openapi.Parameter
import work.foofish.apinspector.openapi.ParameterLocation
import work.foofish.apinspector.openapi.ParameterStyle

/** 一个参数序列化后放在请求中的位置与内容。查询与路径部分已完成百分号编码。 */
internal sealed interface SerializedParameter {
    /** 替换路径模板中的 `{name}`。 */
    data class Path(
        val text: String,
    ) : SerializedParameter

    /** 查询串中的若干 `name=value` 片段；可能为空（如空数组）。 */
    data class Query(
        val pairs: List<String>,
    ) : SerializedParameter

    /** OpenAPI 3.2 `querystring`：整个查询串。 */
    data class QueryString(
        val text: String,
    ) : SerializedParameter

    data class Header(
        val value: String,
    ) : SerializedParameter

    data class Cookies(
        val pairs: List<Field>,
    ) : SerializedParameter
}

/**
 * 按 OpenAPI 的 `style` / `explode` 序列化参数（PRD 4.1）。[value] 中的变量必须已经替换。
 *
 * 值的形态决定按基本类型、数组还是对象处理；用 `content` 描述的参数原样编码。
 */
internal fun Parameter.serialize(value: ParameterValue): SerializedParameter {
    val serialization = serialization ?: return serializeContent(value.asText())
    val style = serialization.style
    val explode = serialization.explode
    return when (location) {
        ParameterLocation.PATH -> SerializedParameter.Path(pathValue(style, explode, value))
        ParameterLocation.QUERY, ParameterLocation.QUERY_STRING -> SerializedParameter.Query(queryPairs(style, explode, value))
        ParameterLocation.HEADER -> SerializedParameter.Header(delimited(value, explode, separator = ",", encode = { it }))
        ParameterLocation.COOKIE -> SerializedParameter.Cookies(cookiePairs(style, explode, value))
    }
}

private fun Parameter.serializeContent(text: String): SerializedParameter =
    when (location) {
        ParameterLocation.PATH -> SerializedParameter.Path(PercentEncoding.encode(text))
        ParameterLocation.QUERY -> SerializedParameter.Query(listOf(pair(name, text)))
        ParameterLocation.QUERY_STRING -> SerializedParameter.QueryString(text)
        ParameterLocation.HEADER -> SerializedParameter.Header(text)
        ParameterLocation.COOKIE -> SerializedParameter.Cookies(listOf(Field(name, text)))
    }

private fun Parameter.pathValue(
    style: ParameterStyle,
    explode: Boolean,
    value: ParameterValue,
): String {
    val encode = { text: String -> PercentEncoding.encode(text) }
    return when (style) {
        ParameterStyle.LABEL -> {
            "." + delimited(value, explode, separator = if (explode) "." else ",", encode)
        }

        ParameterStyle.MATRIX -> {
            when (value) {
                is ParameterValue.Text -> {
                    matrixPair(name, encode(value.text))
                }

                is ParameterValue.Items -> {
                    if (explode) {
                        value.items.joinToString("") {
                            matrixPair(name, encode(it))
                        }
                    } else {
                        matrixPair(name, value.items.joinToString(",", transform = encode))
                    }
                }

                is ParameterValue.Fields -> {
                    if (explode) {
                        value.fields.joinToString("") {
                            matrixPair(encode(it.name), encode(it.value))
                        }
                    } else {
                        matrixPair(name, flatten(value.fields, encode))
                    }
                }
            }
        }

        else -> {
            delimited(value, explode, separator = ",", encode)
        }
    }
}

private fun Parameter.queryPairs(
    style: ParameterStyle,
    explode: Boolean,
    value: ParameterValue,
): List<String> =
    when (value) {
        is ParameterValue.Text -> {
            listOf(pair(name, value.text))
        }

        is ParameterValue.Items -> {
            val separator =
                when (style) {
                    ParameterStyle.SPACE_DELIMITED -> SPACE_ENCODED
                    ParameterStyle.PIPE_DELIMITED -> "|"
                    else -> ","
                }
            when {
                explode -> value.items.map { pair(name, it) }
                value.items.isEmpty() -> emptyList()
                else -> listOf(PercentEncoding.encode(name) + "=" + value.items.joinToString(separator) { PercentEncoding.encode(it) })
            }
        }

        is ParameterValue.Fields -> {
            when {
                style == ParameterStyle.DEEP_OBJECT -> value.fields.map { pair("$name[${it.name}]", it.value, keep = "[]") }
                explode -> value.fields.map { pair(it.name, it.value) }
                value.fields.isEmpty() -> emptyList()
                else -> listOf(PercentEncoding.encode(name) + "=" + flatten(value.fields) { PercentEncoding.encode(it) })
            }
        }
    }

/** `form` 风格的 Cookie 值按百分号编码，OpenAPI 3.2 的 `cookie` 风格原样发送。 */
private fun Parameter.cookiePairs(
    style: ParameterStyle,
    explode: Boolean,
    value: ParameterValue,
): List<Field> {
    val encode: (String) -> String = if (style == ParameterStyle.COOKIE) ({ it }) else ({ PercentEncoding.encode(it) })
    return when (value) {
        is ParameterValue.Text -> {
            listOf(Field(name, encode(value.text)))
        }

        is ParameterValue.Items -> {
            if (explode) {
                value.items.map {
                    Field(name, encode(it))
                }
            } else {
                listOf(Field(name, value.items.joinToString(",", transform = encode)))
            }
        }

        is ParameterValue.Fields -> {
            if (explode) {
                value.fields.map {
                    Field(it.name, encode(it.value))
                }
            } else {
                listOf(Field(name, flatten(value.fields, encode)))
            }
        }
    }
}

/** `simple` 风格：数组以 [separator] 连接；对象展开为 `k=v`，否则为 `k,v` 交替。 */
private fun delimited(
    value: ParameterValue,
    explode: Boolean,
    separator: String,
    encode: (String) -> String,
): String =
    when (value) {
        is ParameterValue.Text -> {
            value.text.let(encode)
        }

        is ParameterValue.Items -> {
            value.items.joinToString(separator, transform = encode)
        }

        is ParameterValue.Fields -> {
            if (explode) {
                value.fields.joinToString(separator) {
                    encode(it.name) + "=" + encode(it.value)
                }
            } else {
                flatten(value.fields, encode)
            }
        }
    }

private fun flatten(
    fields: List<Field>,
    encode: (String) -> String,
): String = fields.joinToString(",") { encode(it.name) + "," + encode(it.value) }

private fun matrixPair(
    name: String,
    value: String,
): String = if (value.isEmpty()) ";$name" else ";$name=$value"

private fun pair(
    name: String,
    value: String,
    keep: String = "",
): String = PercentEncoding.encode(name, keep) + "=" + PercentEncoding.encode(value)

/** 文本形态以外的值按基本类型发送时的文本：数组与对象以逗号连接。 */
internal fun ParameterValue.asText(): String =
    when (this) {
        is ParameterValue.Text -> text
        is ParameterValue.Items -> items.joinToString(",")
        is ParameterValue.Fields -> fields.joinToString(",") { "${it.name},${it.value}" }
    }

private const val SPACE_ENCODED = "%20"
