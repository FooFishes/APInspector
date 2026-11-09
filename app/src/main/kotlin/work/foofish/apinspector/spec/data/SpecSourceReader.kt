package work.foofish.apinspector.spec.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.coroutines.executeAsync
import okio.BufferedSource
import okio.buffer
import okio.source
import work.foofish.apinspector.core.coroutines.IoDispatcher
import work.foofish.apinspector.spec.ImportFailure
import work.foofish.apinspector.spec.ImportSource
import work.foofish.apinspector.spec.SpecOrigin
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** 读取导入来源的原文。 */
@Singleton
class SpecSourceReader
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val client: OkHttpClient,
        @IoDispatcher private val io: CoroutineDispatcher,
    ) {
        sealed interface Result {
            data class Read(
                val text: String,
                val origin: SpecOrigin,
            ) : Result

            data class Failed(
                val failure: ImportFailure,
            ) : Result
        }

        suspend fun read(source: ImportSource): Result =
            when (source) {
                is ImportSource.Text -> Result.Read(source.text, SpecOrigin.Pasted)
                is ImportSource.Url -> fetch(source.url.trim())
                is ImportSource.File -> open(source.uri)
            }

        private suspend fun fetch(url: String): Result {
            val httpUrl = url.toHttpUrlOrNull() ?: return Result.Failed(ImportFailure.InvalidUrl)
            val request =
                Request
                    .Builder()
                    .url(httpUrl)
                    .header(ACCEPT_HEADER, ACCEPTED_TYPES)
                    .build()
            return try {
                client.newCall(request).executeAsync().use { response ->
                    if (!response.isSuccessful) {
                        Result.Failed(ImportFailure.HttpStatus(response.code))
                    } else {
                        withContext(io) { response.body.source().readLimited(SpecOrigin.Url(url)) }
                    }
                }
            } catch (e: IOException) {
                Result.Failed(e.toFailure())
            }
        }

        private suspend fun open(uri: Uri): Result =
            withContext(io) {
                try {
                    // 内容提供方崩溃时返回 null。
                    val stream =
                        context.contentResolver.openInputStream(uri)
                            ?: return@withContext Result.Failed(ImportFailure.Unreadable(uri.toString()))
                    stream.source().buffer().use { it.readLimited(SpecOrigin.File(displayName(uri))) }
                } catch (e: IOException) {
                    Result.Failed(e.toFailure())
                }
            }

        private fun displayName(uri: Uri): String? =
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                cursor.takeIf { it.moveToFirst() }?.getString(0)
            }

        /** 超过 [MAX_SPEC_BYTES] 的内容不读入内存。 */
        private fun BufferedSource.readLimited(origin: SpecOrigin): Result =
            if (request(MAX_SPEC_BYTES + 1)) {
                Result.Failed(ImportFailure.TooLarge(MAX_SPEC_BYTES))
            } else {
                Result.Read(readUtf8(), origin)
            }

        private fun IOException.toFailure(): ImportFailure = ImportFailure.Unreadable(message ?: javaClass.simpleName)

        private companion object {
            /** 远大于常见的大型文档（数 MB），同时避免误选超大文件耗尽内存。 */
            const val MAX_SPEC_BYTES = 32L * 1024 * 1024
            const val ACCEPT_HEADER = "Accept"
            const val ACCEPTED_TYPES = "application/json, application/yaml, application/x-yaml, text/yaml, */*;q=0.8"
        }
    }
