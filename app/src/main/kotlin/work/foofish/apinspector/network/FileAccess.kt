package work.foofish.apinspector.network

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import work.foofish.apinspector.core.coroutines.IoDispatcher
import work.foofish.apinspector.openapi.request.FileRef
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/** 请求 Body 引用的文件无法读取（被删除或授权失效，PRD 4.3）。 */
class FileUnavailableException(
    val file: FileRef,
    cause: Throwable,
) : IOException("文件不可用：${file.name}", cause)

/** 访问用户通过系统文件选择器选择的文件：只保存引用，发送时再读取（PRD 4.3）。 */
interface FileAccess {
    /** 申请持久读取授权并读取文件名、大小与类型；无法访问时返回 `null`。 */
    suspend fun adopt(uri: Uri): FileRef?

    /** 文件仍可读取（存在且授权有效）。 */
    suspend fun isReadable(file: FileRef): Boolean

    /** 每次写出时重新打开文件，以便重试与重定向时重发；无法读取时抛出 [FileUnavailableException]。 */
    fun requestBody(
        file: FileRef,
        contentType: MediaType?,
    ): RequestBody

    /** 把响应 Body 另存到用户选择的位置。 */
    suspend fun save(
        from: File,
        to: Uri,
    )
}

/** 通过 ContentResolver 访问文件。 */
@Singleton
class ContentResolverFileAccess
    @Inject
    constructor(
        @ApplicationContext context: Context,
        @IoDispatcher private val io: CoroutineDispatcher,
    ) : FileAccess {
        private val resolver: ContentResolver = context.contentResolver

        override suspend fun adopt(uri: Uri): FileRef? =
            withContext(io) {
                try {
                    resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                        if (!cursor.moveToFirst()) return@use null
                        val name = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                        val sizeColumn = cursor.getColumnIndexOrThrow(OpenableColumns.SIZE)
                        val size = if (cursor.isNull(sizeColumn)) null else cursor.getLong(sizeColumn)
                        FileRef(uri.toString(), name ?: uri.lastPathSegment.orEmpty(), size, resolver.getType(uri))
                    }
                } catch (_: SecurityException) {
                    // 提供方不支持持久授权或已拒绝访问：视为无法选择该文件。
                    null
                }
            }

        override suspend fun isReadable(file: FileRef): Boolean =
            withContext(io) {
                try {
                    resolver.openAssetFileDescriptor(file.uri.toUri(), READ_MODE)?.use { true } ?: false
                } catch (_: FileNotFoundException) {
                    false
                } catch (_: SecurityException) {
                    false
                }
            }

        override fun requestBody(
            file: FileRef,
            contentType: MediaType?,
        ): RequestBody =
            object : RequestBody() {
                override fun contentType(): MediaType? = contentType

                override fun contentLength(): Long = file.size ?: -1L

                override fun writeTo(sink: BufferedSink) {
                    open(file).source().use(sink::writeAll)
                }
            }

        override suspend fun save(
            from: File,
            to: Uri,
        ) {
            withContext(io) {
                val output = resolver.openOutputStream(to) ?: throw FileNotFoundException(to.toString())
                output.use { stream -> from.inputStream().use { it.copyTo(stream) } }
            }
        }

        /** 打开文件；文件已删除或授权已失效时抛出 [FileUnavailableException]。 */
        private fun open(file: FileRef): InputStream =
            try {
                resolver.openInputStream(file.uri.toUri())
            } catch (exception: FileNotFoundException) {
                unavailable(file, exception)
            } catch (exception: SecurityException) {
                unavailable(file, exception)
            } ?: unavailable(file, FileNotFoundException(file.uri))

        private fun unavailable(
            file: FileRef,
            cause: Exception,
        ): Nothing = throw FileUnavailableException(file, cause)

        private companion object {
            const val READ_MODE = "r"
        }
    }
