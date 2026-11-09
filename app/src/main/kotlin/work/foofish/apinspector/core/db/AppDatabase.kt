package work.foofish.apinspector.core.db

import androidx.room3.Database
import androidx.room3.RoomDatabase
import work.foofish.apinspector.spec.data.FavoriteOperationEntity
import work.foofish.apinspector.spec.data.SpecDao
import work.foofish.apinspector.spec.data.SpecEntity

@Database(
    entities = [SpecEntity::class, FavoriteOperationEntity::class],
    version = 1,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun specDao(): SpecDao

    companion object {
        const val NAME = "apinspector.db"
    }
}
