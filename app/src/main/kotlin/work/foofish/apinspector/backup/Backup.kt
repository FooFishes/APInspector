package work.foofish.apinspector.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import work.foofish.apinspector.custom.data.CustomRequestEntity
import work.foofish.apinspector.environment.data.EnvironmentEntity
import work.foofish.apinspector.extraction.data.ExtractionRuleEntity
import work.foofish.apinspector.history.data.HistoryEntity
import work.foofish.apinspector.request.data.PresetEntity
import work.foofish.apinspector.request.data.WorkingParamsEntity
import work.foofish.apinspector.settings.ProxyConfig
import work.foofish.apinspector.spec.data.FavoriteOperationEntity
import work.foofish.apinspector.spec.data.SpecEntity

/**
 * 备份文件的内容（PRD 8）：API 文档、环境（含凭证）、预设与当前参数、收藏、自定义请求、提取规则、代理设置；
 * 请求记录可选，且不含响应 Body（它们在缓存目录里，导入后显示为"已清理"）。Cookie 是会话状态，不进备份。
 *
 * 各表按数据库中的行原样保存，[schema] 记下导出时的数据库版本。
 *
 * @property history 导出时没有勾选"包含请求记录"则为 `null`。
 */
@Serializable
data class Backup(
    val schema: Int,
    val createdAt: Long,
    val specs: List<SpecBackup>,
    val favorites: List<FavoriteOperationEntity>,
    val environments: List<EnvironmentEntity>,
    val presets: List<PresetEntity>,
    val working: List<WorkingParamsEntity>,
    val customRequests: List<CustomRequestEntity>,
    val rules: List<ExtractionRuleEntity>,
    val proxy: ProxyConfig?,
    val history: List<HistoryEntity>? = null,
)

/** 一份 API 文档及其原文；原文文件丢失时 [content] 为 `null`。 */
@Serializable
data class SpecBackup(
    val spec: SpecEntity,
    val content: String?,
)

/** 读取备份文件的结果。 */
sealed interface BackupRead {
    data class Ready(
        val backup: Backup,
    ) : BackupRead

    /** 文件已加密，需要密码。 */
    data object PasswordRequired : BackupRead

    /** 密码错误，或加密的文件已损坏（两者无法区分）。 */
    data object WrongPassword : BackupRead

    /** 不是备份文件，或内容已损坏。 */
    data object Invalid : BackupRead

    /** 由更新版本的应用导出，这个版本读不了。 */
    data object TooNew : BackupRead
}

/** 备份文件的编码：JSON，设了密码时再整体加密（[BackupCipher]）。 */
object BackupCodec {
    private val json = Json { ignoreUnknownKeys = true }

    /** [password] 为 `null` 或空时明文保存。 */
    fun encode(
        backup: Backup,
        password: CharArray?,
    ): ByteArray {
        val plain = json.encodeToString(Backup.serializer(), backup).encodeToByteArray()
        return if (password == null || password.isEmpty()) plain else BackupCipher.encrypt(plain, password)
    }

    /**
     * @param schema 当前的数据库版本；备份的版本比它新时无法导入。
     */
    fun decode(
        data: ByteArray,
        password: CharArray?,
        schema: Int,
    ): BackupRead {
        val plain =
            if (BackupCipher.isEncrypted(data)) {
                if (password == null) return BackupRead.PasswordRequired
                BackupCipher.decrypt(data, password) ?: return BackupRead.WrongPassword
            } else {
                data
            }
        val backup =
            try {
                json.decodeFromString(Backup.serializer(), plain.decodeToString())
            } catch (_: SerializationException) {
                // 不是这个应用的备份，或内容被改坏了。
                return BackupRead.Invalid
            }
        return if (backup.schema > schema) BackupRead.TooNew else BackupRead.Ready(backup)
    }
}
