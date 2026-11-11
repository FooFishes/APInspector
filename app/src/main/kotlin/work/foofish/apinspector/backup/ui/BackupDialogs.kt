package work.foofish.apinspector.backup.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import work.foofish.apinspector.R
import work.foofish.apinspector.backup.BackupFailure
import work.foofish.apinspector.backup.BackupStatus
import work.foofish.apinspector.backup.BackupSummary
import work.foofish.apinspector.backup.ExportOptions
import work.foofish.apinspector.ui.component.ConfirmDialog
import work.foofish.apinspector.ui.component.fieldColors
import work.foofish.apinspector.ui.text.formatTime
import java.time.Instant

/** 导出选项：是否包含请求记录，以及可选的密码（留空则明文保存）。 */
@Composable
internal fun ExportDialog(
    onConfirm: (ExportOptions) -> Unit,
    onDismiss: () -> Unit,
) {
    var includeHistory by rememberSaveable { mutableStateOf(false) }
    val password = rememberTextFieldState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_export)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(DialogSpacing)) {
                Text(stringResource(R.string.backup_export_body))
                Row(
                    modifier =
                        Modifier.fillMaxWidth().toggleable(includeHistory, role = Role.Checkbox, onValueChange = {
                            includeHistory =
                                it
                        }),
                    horizontalArrangement = Arrangement.spacedBy(DialogSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = includeHistory, onCheckedChange = null)
                    Text(stringResource(R.string.backup_include_history))
                }
                OutlinedSecureTextField(
                    state = password,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.backup_password_optional)) },
                    supportingText = { Text(stringResource(R.string.backup_password_hint)) },
                    shape = MaterialTheme.shapes.medium,
                    colors = fieldColors(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(ExportOptions(includeHistory, password.text.toString())) }) {
                Text(stringResource(R.string.backup_choose_location))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** 导出 / 导入各阶段的对话框。 */
@Composable
internal fun BackupStatusDialogs(
    status: BackupStatus,
    onSubmitPassword: (String) -> Unit,
    onConfirmImport: () -> Unit,
    onDismiss: () -> Unit,
    onImportComplete: () -> Unit,
) {
    when (status) {
        BackupStatus.Idle, BackupStatus.Working -> {}

        BackupStatus.Exported -> {
            MessageDialog(stringResource(R.string.backup_exported), body = null, onDismiss)
        }

        is BackupStatus.PasswordRequired -> {
            PasswordDialog(status.wrong, onSubmitPassword, onDismiss)
        }

        is BackupStatus.Confirming -> {
            ConfirmDialog(
                title = stringResource(R.string.backup_confirm_title),
                body = status.summary.describe(),
                confirmText = stringResource(R.string.backup_confirm_action),
                onConfirm = onConfirmImport,
                onDismiss = onDismiss,
                destructive = true,
            )
        }

        BackupStatus.Imported -> {
            MessageDialog(stringResource(R.string.backup_imported), stringResource(R.string.backup_imported_body), onImportComplete)
        }

        is BackupStatus.Failed -> {
            MessageDialog(stringResource(R.string.backup_failed), stringResource(status.failure.message()), onDismiss)
        }
    }
}

@Composable
private fun BackupSummary.describe(): String {
    val history = history?.let { stringResource(R.string.backup_summary_history, it) } ?: stringResource(R.string.backup_summary_no_history)
    return stringResource(
        R.string.backup_confirm_body,
        formatTime(Instant.ofEpochMilli(createdAt)),
        specs,
        environments,
        customRequests,
        history,
    )
}

private fun BackupFailure.message(): Int =
    when (this) {
        BackupFailure.IO -> R.string.backup_failure_io
        BackupFailure.INVALID -> R.string.backup_failure_invalid
        BackupFailure.TOO_NEW -> R.string.backup_failure_too_new
    }

@Composable
private fun PasswordDialog(
    wrong: Boolean,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val password = rememberTextFieldState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_password_title)) },
        text = {
            OutlinedSecureTextField(
                state = password,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.backup_password)) },
                supportingText = if (wrong) ({ Text(stringResource(R.string.backup_password_wrong)) }) else null,
                isError = wrong,
                shape = MaterialTheme.shapes.medium,
                colors = fieldColors(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(password.text.toString()) }, enabled = password.text.isNotEmpty()) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun MessageDialog(
    title: String,
    body: String?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = body?.let { { Text(it) } },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
    )
}

private val DialogSpacing = 12.dp
