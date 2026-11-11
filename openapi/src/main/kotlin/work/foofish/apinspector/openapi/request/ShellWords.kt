package work.foofish.apinspector.openapi.request

/** 按 POSIX shell 的规则把一行命令拆成单词（cURL 导入，PRD 4.2）。 */
internal object ShellWords {
    /**
     * 支持单引号、双引号及其反斜杠转义、`$'…'`（ANSI-C 引号）与行尾 `\` 续行；不做变量展开、通配与命令替换。
     *
     * 引号没有闭合时返回 `null`。
     */
    fun split(command: String): List<String>? = ShellWordSplitter(command).split()
}

private class ShellWordSplitter(
    private val text: String,
) {
    private val words = mutableListOf<String>()
    private val word = StringBuilder()

    /** 空引号 `''` 也是一个单词，所以不能只看 [word] 是否为空。 */
    private var inWord = false
    private var index = 0

    fun split(): List<String>? {
        var closed = true
        while (closed && index < text.length) closed = step()
        flush()
        return words.takeIf { closed }
    }

    /** 处理一个字符（或一段引号）；引号没有闭合时返回 `false`。 */
    private fun step(): Boolean {
        val char = text[index++]
        return when {
            char.isWhitespace() -> {
                flush()
                true
            }

            char == ESCAPE -> {
                escape()
                true
            }

            char == SINGLE_QUOTE -> {
                single()
            }

            char == DOUBLE_QUOTE -> {
                double()
            }

            char == ANSI_PREFIX && text.getOrNull(index) == SINGLE_QUOTE -> {
                index++
                ansi()
            }

            else -> {
                append(char)
                true
            }
        }
    }

    private fun flush() {
        if (inWord) words += word.toString()
        word.clear()
        inWord = false
    }

    private fun append(char: Char) {
        word.append(char)
        inWord = true
    }

    /** 引号外的反斜杠：后面是换行时为续行，否则原样取下一个字符。 */
    private fun escape() {
        when (val next = text.getOrNull(index)) {
            null -> {}

            '\n' -> {
                index++
            }

            '\r' -> {
                index++
                if (text.getOrNull(index) == '\n') index++
            }

            else -> {
                append(next)
                index++
            }
        }
    }

    private fun single(): Boolean {
        val end = text.indexOf(SINGLE_QUOTE, index)
        inWord = true
        if (end >= 0) {
            word.append(text, index, end)
            index = end + 1
        }
        return end >= 0
    }

    /** 双引号内反斜杠只转义 `"`、`\`、`$` 与反引号，其余保留反斜杠。 */
    private fun double(): Boolean {
        var closed = false
        while (!closed && index < text.length) {
            val char = text[index++]
            when {
                char == DOUBLE_QUOTE -> {
                    closed = true
                }

                char == ESCAPE && index < text.length -> {
                    val next = text[index++]
                    when {
                        next in DOUBLE_QUOTE_ESCAPABLE -> {
                            word.append(next)
                        }

                        next == '\n' -> {}

                        else -> {
                            word.append(ESCAPE).append(next)
                        }
                    }
                }

                else -> {
                    word.append(char)
                }
            }
        }
        inWord = true
        return closed
    }

    private fun ansi(): Boolean {
        var closed = false
        while (!closed && index < text.length) {
            val char = text[index++]
            when {
                char == SINGLE_QUOTE -> closed = true
                char == ESCAPE && index < text.length -> ansiEscape(text[index++])
                else -> word.append(char)
            }
        }
        inWord = true
        return closed
    }

    private fun ansiEscape(char: Char) {
        val simple = ANSI_ESCAPES[char]
        when {
            simple != null -> word.append(simple)
            char == 'x' -> codePoint(char, HEX_ESCAPE_DIGITS)
            char == 'u' -> codePoint(char, UNICODE_ESCAPE_DIGITS)
            else -> word.append(ESCAPE).append(char)
        }
    }

    /** `\xHH` 与 `\uXXXX`：至多 [maxDigits] 位十六进制；没有数字时按原文保留。 */
    private fun codePoint(
        marker: Char,
        maxDigits: Int,
    ) {
        val digits = text.substring(index, minOf(index + maxDigits, text.length)).takeWhile { it.digitToIntOrNull(HEX_RADIX) != null }
        if (digits.isEmpty()) {
            word.append(ESCAPE).append(marker)
        } else {
            word.append(digits.toInt(HEX_RADIX).toChar())
            index += digits.length
        }
    }

    private companion object {
        const val ESCAPE = '\\'
        const val SINGLE_QUOTE = '\''
        const val DOUBLE_QUOTE = '"'
        const val ANSI_PREFIX = '$'
        const val DOUBLE_QUOTE_ESCAPABLE = "\"\\$`"
        const val HEX_RADIX = 16
        const val HEX_ESCAPE_DIGITS = 2
        const val UNICODE_ESCAPE_DIGITS = 4
        val ANSI_ESCAPES: Map<Char, Char> =
            mapOf(
                'n' to '\n',
                't' to '\t',
                'r' to '\r',
                'a' to '\u0007',
                'b' to '\b',
                'f' to '\u000C',
                'v' to '\u000B',
                'e' to '\u001B',
                '\\' to '\\',
                '\'' to '\'',
                '"' to '"',
            )
    }
}
