package work.foofish.apinspector.openapi.parse

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.io.ByteArrayOutputStream

/**
 * RFC 6901 JSON Pointer，以及 `$ref` 使用的 URI 片段写法（RFC 6901 §6）。
 */
internal data class JsonPointer(
    val tokens: List<String>,
) {
    fun child(token: String): JsonPointer = JsonPointer(tokens + token)

    fun child(index: Int): JsonPointer = child(index.toString())

    /** 在 [root] 中定位该指针；任一段不存在时返回 `null`。 */
    fun evaluate(root: JsonElement): JsonElement? =
        tokens.fold<String, JsonElement?>(root) { node, token ->
            when (node) {
                is JsonObject -> node[token]
                is JsonArray -> token.toArrayIndex()?.let(node::getOrNull)
                else -> null
            }
        }

    /** 规范化的 URI 片段形式，例如 `#/paths/~1users/get`。 */
    fun toFragment(): String = FRAGMENT_PREFIX + tokens.joinToString(separator = "") { SEPARATOR + escape(it) }

    override fun toString(): String = toFragment()

    companion object {
        val ROOT: JsonPointer = JsonPointer(emptyList())

        private const val FRAGMENT_PREFIX = "#"
        private const val SEPARATOR = "/"
        private const val ESCAPE = '~'
        private const val ESCAPED_TILDE = '0'
        private const val ESCAPED_SLASH = '1'
        private const val PERCENT = '%'
        private const val HEX_RADIX = 16
        private const val PERCENT_ESCAPE_LENGTH = 3
        private val ARRAY_INDEX = Regex("0|[1-9][0-9]*")

        /** 解析 `#/a/b` 形式的 URI 片段；不是合法片段时返回 `null`。 */
        fun fromFragment(fragment: String): JsonPointer? {
            val pointer = fragment.removePrefixOrNull(FRAGMENT_PREFIX)?.let(::percentDecode)
            return when {
                pointer == null -> {
                    null
                }

                pointer.isEmpty() -> {
                    ROOT
                }

                !pointer.startsWith(SEPARATOR) -> {
                    null
                }

                else -> {
                    pointer
                        .substring(SEPARATOR.length)
                        .split(SEPARATOR)
                        .map { unescape(it) ?: return null }
                        .let(::JsonPointer)
                }
            }
        }

        private fun escape(token: String): String =
            token.replace(ESCAPE.toString(), "$ESCAPE$ESCAPED_TILDE").replace(SEPARATOR, "$ESCAPE$ESCAPED_SLASH")

        private fun unescape(token: String): String? =
            buildString {
                var index = 0
                while (index < token.length) {
                    val char = token[index]
                    if (char == ESCAPE) {
                        when (token.getOrNull(index + 1)) {
                            ESCAPED_TILDE -> append(ESCAPE)
                            ESCAPED_SLASH -> append(SEPARATOR)
                            else -> return null
                        }
                        index += 2
                    } else {
                        append(char)
                        index++
                    }
                }
            }

        /** 解码 URI 片段中的 `%XX` 序列（按 UTF-8）；序列非法时返回 `null`。 */
        private fun percentDecode(text: String): String? {
            if (PERCENT !in text) return text
            val result = StringBuilder(text.length)
            var index = 0
            while (index < text.length) {
                if (text[index] != PERCENT) {
                    result.append(text[index])
                    index++
                    continue
                }
                val bytes = ByteArrayOutputStream()
                while (index < text.length && text[index] == PERCENT) {
                    val byte =
                        text
                            .substring(index + 1, minOf(index + PERCENT_ESCAPE_LENGTH, text.length))
                            .takeIf { it.length == PERCENT_ESCAPE_LENGTH - 1 }
                            ?.toIntOrNull(HEX_RADIX)
                            ?: return null
                    bytes.write(byte)
                    index += PERCENT_ESCAPE_LENGTH
                }
                result.append(decodeUtf8(bytes.toByteArray()) ?: return null)
            }
            return result.toString()
        }

        /** 合法的 UTF-8 解码后再编码必定得到原字节；非法序列会被替换为 U+FFFD，从而不再一致。 */
        private fun decodeUtf8(bytes: ByteArray): String? =
            String(bytes, Charsets.UTF_8).takeIf { it.toByteArray(Charsets.UTF_8).contentEquals(bytes) }

        private fun String.toArrayIndex(): Int? = takeIf(ARRAY_INDEX::matches)?.toIntOrNull()

        private fun String.removePrefixOrNull(prefix: String): String? = if (startsWith(prefix)) substring(prefix.length) else null
    }
}
