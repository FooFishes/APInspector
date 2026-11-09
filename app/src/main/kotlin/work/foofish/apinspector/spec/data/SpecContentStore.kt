package work.foofish.apinspector.spec.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import work.foofish.apinspector.core.coroutines.IoDispatcher
import work.foofish.apinspector.spec.SpecId
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.moveTo
import kotlin.io.path.readText
import kotlin.io.path.writeText

/** 以文件保存 API 文档原文，每份一个文件。 */
@Singleton
class SpecContentStore
    @Inject
    constructor(
        @ApplicationContext context: Context,
        @IoDispatcher private val io: CoroutineDispatcher,
    ) {
        private val directory: Path = context.filesDir.toPath().resolve(DIRECTORY)

        /** 先写临时文件再原子替换，避免中途失败留下半个文件。 */
        suspend fun write(
            id: SpecId,
            content: String,
        ) = withContext(io) {
            directory.createDirectories()
            val temporary = createTempFile(directory, id.value, TEMPORARY_SUFFIX)
            temporary.writeText(content)
            temporary.moveTo(file(id), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }

        /** 文件不存在时返回 `null`。 */
        suspend fun read(id: SpecId): String? = withContext(io) { file(id).takeIf { it.exists() }?.readText() }

        suspend fun delete(id: SpecId) {
            withContext(io) { file(id).deleteIfExists() }
        }

        private fun file(id: SpecId): Path = directory.resolve(id.value + CONTENT_SUFFIX)

        private companion object {
            const val DIRECTORY = "specs"
            const val CONTENT_SUFFIX = ".spec"
            const val TEMPORARY_SUFFIX = ".tmp"
        }
    }
