package work.foofish.apinspector.response

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import work.foofish.apinspector.core.coroutines.IoDispatcher
import work.foofish.apinspector.history.HistoryRepository
import work.foofish.apinspector.history.StoredBody
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.IllegalCharsetNameException
import java.nio.charset.UnsupportedCharsetException
import javax.inject.Inject

/** 响应 Body 的显示内容（PRD 7.2）。 */
sealed interface BodyView {
    /** 没有 Body。 */
    data object Empty : BodyView

    /** Body 文件已被清理（手动或系统回收，PRD 7.1）。 */
    data object Cleared : BodyView

    /**
     * @property element 可以解析时的 JSON；截断或语法错误时为 `null`，只显示原文。
     * @property limited 内容超出显示上限，只显示了开头部分。
     * @property file 完整保存时可以另存的文件；被截断时为 `null`。
     */
    data class Json(
        val text: String,
        val element: JsonElement?,
        val limited: Boolean,
        val file: File?,
    ) : BodyView

    data class Text(
        val text: String,
        val limited: Boolean,
        val file: File?,
    ) : BodyView

    /** 完整保存的图片，可以预览与另存。 */
    data class Image(
        val file: File,
    ) : BodyView

    /** 二进制内容；[file] 为 `null` 时内容被截断，不能另存。 */
    data class Binary(
        val file: File?,
    ) : BodyView
}

/** 按 content type 读取并归类响应 Body。 */
class ResponseBodyLoader
    @Inject
    constructor(
        private val history: HistoryRepository,
        @IoDispatcher private val io: CoroutineDispatcher,
    ) {
        suspend fun load(
            body: StoredBody?,
            contentType: String?,
        ): BodyView {
            body ?: return BodyView.Empty
            val file = history.bodyFile(body) ?: return BodyView.Cleared
            return withContext(io) { classify(file, body, contentType) }
        }

        private fun classify(
            file: File,
            body: StoredBody,
            contentType: String?,
        ): BodyView {
            val mediaType = contentType?.substringBefore(';')?.trim()?.lowercase()
            return when {
                mediaType != null && mediaType.startsWith(IMAGE_PREFIX) -> {
                    if (body.truncated) BodyView.Binary(null) else BodyView.Image(file)
                }

                mediaType != null && isJson(mediaType) -> {
                    readText(file, contentType)?.let { json(it, file, body.truncated) }
                        ?: binary(file, body)
                }

                // 其余类型（包括未声明类型）：能解码为文本的按文本显示。
                else -> {
                    readText(file, contentType)?.let { BodyView.Text(it.text, it.limited, file.takeUnless { body.truncated }) }
                        ?: binary(file, body)
                }
            }
        }

        private fun json(
            text: LimitedText,
            file: File,
            truncated: Boolean,
        ): BodyView {
            val element =
                if (truncated || text.limited) {
                    null
                } else {
                    try {
                        Json.parseToJsonElement(text.text)
                    } catch (_: SerializationException) {
                        // 声明为 JSON 但内容不合法：按原文显示。
                        null
                    }
                }
            return BodyView.Json(text.text, element, text.limited, file.takeUnless { truncated })
        }

        private fun binary(
            file: File,
            body: StoredBody,
        ): BodyView = BodyView.Binary(file.takeUnless { body.truncated })

        /** 按 charset 严格解码前 [DISPLAY_LIMIT_BYTES] 字节；不是合法文本时返回 `null`。 */
        private fun readText(
            file: File,
            contentType: String?,
        ): LimitedText? {
            val limited = file.length() > DISPLAY_LIMIT_BYTES
            val bytes = file.inputStream().use { it.readNBytes(DISPLAY_LIMIT_BYTES) }
            val decoder =
                charset(contentType)
                    .newDecoder()
                    // 截断处可能切开多字节字符，结尾的不完整字符替换掉，中间的非法字节则说明不是文本。
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
            val text =
                try {
                    decoder.decode(ByteBuffer.wrap(bytes, 0, if (limited) safeLength(bytes) else bytes.size)).toString()
                } catch (_: CharacterCodingException) {
                    return null
                }
            if (NUL in text) return null
            return LimitedText(text, limited)
        }

        /** 截断时去掉末尾不完整的 UTF-8 序列。 */
        private fun safeLength(bytes: ByteArray): Int {
            var end = bytes.size
            var back = 0
            while (end > 0 && back < MAX_UTF8_BYTES && bytes[end - 1].toInt() and UTF8_CONTINUATION_MASK == UTF8_CONTINUATION) {
                end--
                back++
            }
            return if (end > 0 && bytes[end - 1].toInt() and UTF8_LEAD_MASK == UTF8_LEAD) end - 1 else bytes.size
        }

        private fun charset(contentType: String?): Charset {
            val name =
                contentType
                    ?.split(';')
                    ?.drop(1)
                    ?.map { it.trim() }
                    ?.firstOrNull { it.startsWith(CHARSET_PARAMETER, ignoreCase = true) }
                    ?.substringAfter('=')
                    ?.trim('"', ' ')
            return try {
                name?.let(Charset::forName) ?: Charsets.UTF_8
            } catch (_: IllegalCharsetNameException) {
                Charsets.UTF_8
            } catch (_: UnsupportedCharsetException) {
                Charsets.UTF_8
            }
        }

        private fun isJson(mediaType: String): Boolean = mediaType.endsWith(JSON_SUBTYPE) || mediaType.endsWith(JSON_SUFFIX)

        private data class LimitedText(
            val text: String,
            val limited: Boolean,
        )

        private companion object {
            /** 文本的显示上限：更大的内容只显示开头，完整内容可以另存。 */
            const val DISPLAY_LIMIT_BYTES = 2 * 1024 * 1024
            const val IMAGE_PREFIX = "image/"
            const val JSON_SUBTYPE = "/json"
            const val JSON_SUFFIX = "+json"
            const val CHARSET_PARAMETER = "charset="
            const val NUL = '\u0000'
            const val MAX_UTF8_BYTES = 3
            const val UTF8_CONTINUATION_MASK = 0xC0
            const val UTF8_CONTINUATION = 0x80
            const val UTF8_LEAD_MASK = 0xC0
            const val UTF8_LEAD = 0xC0
        }
    }
