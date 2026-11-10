package work.foofish.apinspector.environment.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.ImmutableList
import work.foofish.apinspector.R
import work.foofish.apinspector.environment.EnvironmentId
import work.foofish.apinspector.request.EnvironmentOption
import work.foofish.apinspector.ui.component.BackButton
import work.foofish.apinspector.ui.component.ConfirmDialog
import work.foofish.apinspector.ui.component.EmptyState
import work.foofish.apinspector.ui.component.LoadingState
import work.foofish.apinspector.ui.component.SegmentGap
import work.foofish.apinspector.ui.component.TextInputDialog
import work.foofish.apinspector.ui.component.segmentedShape

/** 环境列表（PRD 5"管理环境"）。 */
@Composable
fun EnvironmentsScreen(
    viewModel: EnvironmentsViewModel,
    selected: EnvironmentId?,
    onOpen: (EnvironmentId) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actions: EnvironmentListActions = viewModel
    var dialog by remember { mutableStateOf<EnvironmentDialog?>(null) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.environment_manage)) },
                navigationIcon = {
                    BackButton(onClick = onBack)
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { dialog = EnvironmentDialog.Create },
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                text = { Text(stringResource(R.string.environment_new)) },
            )
        },
    ) { innerPadding ->
        val environments = state.environments
        when {
            environments == null -> {
                LoadingState(Modifier.padding(innerPadding))
            }

            environments.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    EmptyState(painterResource(R.drawable.ic_public), stringResource(R.string.environment_none), body = null)
                }
            }

            else -> {
                EnvironmentList(
                    environments = environments,
                    current = state.current,
                    selected = selected,
                    contentPadding = innerPadding,
                    onOpen = onOpen,
                    onSelect = viewModel::select,
                    onDialog = { dialog = it },
                )
            }
        }
    }
    EnvironmentDialogs(dialog, actions, onCreate = onOpen) { dialog = null }
}

@Composable
private fun EnvironmentList(
    environments: ImmutableList<EnvironmentOption>,
    current: EnvironmentId?,
    selected: EnvironmentId?,
    contentPadding: PaddingValues,
    onOpen: (EnvironmentId) -> Unit,
    onSelect: (EnvironmentId) -> Unit,
    onDialog: (EnvironmentDialog) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding =
            PaddingValues(
                top = contentPadding.calculateTopPadding(),
                bottom = ListBottomPadding,
                start = ScreenPadding,
                end = ScreenPadding,
            ),
        verticalArrangement = Arrangement.spacedBy(SegmentGap),
    ) {
        itemsIndexed(environments, key = { _, environment -> environment.id.value }) { index, environment ->
            EnvironmentRow(
                environment = environment,
                current = environment.id == current,
                selected = environment.id == selected,
                canDelete = environments.size > 1,
                onClick = { onOpen(environment.id) },
                onSelect = { onSelect(environment.id) },
                onDialog = onDialog,
                modifier = Modifier.clip(segmentedShape(index, environments.size)),
            )
        }
    }
}

@Composable
private fun EnvironmentRow(
    environment: EnvironmentOption,
    current: Boolean,
    selected: Boolean,
    canDelete: Boolean,
    onClick: () -> Unit,
    onSelect: () -> Unit,
    onDialog: (EnvironmentDialog) -> Unit,
    modifier: Modifier = Modifier,
) {
    val status =
        listOfNotNull(
            stringResource(R.string.environment_current).takeIf { current },
            stringResource(R.string.environment_incomplete).takeIf { environment.incomplete },
        ).joinToString(stringResource(R.string.spec_info_separator))
    ListItem(
        onClick = onClick,
        modifier = modifier,
        leadingContent = {
            Icon(
                painterResource(if (environment.insecure) R.drawable.ic_warning else R.drawable.ic_public),
                contentDescription = null,
                tint = if (environment.insecure) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        supportingContent =
            status.takeIf { it.isNotEmpty() }?.let {
                {
                    Text(it, color = if (environment.incomplete) MaterialTheme.colorScheme.error else Color.Unspecified)
                }
            },
        trailingContent = { EnvironmentMenu(environment, current, canDelete, onSelect, onDialog) },
        colors =
            ListItemDefaults.colors(
                containerColor = with(MaterialTheme.colorScheme) { if (selected) secondaryContainer else surfaceContainerLow },
            ),
    ) {
        Text(environment.name)
    }
}

@Composable
private fun EnvironmentMenu(
    environment: EnvironmentOption,
    current: Boolean,
    canDelete: Boolean,
    onSelect: () -> Unit,
    onDialog: (EnvironmentDialog) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.action_more))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.environment_use)) },
                enabled = !current,
                onClick = {
                    expanded = false
                    onSelect()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.environment_rename)) },
                onClick = {
                    expanded = false
                    onDialog(EnvironmentDialog.Rename(environment))
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.environment_duplicate)) },
                onClick = {
                    expanded = false
                    onDialog(EnvironmentDialog.Duplicate(environment))
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_delete)) },
                enabled = canDelete,
                onClick = {
                    expanded = false
                    onDialog(EnvironmentDialog.Delete(environment))
                },
            )
        }
    }
}

private sealed interface EnvironmentDialog {
    data object Create : EnvironmentDialog

    data class Rename(
        val environment: EnvironmentOption,
    ) : EnvironmentDialog

    data class Duplicate(
        val environment: EnvironmentOption,
    ) : EnvironmentDialog

    data class Delete(
        val environment: EnvironmentOption,
    ) : EnvironmentDialog
}

@Composable
private fun EnvironmentDialogs(
    dialog: EnvironmentDialog?,
    actions: EnvironmentListActions,
    onCreate: (EnvironmentId) -> Unit,
    onDismiss: () -> Unit,
) {
    val nameLabel = stringResource(R.string.environment_name)
    when (dialog) {
        null -> {}

        EnvironmentDialog.Create -> {
            TextInputDialog(
                title = stringResource(R.string.environment_new),
                label = nameLabel,
                confirmText = stringResource(R.string.action_add),
                onConfirm = {
                    actions.create(it, onCreate)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }

        is EnvironmentDialog.Rename -> {
            TextInputDialog(
                title = stringResource(R.string.environment_rename),
                label = nameLabel,
                confirmText = stringResource(R.string.action_save),
                onConfirm = {
                    actions.rename(dialog.environment.id, it)
                    onDismiss()
                },
                onDismiss = onDismiss,
                initial = dialog.environment.name,
            )
        }

        is EnvironmentDialog.Duplicate -> {
            TextInputDialog(
                title = stringResource(R.string.environment_duplicate),
                label = nameLabel,
                confirmText = stringResource(R.string.action_add),
                onConfirm = {
                    actions.duplicate(dialog.environment.id, it)
                    onDismiss()
                },
                onDismiss = onDismiss,
                initial = stringResource(R.string.environment_copy_name, dialog.environment.name),
            )
        }

        is EnvironmentDialog.Delete -> {
            ConfirmDialog(
                title = stringResource(R.string.environment_delete_title, dialog.environment.name),
                body = stringResource(R.string.environment_delete_body),
                confirmText = stringResource(R.string.action_delete),
                onConfirm = {
                    actions.delete(dialog.environment.id)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }
    }
}

/** 双栏布局下右侧未选择环境时的占位。 */
@Composable
fun NoEnvironmentSelectedPane(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(painterResource(R.drawable.ic_public), stringResource(R.string.environment_no_selection), body = null)
    }
}

private val ScreenPadding = 16.dp
private val ListBottomPadding = 96.dp
