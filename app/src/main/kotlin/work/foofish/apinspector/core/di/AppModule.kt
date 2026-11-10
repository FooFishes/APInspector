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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import work.foofish.apinspector.core.coroutines.ApplicationScope
import work.foofish.apinspector.core.coroutines.DefaultDispatcher
import work.foofish.apinspector.core.coroutines.IoDispatcher
import work.foofish.apinspector.core.db.AppDatabase
import work.foofish.apinspector.environment.CookieStore
import work.foofish.apinspector.environment.EnvironmentRepository
import work.foofish.apinspector.environment.data.DefaultEnvironmentRepository
import work.foofish.apinspector.environment.data.EnvironmentDao
import work.foofish.apinspector.history.HistoryRepository
import work.foofish.apinspector.history.ResponseCache
import work.foofish.apinspector.history.data.DefaultHistoryRepository
import work.foofish.apinspector.history.data.HistoryDao
import work.foofish.apinspector.network.ContentResolverFileAccess
import work.foofish.apinspector.network.FileAccess
import work.foofish.apinspector.network.RequestExecutor
import work.foofish.apinspector.network.RequestSender
import work.foofish.apinspector.network.UserAgent
import work.foofish.apinspector.request.RequestInputRepository
import work.foofish.apinspector.request.data.DefaultRequestInputRepository
import work.foofish.apinspector.request.data.RequestInputDao
import work.foofish.apinspector.settings.DataStoreSettingsRepository
import work.foofish.apinspector.settings.SettingsRepository
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
    fun environmentDao(database: AppDatabase): EnvironmentDao = database.environmentDao()

    @Provides
    fun requestInputDao(database: AppDatabase): RequestInputDao = database.requestInputDao()

    @Provides
    fun historyDao(database: AppDatabase): HistoryDao = database.historyDao()

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 自动附加的 `User-Agent`（PRD 4.3）：`APInspector/版本号`。 */
    @Provides
    @UserAgent
    fun userAgent(
        @ApplicationContext context: Context,
    ): String = "APInspector/" + context.packageManager.getPackageInfo(context.packageName, 0).versionName

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

    @Binds
    fun environmentRepository(repository: DefaultEnvironmentRepository): EnvironmentRepository

    @Binds
    fun cookieStore(repository: DefaultEnvironmentRepository): CookieStore

    @Binds
    fun requestInputRepository(repository: DefaultRequestInputRepository): RequestInputRepository

    @Binds
    fun historyRepository(repository: DefaultHistoryRepository): HistoryRepository

    @Binds
    fun responseCache(repository: DefaultHistoryRepository): ResponseCache

    @Binds
    fun settingsRepository(repository: DataStoreSettingsRepository): SettingsRepository

    @Binds
    fun requestSender(executor: RequestExecutor): RequestSender

    @Binds
    fun fileAccess(access: ContentResolverFileAccess): FileAccess
}
