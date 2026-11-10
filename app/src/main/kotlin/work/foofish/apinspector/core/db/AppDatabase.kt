package work.foofish.apinspector.core.db

import androidx.room3.AutoMigration
import androidx.room3.Database
import androidx.room3.RoomDatabase
import work.foofish.apinspector.environment.data.CookieEntity
import work.foofish.apinspector.environment.data.EnvironmentDao
import work.foofish.apinspector.environment.data.EnvironmentEntity
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
    ],
    version = 2,
    autoMigrations = [
        // 2：环境、Cookie 罐、当前参数、预设与请求记录（PRD 4–7）。
        AutoMigration(from = 1, to = 2),
    ],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun specDao(): SpecDao

    abstract fun environmentDao(): EnvironmentDao

    abstract fun requestInputDao(): RequestInputDao

    abstract fun historyDao(): HistoryDao

    companion object {
        const val NAME = "apinspector.db"
    }
}
