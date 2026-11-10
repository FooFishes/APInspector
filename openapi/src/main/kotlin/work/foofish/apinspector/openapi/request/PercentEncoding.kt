package work.foofish.apinspector.openapi.request

import java.io.ByteArrayOutputStream

/** 百分号编码（RFC 3986）与 `application/x-www-form-urlencoded` 编解码，均按 UTF-8。 */
internal object PercentEncoding {
    /** 只保留 RFC 3986 的非保留字符（字母、数字、`-._~`）与 [keep] 中的字符，其余全部编码。 */
    fun encode(
        value: String,
        keep: String = "",
    ): String =
        buildString {
            value.encodeToByteArray().forEach { byte ->
                val char = (byte.toInt() and BYTE_MASK).toChar()
                if (byte >= 0 && (char.isUnreserved() || char in keep)) append(char) else appendEscaped(byte)
            }
        }

    /** 表单编码：空格写作 `+`，其余同 HTML 的 `application/x-www-form-urlencoded` 序列化。 */
    fun encodeForm(value: String): String =
        buildString {
            value.encodeToByteArray().forEach { byte ->
                val char = (byte.toInt() and BYTE_MASK).toChar()
                when {
                    byte < 0 -> appendEscaped(byte)
                    char == ' ' -> append('+')
                    char.isAsciiLetterOrDigit() || char in FORM_SAFE -> append(char)
                    else -> appendEscaped(byte)
                }
            }
        }

    /** 表单解码：`+` 为空格；不完整或非法的 `%` 转义按原文保留。 */
    fun decodeForm(value: String): String {
        val bytes = ByteArrayOutputStream(value.length)
        var index = 0
        while (index < value.length) {
            val char = value[index]
            val escape = value.substring(index + 1, minOf(index + ESCAPE_LENGTH, value.length))
            when {
                char == '%' && escape.length == 2 && escape.all { it in HEX_DIGITS || it in HEX_DIGITS.lowercase() } -> {
                    bytes.write(escape.toInt(HEX_RADIX))
                    index += ESCAPE_LENGTH
                }

                char == '+' -> {
                    bytes.write(' '.code)
                    index++
                }

                else -> {
                    // 连续的普通字符整段编码，避免把代理对拆开。
                    val end = (index + 1 until value.length).firstOrNull { value[it] == '%' || value[it] == '+' } ?: value.length
                    bytes.write(value.substring(index, end).encodeToByteArray())
                    index = end
                }
            }
        }
        return bytes.toByteArray().decodeToString()
    }

    private fun StringBuilder.appendEscaped(byte: Byte) {
        val code = byte.toInt() and BYTE_MASK
        append('%')
        append(HEX_DIGITS[code shr NIBBLE_BITS])
        append(HEX_DIGITS[code and NIBBLE_MASK])
    }

    private fun Char.isAsciiLetterOrDigit(): Boolean = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'

    private fun Char.isUnreserved(): Boolean = isAsciiLetterOrDigit() || this in UNRESERVED_MARKS

    private const val UNRESERVED_MARKS = "-._~"
    private const val FORM_SAFE = "*-._"
    private const val HEX_DIGITS = "0123456789ABCDEF"
    private const val HEX_RADIX = 16
    private const val BYTE_MASK = 0xFF
    private const val NIBBLE_BITS = 4
    private const val NIBBLE_MASK = 0x0F
    private const val ESCAPE_LENGTH = 3
}
