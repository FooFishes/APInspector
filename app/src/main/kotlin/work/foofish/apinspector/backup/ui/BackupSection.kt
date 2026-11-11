package work.foofish.apinspector.backup.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import work.foofish.apinspector.R
import work.foofish.apinspector.backup.BackupStatus
import work.foofish.apinspector.backup.BackupViewModel
import work.foofish.apinspector.backup.ExportOptions
import work.foofish.apinspector.ui.component.SectionCard
import work.foofish.apinspector.ui.component.SectionLabel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 设置页里的"备份"（PRD 8）：导出到文件（可选包含请求记录、可选密码），或从文件导入并覆盖全部数据。
 *
 * @param onImportComplete 导入完成并确认后调用：原来打开的页面可能已不存在，由调用方回到首页。
 */
@Composable
fun BackupSection(
    viewModel: BackupViewModel,
    onImportComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    var choosingOptions by remember { mutableStateOf(false) }
    var options by remember { mutableStateOf<ExportOptions?>(null) }
    val create =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE)) { uri ->
            val chosen = options
            if (uri != null && chosen != null) viewModel.export(uri, chosen.includeHistory, chosen.password)
            options = null
        }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::open) }
    val working = status == BackupStatus.Working

    Column(modifier) {
        SectionLabel(stringResource(R.string.backup_title))
        SectionCard {
            BackupRow(stringResource(R.string.backup_export), stringResource(R.string.backup_export_hint)) {
                FilledTonalButton(
                    onClick = { choosingOptions = true },
                    enabled = !working,
                ) { Text(stringResource(R.string.backup_export_action)) }
            }
            BackupRow(
                title = stringResource(R.string.backup_import),
                description = stringResource(R.string.backup_import_hint),
                modifier = Modifier.padding(top = RowSpacing),
            ) {
                OutlinedButton(
                    onClick = { open.launch(arrayOf(ANY_FILE)) },
                    enabled = !working,
                ) { Text(stringResource(R.string.action_import)) }
            }
            if (working) LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(top = RowSpacing))
        }
    }
    if (choosingOptions) {
        ExportDialog(
            onConfirm = {
                choosingOptions = false
                options = it
                create.launch(backupFileName())
            },
            onDismiss = { choosingOptions = false },
        )
    }
    BackupStatusDialogs(
        status = status,
        onSubmitPassword = viewModel::submitPassword,
        onConfirmImport = viewModel::confirmImport,
        onDismiss = viewModel::dismiss,
        onImportComplete = {
            viewModel.dismiss()
            onImportComplete()
        },
    )
}

@Composable
private fun BackupRow(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    control: @Composable () -> Unit,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(RowSpacing), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        control()
    }
}

private fun backupFileName(): String = FILE_PREFIX + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + FILE_SUFFIX

private const val BACKUP_MIME_TYPE = "application/octet-stream"
private const val ANY_FILE = "*/*"
private const val FILE_PREFIX = "apinspector-"
private const val FILE_SUFFIX = ".apibak"
private val RowSpacing = 16.dp
