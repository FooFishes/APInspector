package work.foofish.apinspector.request.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ChipColors
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import kotlinx.collections.immutable.ImmutableList
import work.foofish.apinspector.R
import work.foofish.apinspector.environment.EnvironmentId
import work.foofish.apinspector.request.EnvironmentOption
import work.foofish.apinspector.request.PresetId
import work.foofish.apinspector.request.PresetOption
import work.foofish.apinspector.request.PresetState
import work.foofish.apinspector.request.RequestHeaderActions
import work.foofish.apinspector.request.RequestUiState
import work.foofish.apinspector.ui.component.ConfirmDialog
import work.foofish.apinspector.ui.component.TextInputDialog
import work.foofish.apinspector.ui.theme.card

/** "请求" Tab 顶部：环境与预设选择、预设操作（PRD 4.1、5）。 */
@Composable
internal fun RequestHeader(
    state: RequestUiState.Loaded,
    actions: RequestHeaderActions,
    onManageEnvironments: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dialog by remember { mutableStateOf<PresetDialog?>(null) }
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(FieldSpacing),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        EnvironmentSelector(state.environments, state.environment, actions::selectEnvironment, onManageEnvironments)
        // 预设绑定接口（PRD 4.1）：自定义请求没有预设。
        if (state.custom == null) {
            PresetSelector(
                state = state.presets,
                onLoad = { id ->
                    if (state.presets.current != null &&
                        state.presets.modified
                    ) {
                        dialog = PresetDialog.ConfirmLoad(id)
                    } else {
                        actions.loadPreset(id)
                    }
                },
                onDialog = { dialog = it },
            )
        }
    }
    PresetDialogs(dialog, state.presets, actions) { dialog = null }
}

/** 环境选择（PRD 5）：跳过证书校验的环境以警示色标识，baseUrl 不完整的环境附说明。 */
@Composable
internal fun EnvironmentSelector(
    environments: ImmutableList<EnvironmentOption>,
    current: EnvironmentOption?,
    onSelect: (EnvironmentId) -> Unit,
    onManage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        EnvironmentChip(current, onClick = { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, shape = MaterialTheme.shapes.large) {
            environments.forEach { environment ->
                EnvironmentMenuItem(
                    environment = environment,
                    current = environment.id == current?.id,
                    onClick = {
                        expanded = false
                        onSelect(environment.id)
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.environment_manage)) },
                onClick = {
                    expanded = false
                    onManage()
                },
                leadingIcon = { Icon(painterResource(R.drawable.ic_settings), contentDescription = null) },
            )
        }
    }
}

@Composable
private fun EnvironmentChip(
    current: EnvironmentOption?,
    onClick: () -> Unit,
) {
    val insecure = current?.insecure == true
    AssistChip(
        onClick = onClick,
        label = { Text(current?.name ?: stringResource(R.string.environment_none)) },
        leadingIcon = {
            Icon(
                painterResource(if (insecure) R.drawable.ic_warning else R.drawable.ic_public),
                contentDescription = null,
                modifier = Modifier.size(AssistChipDefaults.IconSize),
            )
        },
        trailingIcon = {
            Icon(
                painterResource(R.drawable.ic_arrow_drop_down),
                contentDescription = null,
                modifier = Modifier.size(AssistChipDefaults.IconSize),
            )
        },
        shape = CircleShape,
        colors =
            if (insecure) {
                tonalChipColors(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
            } else {
                tonalChipColors(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
            },
        border = null,
    )
}

@Composable
private fun EnvironmentMenuItem(
    environment: EnvironmentOption,
    current: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(environment.name) },
        onClick = onClick,
        leadingIcon = if (current) ({ Icon(painterResource(R.drawable.ic_check), contentDescription = null) }) else null,
        trailingIcon =
            when {
                environment.insecure -> (
                    {
                        Icon(painterResource(R.drawable.ic_warning), contentDescription = stringResource(R.string.environment_insecure))
                    }
                )

                environment.incomplete -> (
                    {
                        Text(
                            stringResource(R.string.environment_incomplete),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                )

                else -> null
            },
    )
}

/** 填充色的胶囊：页面底色上不描边，靠色调与周围区分。 */
@Composable
private fun tonalChipColors(
    container: Color,
    content: Color,
): ChipColors =
    AssistChipDefaults.assistChipColors(
        containerColor = container,
        labelColor = content,
        leadingIconContentColor = content,
        trailingIconContentColor = content,
    )

/**
 * 预设（PRD 4.1）：显示当前参数来自哪个预设、是否已修改；
 * 点开后上半部分选择要加载的预设，下半部分是对当前参数与预设的操作。
 */
@Composable
private fun PresetSelector(
    state: PresetState,
    onLoad: (PresetId) -> Unit,
    onDialog: (PresetDialog) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        val name = state.current?.name ?: stringResource(R.string.preset_none)
        AssistChip(
            onClick = { expanded = true },
            label = { Text(if (state.modified) stringResource(R.string.preset_modified, name) else name) },
            leadingIcon = {
                Icon(
                    painterResource(R.drawable.ic_bookmark),
                    contentDescription = null,
                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                )
            },
            trailingIcon = {
                Icon(
                    painterResource(R.drawable.ic_arrow_drop_down),
                    contentDescription = null,
                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                )
            },
            shape = CircleShape,
            colors = tonalChipColors(MaterialTheme.colorScheme.card, MaterialTheme.colorScheme.onSurface),
            border = null,
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, shape = MaterialTheme.shapes.large) {
            state.presets.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(preset.name) },
                    onClick = {
                        expanded = false
                        onLoad(preset.id)
                    },
                    leadingIcon =
                        if (preset.id ==
                            state.current?.id
                        ) {
                            ({ Icon(painterResource(R.drawable.ic_check), contentDescription = null) })
                        } else {
                            null
                        },
                )
            }
            if (state.presets.isNotEmpty()) HorizontalDivider()
            PresetActions(state) {
                expanded = false
                onDialog(it)
            }
        }
    }
}

