package work.foofish.apinspector.openapi.request

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 提取规则使用的 JSONPath 子集（PRD 5）。
 *
 * 支持 `$`、`.name`、`['name']` / `["name"]`、`[n]`、`[-n]`（从末尾数）、`[*]` / `.*` 与 `..name`（递归下降）；
 * 不支持过滤表达式、切片与并集。开头的 `$` 可以省略（`data.token` 等同 `$.data.token`）。
 */
public class JsonPath private constructor(
    private val segments: List<Segment>,
) {
    /**
     * 在 JSON 文本 [json] 中取第一个匹配：字符串取其内容，数字 / 布尔 / null 取字面量，对象 / 数组取紧凑 JSON；
     * 不是合法 JSON 或没有匹配时为 `null`。
     */
    public fun extract(json: String): String? {
        val root =
            try {
                Json.parseToJsonElement(json)
            } catch (_: SerializationException) {
                null
            }
        return root?.let { segments.fold(sequenceOf(it)) { matches, segment -> matches.flatMap(segment::select) }.firstOrNull() }?.render()
    }

    /** 规范化后的写法。 */
    override fun toString(): String = ROOT + segments.joinToString("")

    override fun equals(other: Any?): Boolean = other is JsonPath && other.segments == segments

    override fun hashCode(): Int = segments.hashCode()

    public companion object {
        /** 解析 [text]；写法不合法时返回 `null`。 */
        public fun parse(text: String): JsonPath? {
            val trimmed = text.trim()
            val path =
                when {
                    trimmed.isEmpty() -> null
                    trimmed.startsWith(ROOT) -> trimmed
                    trimmed.startsWith(MEMBER) || trimmed.startsWith(BRACKET_OPEN) -> ROOT + trimmed
                    else -> ROOT + MEMBER + trimmed
                }
            return path?.let { JsonPathReader(it, start = ROOT.length).read() }?.let(::JsonPath)
        }
    }
}

/** 路径中的一步。 */
internal sealed interface Segment {
    fun select(element: JsonElement): Sequence<JsonElement>

    data class Key(
        val name: String,
    ) : Segment {
        override fun select(element: JsonElement): Sequence<JsonElement> = sequenceOf((element as? JsonObject)?.get(name)).filterNotNull()

        override fun toString(): String = keyText(name)
    }

    /** 负数从末尾数：`-1` 是最后一个元素。 */
    data class Index(
        val index: Int,
    ) : Segment {
        override fun select(element: JsonElement): Sequence<JsonElement> {
            val array = element as? JsonArray
            return sequenceOf(array?.getOrNull(if (index < 0) array.size + index else index)).filterNotNull()
        }

        override fun toString(): String = "$BRACKET_OPEN$index$BRACKET_CLOSE"
    }

    data object Wildcard : Segment {
        override fun select(element: JsonElement): Sequence<JsonElement> = element.children()

        override fun toString(): String = "$BRACKET_OPEN$WILDCARD$BRACKET_CLOSE"
    }

    /** 递归下降：自身及所有后代中名为 [name] 的成员，按文档顺序。 */
    data class Descendant(
        val name: String,
    ) : Segment {
        override fun select(element: JsonElement): Sequence<JsonElement> =
            sequence {
                (element as? JsonObject)?.get(name)?.let { yield(it) }
                element.children().forEach { yieldAll(select(it)) }
            }

        override fun toString(): String = keyText(name).let { if (it.startsWith(MEMBER)) MEMBER + it else MEMBER + MEMBER + it }
    }
}

/** 逐段读取路径；[start] 为根 `$` 之后的位置。 */
private class JsonPathReader(
    private val text: String,
    start: Int,
) {
    private var index = start

    fun read(): List<Segment>? {
        val segments = mutableListOf<Segment>()
        var valid = true
        while (valid && index < text.length) {
            val segment = segment()
            if (segment == null) valid = false else segments += segment
        }
        return segments.takeIf { valid }
    }

    private fun segment(): Segment? =
        when {
            text.startsWith(MEMBER + MEMBER, index) -> {
                index += 2
                descendant()
            }

            text.startsWith(MEMBER, index) -> {
                index++
                member()
            }

            text.startsWith(BRACKET_OPEN, index) -> {
                index++
                bracket()
            }

            else -> {
                null
            }
        }

    private fun member(): Segment? =
        if (text.getOrNull(index) == WILDCARD) {
            index++
            Segment.Wildcard
        } else {
            name()?.let(Segment::Key)
        }

    private fun descendant(): Segment? =
        if (text.startsWith(BRACKET_OPEN, index)) {
            index++
            (bracket() as? Segment.Key)?.let { Segment.Descendant(it.name) }
        } else {
            name()?.let(Segment::Descendant)
        }

    /** 点号写法的名称：到下一个 `.` 或 `[` 为止。 */
    private fun name(): String? {
        val start = index
        while (index < text.length && text[index] !in NAME_TERMINATORS) index++
        return text.substring(start, index).takeIf { it.isNotEmpty() }
    }

    /** `[` 之后的内容，直到并包含 `]`。 */
    private fun bracket(): Segment? {
        val first = text.getOrNull(index)
        val segment =
            when (first) {
                WILDCARD -> {
                    index++
                    Segment.Wildcard
                }

                SINGLE_QUOTE, DOUBLE_QUOTE -> {
                    quoted(text[index++])?.let(Segment::Key)
                }

                else -> {
                    integer()?.let(Segment::Index)
                }
            }
        return segment?.takeIf { text.getOrNull(index++) == BRACKET_CLOSE }
    }

    /** 引号中的名称；反斜杠转义其后的一个字符。 */
    private fun quoted(quote: Char): String? {
        val name = StringBuilder()
        var closed = false
        while (!closed && index < text.length) {
            val char = text[index++]
            when {
                char == quote -> closed = true
                char == ESCAPE && index < text.length -> name.append(text[index++])
                else -> name.append(char)
            }
        }
        return name.toString().takeIf { closed }
    }

    private fun integer(): Int? {
        val start = index
        if (text.getOrNull(index) == MINUS) index++
        while (index < text.length && text[index].isDigit()) index++
        return text.substring(start, index).toIntOrNull()
    }
}

private fun JsonElement.children(): Sequence<JsonElement> =
    when (this) {
        is JsonObject -> values.asSequence()
        is JsonArray -> asSequence()
        is JsonPrimitive -> emptySequence()
    }

private fun JsonElement.render(): String = if (this is JsonPrimitive) content else toString()

/** 成员的写法：简单的名称用 `.name`，其余用 `['name']`。 */
private fun keyText(name: String): String =
    if (SIMPLE_NAME.matches(name)) {
        MEMBER + name
    } else {
        val escaped = name.replace("$ESCAPE", "$ESCAPE$ESCAPE").replace("$SINGLE_QUOTE", "$ESCAPE$SINGLE_QUOTE")
        "$BRACKET_OPEN$SINGLE_QUOTE$escaped$SINGLE_QUOTE$BRACKET_CLOSE"
    }

private const val ROOT = "$"
private const val MEMBER = "."
private const val BRACKET_OPEN = "["
private const val BRACKET_CLOSE = ']'
private const val WILDCARD = '*'
private const val SINGLE_QUOTE = '\''
private const val DOUBLE_QUOTE = '"'
private const val ESCAPE = '\\'
private const val MINUS = '-'
private const val NAME_TERMINATORS = ".["
private val SIMPLE_NAME = Regex("[A-Za-z_][A-Za-z0-9_]*")
