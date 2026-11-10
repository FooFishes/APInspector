package work.foofish.apinspector.settings

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** 全局设置的读写。 */
interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings)
}

@Singleton
class DataStoreSettingsRepository
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : SettingsRepository {
        private val store: DataStore<AppSettings> =
            DataStoreFactory.create(
                serializer = AppSettingsSerializer,
                // 设置文件损坏时回到默认设置，而不是让读取一直失败。
                corruptionHandler = ReplaceFileCorruptionHandler { AppSettings() },
            ) { context.filesDir.resolve(FILE_NAME) }

        override val settings: Flow<AppSettings> = store.data

        override suspend fun update(transform: (AppSettings) -> AppSettings) {
            store.updateData(transform)
        }

        private companion object {
            const val FILE_NAME = "datastore/settings.json"
        }
    }

private object AppSettingsSerializer : Serializer<AppSettings> {
    /** 新增字段取默认值；旧版本留下的未知字段忽略。 */
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    override val defaultValue: AppSettings = AppSettings()

    override suspend fun readFrom(input: InputStream): AppSettings =
        try {
            json.decodeFromString(AppSettings.serializer(), input.readBytes().decodeToString())
        } catch (exception: SerializationException) {
            throw CorruptionException("设置文件无法解析", exception)
        }

    override suspend fun writeTo(
        t: AppSettings,
        output: OutputStream,
    ) {
        output.write(json.encodeToString(AppSettings.serializer(), t).encodeToByteArray())
    }
}
