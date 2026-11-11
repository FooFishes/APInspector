package work.foofish.apinspector.core.db

import androidx.room3.AutoMigration
import androidx.room3.Database
import androidx.room3.RoomDatabase
import work.foofish.apinspector.backup.data.BackupDao
import work.foofish.apinspector.custom.data.CustomRequestDao
import work.foofish.apinspector.custom.data.CustomRequestEntity
import work.foofish.apinspector.environment.data.CookieEntity
import work.foofish.apinspector.environment.data.EnvironmentDao
import work.foofish.apinspector.environment.data.EnvironmentEntity
import work.foofish.apinspector.extraction.data.ExtractionRuleDao
import work.foofish.apinspector.extraction.data.ExtractionRuleEntity
import work.foofish.apinspector.history.data.HistoryDao
import work.foofish.apinspector.history.data.HistoryEntity
import work.foofish.apinspector.request.data.PresetEntity
import work.foofish.apinspector.request.data.RequestInputDao
import work.foofish.apinspector.request.data.WorkingParamsEntity
import work.foofish.apinspector.spec.data.FavoriteOperationEntity
import work.foofish.apinspector.spec.data.SpecDao
import work.foofish.apinspector.spec.data.SpecEntity

@Database(
    entities = [
        SpecEntity::class,
        FavoriteOperationEntity::class,
        EnvironmentEntity::class,
        CookieEntity::class,
        WorkingParamsEntity::class,
        PresetEntity::class,
        HistoryEntity::class,
        CustomRequestEntity::class,
        ExtractionRuleEntity::class,
    ],
    version = AppDatabase.VERSION,
    autoMigrations = [
        // 2：环境、Cookie 罐、当前参数、预设与请求记录（PRD 4–7）。
        AutoMigration(from = 1, to = 2),
        // 3：自定义请求与提取规则（PRD 4.2、5）；请求记录可以属于自定义请求或临时请求。
        AutoMigration(from = 2, to = 3),
    ],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun specDao(): SpecDao

    abstract fun environmentDao(): EnvironmentDao

    abstract fun requestInputDao(): RequestInputDao

    abstract fun historyDao(): HistoryDao

    abstract fun customRequestDao(): CustomRequestDao

    abstract fun extractionRuleDao(): ExtractionRuleDao

    abstract fun backupDao(): BackupDao

    companion object {
        const val NAME = "apinspector.db"

        /** 数据库版本；备份文件记下它，比它新的备份无法导入（PRD 8）。 */
        const val VERSION = 3
    }
}
