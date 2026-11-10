package work.foofish.apinspector.history.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import work.foofish.apinspector.core.coroutines.IoDispatcher
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 响应 Body 文件（PRD 7.1）：放在缓存目录中，可以手动清理，系统存储紧张时也可能被回收。
 * 文件不存在即视为"已清理"。
 */
@Singleton
class ResponseBodyStore
    @Inject
    constructor(
        @ApplicationContext context: Context,
        @IoDispatcher private val io: CoroutineDispatcher,
    ) {
        private val directory = context.cacheDir.resolve(DIRECTORY)

        /** 为新的响应准备一个文件；写入失败时调用方负责删除。 */
        suspend fun newFile(): File =
            withContext(io) {
                directory.mkdirs()
                directory.resolve(UUID.randomUUID().toString() + SUFFIX)
            }

        /** 文件已被清理时返回 `null`。 */
        suspend fun existing(name: String): File? = withContext(io) { directory.resolve(name).takeIf(File::isFile) }

        suspend fun delete(names: Collection<String>) {
            if (names.isEmpty()) return
            withContext(io) { names.forEach { directory.resolve(it).delete() } }
        }

        /** 已用空间（字节）。 */
        suspend fun size(): Long = withContext(io) { directory.listFiles()?.sumOf(File::length) ?: 0L }

        suspend fun clear() {
            withContext(io) { directory.listFiles()?.forEach(File::delete) }
        }

        private companion object {
            const val DIRECTORY = "responses"
            const val SUFFIX = ".body"
        }
    }
