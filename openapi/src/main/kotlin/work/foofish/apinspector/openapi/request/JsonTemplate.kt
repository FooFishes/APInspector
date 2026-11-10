package work.foofish.apinspector.openapi.request

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * 可以含有变量引用的 JSON 值：`{{name}}` 可以出现在任何值的位置（PRD 4.3）。
 *
 * 对象保持成员顺序，数字保留原文，以便格式化时不改变用户写下的内容。
 */
public sealed interface JsonNode {
    public data class ObjectNode(
        val members: List<JsonMember>,
    ) : JsonNode

    public data class ArrayNode(
        val items: List<JsonNode>,
    ) : JsonNode

    public data class StringNode(
        val value: String,
    ) : JsonNode

    /** [literal] 是符合 JSON 语法的数字原文。 */
    public data class NumberNode(
        val literal: String,
    ) : JsonNode

    public data class BooleanNode(
        val value: Boolean,
    ) : JsonNode

    public data object NullNode : JsonNode

    /** 不带引号的 `{{name}}`：发送时以变量的值原样替换。 */
    public data class VariableNode(
        val name: String,
    ) : JsonNode
}

public data class JsonMember(
    val key: String,
    val value: JsonNode,
)

public sealed interface JsonTemplateParse {
    public data class Success(
        val node: JsonNode,
    ) : JsonTemplateParse

    /** [line] 与 [column] 从 1 开始。 */
    public data class Failure(
        val error: JsonSyntaxError,
        val line: Int,
        val column: Int,
    ) : JsonTemplateParse
}

public enum class JsonSyntaxError {
    /** 只有空白。 */
    EMPTY,
    UNEXPECTED_CHARACTER,
    UNEXPECTED_END,
    UNTERMINATED_STRING,
    INVALID_ESCAPE,

    /** 字符串中出现未转义的控制字符（如换行）。 */
    CONTROL_CHARACTER,
    INVALID_NUMBER,

    /** 值结束后还有内容。 */
    TRAILING_CONTENT,
    TOO_DEEP,
}

public object JsonTemplate {
    public fun parse(text: String): JsonTemplateParse = JsonTemplateParser(text).parse()

    /** 以两个空格缩进格式化。 */
    public fun format(node: JsonNode): String = buildString { appendNode(node, indent = 0) }

    public fun fromElement(element: JsonElement): JsonNode =
        when (element) {
            JsonNull -> {
                JsonNode.NullNode
            }

            is JsonObject -> {
                JsonNode.ObjectNode(element.map { (key, value) -> JsonMember(key, fromElement(value)) })
            }

            is JsonArray -> {
                JsonNode.ArrayNode(element.map(::fromElement))
            }

            is JsonPrimitive -> {
                when {
                    element.isString -> JsonNode.StringNode(element.content)
                    element.booleanOrNull != null -> JsonNode.BooleanNode(element.content.toBooleanStrict())
                    else -> JsonNode.NumberNode(element.content)
                }
            }
        }

    /** 把 [value] 写成 JSON 字符串字面量（含引号）。 */
    public fun quote(value: String): String = buildString { appendQuoted(value) }

    private fun StringBuilder.appendNode(
        node: JsonNode,
        indent: Int,
    ) {
        when (node) {
            is JsonNode.ObjectNode -> {
                appendContainer('{', '}', node.members, indent) { member ->
                    appendQuoted(member.key)
                    append(": ")
                    appendNode(member.value, indent + 1)
                }
            }

            is JsonNode.ArrayNode -> {
                appendContainer('[', ']', node.items, indent) { appendNode(it, indent + 1) }
            }

            is JsonNode.StringNode -> {
                appendQuoted(node.value)
            }

            is JsonNode.NumberNode -> {
                append(node.literal)
            }

            is JsonNode.BooleanNode -> {
                append(node.value)
            }

            JsonNode.NullNode -> {
                append(NULL)
            }

            is JsonNode.VariableNode -> {
                append(Variables.reference(node.name))
            }
        }
    }

