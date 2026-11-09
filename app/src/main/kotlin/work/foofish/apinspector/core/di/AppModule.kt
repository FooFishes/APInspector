package work.foofish.apinspector.core.di

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import work.foofish.apinspector.core.coroutines.DefaultDispatcher
import work.foofish.apinspector.core.coroutines.IoDispatcher
import work.foofish.apinspector.core.db.AppDatabase
import work.foofish.apinspector.spec.FavoriteOperationRepository
import work.foofish.apinspector.spec.SpecRepository
import work.foofish.apinspector.spec.data.DefaultSpecRepository
import work.foofish.apinspector.spec.data.SpecDao
import java.time.Clock
import java.time.Duration
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    /** 全局默认超时（PRD 6）。 */
    private val DEFAULT_TIMEOUT: Duration = Duration.ofSeconds(30)

    @Provides
    @Singleton
    fun database(
        @ApplicationContext context: Context,
    ): AppDatabase =
        Room
            .databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            // 使用系统自带的 SQLite，不额外打包原生库。
            .setDriver(AndroidSQLiteDriver())
            .build()

    @Provides
    fun specDao(database: AppDatabase): SpecDao = database.specDao()

    @Provides
    @Singleton
    fun okHttpClient(): OkHttpClient = OkHttpClient.Builder().callTimeout(DEFAULT_TIMEOUT).build()

    @Provides
    fun clock(): Clock = Clock.systemUTC()

    @Provides
    @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun defaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}

@Module
@InstallIn(SingletonComponent::class)
interface RepositoryModule {
    @Binds
    fun specRepository(repository: DefaultSpecRepository): SpecRepository

    @Binds
    fun favoriteOperationRepository(repository: DefaultSpecRepository): FavoriteOperationRepository
}
