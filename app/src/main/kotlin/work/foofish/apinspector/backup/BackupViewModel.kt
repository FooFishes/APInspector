package work.foofish.apinspector.backup

import android.database.SQLException
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import work.foofish.apinspector.core.coroutines.DefaultDispatcher
import work.foofish.apinspector.core.db.AppDatabase
import java.io.IOException
import javax.inject.Inject

/** 导出时的选项：是否包含请求记录；[password] 为空则明文保存。 */
data class ExportOptions(
    val includeHistory: Boolean,
    val password: String,
)

/** 备份文件里有什么，导入前给用户确认。 */
data class BackupSummary(
    val createdAt: Long,
    val specs: Int,
    val environments: Int,
    val customRequests: Int,
    /** 备份不含请求记录时为 `null`。 */
    val history: Int?,
)

enum class BackupFailure {
    /** 文件读写失败。 */
    IO,

    /** 不是备份文件，或内容已损坏。 */
    INVALID,

    /** 由更新版本的应用导出。 */
    TOO_NEW,
}

sealed interface BackupStatus {
    data object Idle : BackupStatus

    data object Working : BackupStatus

    data object Exported : BackupStatus

    /** 选中的备份已加密；[wrong]：刚才输入的密码不对。 */
    data class PasswordRequired(
        val wrong: Boolean,
    ) : BackupStatus

    /** 等待确认"覆盖全部"。 */
    data class Confirming(
        val summary: BackupSummary,
    ) : BackupStatus

    data object Imported : BackupStatus

    data class Failed(
        val failure: BackupFailure,
    ) : BackupStatus
}

/** 备份的导出与导入流程（PRD 8）。 */
@HiltViewModel
class BackupViewModel
    @Inject
    constructor(
        private val manager: BackupManager,
        private val files: BackupFiles,
        @DefaultDispatcher private val computation: CoroutineDispatcher,
    ) : ViewModel() {
        private val state = MutableStateFlow<BackupStatus>(BackupStatus.Idle)
        val status: StateFlow<BackupStatus> = state.asStateFlow()

        /** 选中的备份文件的内容，等待输入密码。 */
        private var pendingData: ByteArray? = null

        /** 已读出的备份，等待确认导入。 */
        private var pendingBackup: Backup? = null

        /** 导出到 [uri]；[password] 为空则明文保存。 */
        fun export(
            uri: Uri,
            includeHistory: Boolean,
            password: String,
        ) {
            run {
                val backup = manager.snapshot(includeHistory)
                // 密钥派生较慢，放到计算线程。
                val data = withContext(computation) { BackupCodec.encode(backup, password.toCharArray()) }
                files.write(uri, data)
                BackupStatus.Exported
            }
        }

        /** 读取选中的备份文件：已加密则要密码，否则进入确认。 */
        fun open(uri: Uri) {
            run {
                val data = files.read(uri)
                pendingData = data
                decode(data, password = null)
            }
        }

        fun submitPassword(password: String) {
            val data = pendingData ?: return
            run { decode(data, password.toCharArray()) }
        }

        /** 确认"覆盖全部"。 */
        fun confirmImport() {
            val backup = pendingBackup ?: return
            run {
                manager.restore(backup)
                clearPending()
                BackupStatus.Imported
            }
        }

        fun dismiss() {
            clearPending()
            state.value = BackupStatus.Idle
        }

        private suspend fun decode(
            data: ByteArray,
            password: CharArray?,
        ): BackupStatus =
            when (val read = withContext(computation) { BackupCodec.decode(data, password, AppDatabase.VERSION) }) {
                is BackupRead.Ready -> {
                    pendingData = null
                    pendingBackup = read.backup
                    BackupStatus.Confirming(read.backup.summary())
                }

                BackupRead.PasswordRequired -> {
                    BackupStatus.PasswordRequired(wrong = false)
                }

                BackupRead.WrongPassword -> {
                    BackupStatus.PasswordRequired(wrong = true)
                }

                BackupRead.Invalid -> {
                    failed(BackupFailure.INVALID)
                }

                BackupRead.TooNew -> {
                    failed(BackupFailure.TOO_NEW)
                }
            }

        private fun failed(failure: BackupFailure): BackupStatus {
            clearPending()
            return BackupStatus.Failed(failure)
        }

        private fun clearPending() {
            pendingData = null
            pendingBackup = null
        }

        /** 一次只进行一步；文件读写失败统一报告为 [BackupFailure.IO]。 */
        private fun run(step: suspend () -> BackupStatus) {
            if (state.value == BackupStatus.Working) return
            state.value = BackupStatus.Working
            viewModelScope.launch {
                state.value =
                    try {
                        step()
                    } catch (_: IOException) {
                        failed(BackupFailure.IO)
                    } catch (_: SQLException) {
                        // 备份里的数据互相对不上（例如环境指向不存在的 API 文档）：事务已回滚，现有数据不变。
                        failed(BackupFailure.INVALID)
                    } catch (_: SecurityException) {
                        // 文件提供方收回了授权。
                        failed(BackupFailure.IO)
                    }
            }
        }
    }

private fun Backup.summary(): BackupSummary =
    BackupSummary(
        createdAt = createdAt,
        specs = specs.size,
        environments = environments.size,
        customRequests = customRequests.size,
        history = history?.size,
    )