    private inline fun <T> StringBuilder.appendContainer(
        open: Char,
        close: Char,
        children: List<T>,
        indent: Int,
        appendChild: StringBuilder.(T) -> Unit,
    ) {
        append(open)
        if (children.isNotEmpty()) {
            children.forEachIndexed { index, child ->
                if (index > 0) append(',')
                append('\n')
                repeat(indent + 1) { append(INDENT) }
                appendChild(child)
            }
            append('\n')
            repeat(indent) { append(INDENT) }
        }
        append(close)
    }

    private fun StringBuilder.appendQuoted(value: String) {
        append('"')
        value.forEach { char ->
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char < ' ') append("\\u%04x".format(char.code)) else append(char)
            }
        }
        append('"')
    }

    internal const val NULL = "null"
    private const val INDENT = "  "
}

/** 递归下降解析；出错时报告第一个错误的位置。 */
private class JsonTemplateParser(
    private val text: String,
) {
    private var position = 0

    private class SyntaxException(
        val error: JsonSyntaxError,
        val at: Int,
    ) : Exception()

    fun parse(): JsonTemplateParse =
        try {
            skipWhitespace()
            if (position == text.length) fail(JsonSyntaxError.EMPTY)
            val node = value(depth = 0)
            skipWhitespace()
            if (position < text.length) fail(JsonSyntaxError.TRAILING_CONTENT)
            JsonTemplateParse.Success(node)
        } catch (exception: SyntaxException) {
            val (line, column) = lineAndColumn(exception.at)
            JsonTemplateParse.Failure(exception.error, line, column)
        }

    private fun value(depth: Int): JsonNode {
        if (depth > MAX_DEPTH) fail(JsonSyntaxError.TOO_DEEP)
        return when (val char = peek()) {
            '{' -> if (text.startsWith(VARIABLE_OPEN, position)) variable() else objectNode(depth)
            '[' -> arrayNode(depth)
            '"' -> JsonNode.StringNode(string())
            't' -> literal(TRUE, JsonNode.BooleanNode(true))
            'f' -> literal(FALSE, JsonNode.BooleanNode(false))
            'n' -> literal(JsonTemplate.NULL, JsonNode.NullNode)
            else -> if (char == '-' || char in '0'..'9') number() else fail(JsonSyntaxError.UNEXPECTED_CHARACTER)
        }
    }

    private fun objectNode(depth: Int): JsonNode {
        position++
        val members = mutableListOf<JsonMember>()
        skipWhitespace()
        if (peek() == '}') {
            position++
            return JsonNode.ObjectNode(members)
        }
        while (true) {
            skipWhitespace()
            if (peek() != '"') fail(JsonSyntaxError.UNEXPECTED_CHARACTER)
            val key = string()
            skipWhitespace()
            expect(':')
            skipWhitespace()
            members += JsonMember(key, value(depth + 1))
            skipWhitespace()
            when (peek()) {
                ',' -> position++
                '}' -> return JsonNode.ObjectNode(members).also { position++ }
                else -> fail(JsonSyntaxError.UNEXPECTED_CHARACTER)
            }
        }
    }

    private fun arrayNode(depth: Int): JsonNode {
        position++
        val items = mutableListOf<JsonNode>()
        skipWhitespace()
        if (peek() == ']') {
            position++
            return JsonNode.ArrayNode(items)
        }
        while (true) {
            skipWhitespace()
            items += value(depth + 1)
            skipWhitespace()
            when (peek()) {
                ',' -> position++
                ']' -> return JsonNode.ArrayNode(items).also { position++ }
                else -> fail(JsonSyntaxError.UNEXPECTED_CHARACTER)
            }
        }
    }

    private fun variable(): JsonNode {
        val end = text.indexOf(VARIABLE_CLOSE, position + VARIABLE_OPEN.length)
        val name = Variables.wholeReference(if (end < 0) "" else text.substring(position, end + VARIABLE_CLOSE.length))
        if (name == null) fail(JsonSyntaxError.UNEXPECTED_CHARACTER)
        position = end + VARIABLE_CLOSE.length
        return JsonNode.VariableNode(name)
    }

    private fun string(): String {
        val start = position
        position++
        val builder = StringBuilder()
        while (true) {
            if (position >= text.length) fail(JsonSyntaxError.UNTERMINATED_STRING, start)
            when (val char = text[position]) {
                '"' -> {
                    position++
                    return builder.toString()
                }

                '\\' -> {
                    builder.append(escape())
                }

                else -> {
                    if (char < ' ') fail(JsonSyntaxError.CONTROL_CHARACTER)
                    builder.append(char)
                    position++
                }
            }
        }
    }

    private fun escape(): Char {
        val start = position
        position++
        if (position >= text.length) fail(JsonSyntaxError.UNTERMINATED_STRING)
        val char = text[position++]
        return when (char) {
            '"', '\\', '/' -> {
                char
            }

            'b' -> {
                '\b'
            }

            'f' -> {
                '\u000C'
            }

            'n' -> {
                '\n'
            }

            'r' -> {
                '\r'
            }

            't' -> {
                '\t'
            }

            'u' -> {
                val hex = text.substring(position, minOf(position + UNICODE_DIGITS, text.length))
                val code = hex.takeIf { it.length == UNICODE_DIGITS }?.toIntOrNull(HEX_RADIX) ?: fail(JsonSyntaxError.INVALID_ESCAPE, start)
                position += UNICODE_DIGITS
                code.toChar()
            }

            else -> {
                fail(JsonSyntaxError.INVALID_ESCAPE, start)
            }
        }
    }

    private fun number(): JsonNode {
        val match = NUMBER.matchAt(text, position) ?: fail(JsonSyntaxError.INVALID_NUMBER)
        position = match.range.last + 1
        // 数字后紧跟字母或数字（如 `01`、`1x`）说明数字本身不合法，而不是后面多了内容。
        if (position < text.length && text[position].isLetterOrDigit()) fail(JsonSyntaxError.INVALID_NUMBER, match.range.first)
        return JsonNode.NumberNode(match.value)
    }

    private fun literal(
        word: String,
        node: JsonNode,
    ): JsonNode {
        if (!text.startsWith(word, position)) fail(JsonSyntaxError.UNEXPECTED_CHARACTER)
        position += word.length
        return node
    }

    private fun expect(char: Char) {
        if (peek() != char) fail(JsonSyntaxError.UNEXPECTED_CHARACTER)
        position++
    }

    private fun peek(): Char {
        if (position >= text.length) fail(JsonSyntaxError.UNEXPECTED_END)
        return text[position]
    }

    private fun skipWhitespace() {
        while (position < text.length && text[position] in WHITESPACE) position++
    }

    private fun fail(
        error: JsonSyntaxError,
        at: Int = position,
    ): Nothing = throw SyntaxException(error, at)

    private fun lineAndColumn(offset: Int): Pair<Int, Int> {
        var line = 1
        var lineStart = 0
        for (index in 0 until minOf(offset, text.length)) {
            if (text[index] == '\n') {
                line++
                lineStart = index + 1
            }
        }
        return line to offset - lineStart + 1
    }

    private companion object {
        const val MAX_DEPTH = 512
        const val UNICODE_DIGITS = 4
        const val HEX_RADIX = 16
        const val TRUE = "true"
        const val FALSE = "false"
        const val VARIABLE_OPEN = "{{"
        const val VARIABLE_CLOSE = "}}"
        val WHITESPACE = setOf(' ', '\t', '\n', '\r')
        val NUMBER = Regex("""-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?""")
    }
}