/** 对当前参数与当前预设的操作；没有当前预设时，只针对预设的操作不显示。 */
@Composable
private fun ColumnScope.PresetActions(
    state: PresetState,
    onDialog: (PresetDialog) -> Unit,
) {
    val current = state.current
    if (current != null && state.modified) {
        PresetAction(R.drawable.ic_check, R.string.preset_save) { onDialog(PresetDialog.ConfirmOverwrite(current)) }
    }
    PresetAction(R.drawable.ic_add, R.string.preset_save_as) { onDialog(PresetDialog.SaveAs) }
    if (current != null) {
        PresetAction(R.drawable.ic_format_align_left, R.string.preset_rename) { onDialog(PresetDialog.Rename(current)) }
        PresetAction(R.drawable.ic_delete, R.string.preset_delete) { onDialog(PresetDialog.Delete(current)) }
    }
    HorizontalDivider()
    PresetAction(R.drawable.ic_undo, R.string.preset_reset) { onDialog(PresetDialog.ConfirmReset) }
}

@Composable
private fun PresetAction(
    icon: Int,
    label: Int,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        onClick = onClick,
        leadingIcon = { Icon(painterResource(icon), contentDescription = null) },
    )
}

private sealed interface PresetDialog {
    /** 只需确认的操作。 */
    sealed interface Confirm : PresetDialog

    data class ConfirmLoad(
        val id: PresetId,
    ) : Confirm

    data class ConfirmOverwrite(
        val preset: PresetOption,
    ) : Confirm

    data object SaveAs : PresetDialog

    data class Rename(
        val preset: PresetOption,
    ) : PresetDialog

    data class Delete(
        val preset: PresetOption,
    ) : Confirm

    data object ConfirmReset : Confirm
}

@Composable
private fun PresetDialogs(
    dialog: PresetDialog?,
    state: PresetState,
    actions: RequestHeaderActions,
    onDismiss: () -> Unit,
) {
    when (dialog) {
        null -> {}

        PresetDialog.SaveAs -> {
            PresetNameDialog(R.string.preset_save_as, initial = "", except = null, state, onDismiss) { actions.saveAsPreset(it) }
        }

        is PresetDialog.Rename -> {
            PresetNameDialog(R.string.preset_rename, dialog.preset.name, dialog.preset.id, state, onDismiss) {
                actions.renamePreset(dialog.preset.id, it)
            }
        }

        is PresetDialog.Confirm -> {
            PresetConfirmDialog(dialog, actions, onDismiss)
        }
    }
}

/** 输入预设名称；与 [except] 以外的预设重名时不能确认。 */
@Composable
private fun PresetNameDialog(
    title: Int,
    initial: String,
    except: PresetId?,
    state: PresetState,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val taken = stringResource(R.string.preset_name_taken)
    TextInputDialog(
        title = stringResource(title),
        label = stringResource(R.string.preset_name),
        confirmText = stringResource(R.string.action_save),
        onConfirm = {
            onConfirm(it)
            onDismiss()
        },
        onDismiss = onDismiss,
        initial = initial,
        validate = { name -> taken.takeIf { state.presets.any { it.name == name && it.id != except } } },
    )
}

@Composable
private fun PresetConfirmDialog(
    dialog: PresetDialog.Confirm,
    actions: RequestHeaderActions,
    onDismiss: () -> Unit,
) {
    val confirmation =
        when (dialog) {
            is PresetDialog.ConfirmLoad -> {
                Confirmation(stringResource(R.string.preset_load_title), stringResource(R.string.preset_load_body), R.string.preset_load) {
                    actions.loadPreset(dialog.id)
                }
            }

            is PresetDialog.ConfirmOverwrite -> {
                Confirmation(
                    stringResource(R.string.preset_save_title, dialog.preset.name),
                    body = null,
                    R.string.preset_save,
                    action = actions::saveToPreset,
                )
            }

            is PresetDialog.Delete -> {
                Confirmation(
                    stringResource(R.string.preset_delete_title, dialog.preset.name),
                    body = null,
                    R.string.action_delete,
                    destructive = true,
                ) {
                    actions.deletePreset(dialog.preset.id)
                }
            }

            PresetDialog.ConfirmReset -> {
                Confirmation(
                    stringResource(R.string.preset_reset_title),
                    stringResource(R.string.preset_reset_body),
                    R.string.preset_reset_confirm,
                    action = actions::resetToDefaults,
                )
            }
        }
    ConfirmDialog(
        title = confirmation.title,
        body = confirmation.body,
        confirmText = stringResource(confirmation.confirmText),
        onConfirm = {
            confirmation.action()
            onDismiss()
        },
        onDismiss = onDismiss,
        destructive = confirmation.destructive,
    )
}

private class Confirmation(
    val title: String,
    val body: String?,
    val confirmText: Int,
    val destructive: Boolean = false,
    val action: () -> Unit,
)
