package work.foofish.apinspector.backup

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import work.foofish.apinspector.backup.data.BackupDao
import work.foofish.apinspector.core.coroutines.IoDispatcher
import work.foofish.apinspector.core.db.AppDatabase
import work.foofish.apinspector.history.data.ResponseBodyStore
import work.foofish.apinspector.settings.SettingsRepository
import work.foofish.apinspector.spec.SpecId
import work.foofish.apinspector.spec.data.SpecContentStore
import java.io.FileNotFoundException
import java.time.Clock
import javax.inject.Inject

/** 备份的导出与"覆盖全部"导入（PRD 8）。 */
class BackupManager
    @Inject
    constructor(
        private val dao: BackupDao,
        private val contents: SpecContentStore,
        private val settings: SettingsRepository,
        private val bodies: ResponseBodyStore,
        private val clock: Clock,
    ) {
        /** 读出当前的全部数据。 */
        suspend fun snapshot(includeHistory: Boolean): Backup {
            val specs = dao.specs()
            return dao.snapshot(AppDatabase.VERSION, clock.millis(), includeHistory).copy(
                specs = specs.map { SpecBackup(it, contents.read(SpecId(it.id))) },
                proxy = settings.current().proxy,
            )
        }

        /** 用 [backup] 覆盖全部数据；响应缓存一并清空（备份里没有响应 Body）。 */
        suspend fun restore(backup: Backup) {
            val previous = dao.specs().map { it.id }
            dao.replaceAll(backup)
            val kept = backup.specs.mapTo(HashSet()) { it.spec.id }
            previous.filterNot(kept::contains).forEach { contents.delete(SpecId(it)) }
            backup.specs.forEach { (spec, content) ->
                if (content == null) contents.delete(SpecId(spec.id)) else contents.write(SpecId(spec.id), content)
            }
            bodies.clear()
            settings.update { it.copy(proxy = backup.proxy) }
        }
    }

/** 读写用户在系统文件选择器里选的备份文件。 */
class BackupFiles
    @Inject
    constructor(
        @ApplicationContext context: Context,
        @IoDispatcher private val io: CoroutineDispatcher,
    ) {
        private val resolver = context.contentResolver

        /** 打不开或读写失败时抛出 `IOException`。 */
        suspend fun read(uri: Uri): ByteArray =
            withContext(io) {
                val input = resolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString())
                input.use { it.readBytes() }
            }

        suspend fun write(
            uri: Uri,
            data: ByteArray,
        ) {
            withContext(io) {
                val output = resolver.openOutputStream(uri, TRUNCATE_MODE) ?: throw FileNotFoundException(uri.toString())
                output.use { it.write(data) }
            }
        }

        private companion object {
            /** 覆盖已有文件时截断，避免旧内容残留在末尾。 */
            const val TRUNCATE_MODE = "wt"
        }
    }
