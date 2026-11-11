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
import work.foofish.apinspector.backup.data.BackupDao
import work.foofish.apinspector.core.coroutines.ApplicationScope
import work.foofish.apinspector.core.coroutines.DefaultDispatcher
import work.foofish.apinspector.core.coroutines.IoDispatcher
import work.foofish.apinspector.core.db.AppDatabase
import work.foofish.apinspector.custom.CustomRequestRepository
import work.foofish.apinspector.custom.data.CustomRequestDao
import work.foofish.apinspector.custom.data.DefaultCustomRequestRepository
import work.foofish.apinspector.environment.CookieStore
import work.foofish.apinspector.environment.EnvironmentRepository
import work.foofish.apinspector.environment.data.DefaultEnvironmentRepository
import work.foofish.apinspector.environment.data.EnvironmentDao
import work.foofish.apinspector.extraction.ExtractionRuleRepository
import work.foofish.apinspector.extraction.data.DefaultExtractionRuleRepository
import work.foofish.apinspector.extraction.data.ExtractionRuleDao
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

/** 数据库与各 DAO。 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
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
    fun customRequestDao(database: AppDatabase): CustomRequestDao = database.customRequestDao()

    @Provides
    fun extractionRuleDao(database: AppDatabase): ExtractionRuleDao = database.extractionRuleDao()

    @Provides
    fun backupDao(database: AppDatabase): BackupDao = database.backupDao()
}

/** 自定义请求与提取规则（PRD 4.2、5）。 */
@Module
@InstallIn(SingletonComponent::class)
interface CustomRequestModule {
    @Binds
    fun customRequestRepository(repository: DefaultCustomRequestRepository): CustomRequestRepository

    @Binds
    fun extractionRuleRepository(repository: DefaultExtractionRuleRepository): ExtractionRuleRepository
}
