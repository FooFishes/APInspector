package work.foofish.apinspector.custom.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import work.foofish.apinspector.R
import work.foofish.apinspector.custom.CurlImportResult
import work.foofish.apinspector.custom.CustomRequestId
import work.foofish.apinspector.custom.CustomRequestItem
import work.foofish.apinspector.custom.CustomRequestListActions
import work.foofish.apinspector.ui.component.MethodBadge
import work.foofish.apinspector.ui.theme.ScreenPadding
import work.foofish.apinspector.ui.theme.card
import work.foofish.apinspector.ui.theme.codeSmall

/** 自定义请求列表上进行中的对话框。 */
internal sealed interface CustomDialog {
    /** [failure]：上一次导入失败的原因。 */
    data class Import(
        val failure: CurlImportResult.Failed?,
    ) : CustomDialog

    /** 已导入为 [id]，但有参数被忽略；看过提示后再打开它。 */
    data class Ignored(
        val id: CustomRequestId,
        val arguments: ImmutableList<String>,
    ) : CustomDialog

    data class Rename(
        val item: CustomRequestItem,
    ) : CustomDialog

    data class Move(
        val item: CustomRequestItem,
    ) : CustomDialog

    data class Delete(
        val item: CustomRequestItem,
    ) : CustomDialog
}

/**
 * 自定义请求列表的操作（PRD 4.2）：新建、cURL 导入，以及每一项的重命名、移动与删除。
 * 列表可以独占一页（临时请求），也可以是接口列表顶部的一个分区，两处共用它。
 */
@Stable
class CustomRequestsHost internal constructor(
    internal val actions: CustomRequestListActions,
    private val scope: CoroutineScope,
    private val defaultName: String,
    private val onOpen: (CustomRequestId) -> Unit,
) {
    internal var dialog by mutableStateOf<CustomDialog?>(null)

    /** 新建一个空白请求并打开它。 */
    fun create() {
        scope.launch { onOpen(actions.create(defaultName)) }
    }

    fun requestImport() {
        dialog = CustomDialog.Import(failure = null)
    }

    internal fun open(id: CustomRequestId) {
        dialog = null
        onOpen(id)
    }

    /** 导入成功后打开新请求；有被忽略的参数时先提示（PRD 4.2）。 */
    internal fun import(command: String) {
        scope.launch {
            when (val result = actions.importCurl(command)) {
                is CurlImportResult.Failed -> {
                    dialog = CustomDialog.Import(result)
                }

                is CurlImportResult.Imported -> {
                    if (result.ignored.isEmpty()) {
                        dialog = null
                        onOpen(result.id)
                    } else {
                        dialog = CustomDialog.Ignored(result.id, result.ignored)
                    }
                }
            }
        }
    }
}

@Composable
fun rememberCustomRequestsHost(
    actions: CustomRequestListActions,
    onOpen: (CustomRequestId) -> Unit,
): CustomRequestsHost {
    val scope = rememberCoroutineScope()
    val defaultName = stringResource(R.string.custom_default_name)
    val currentOnOpen by rememberUpdatedState(onOpen)
    return remember(actions, scope, defaultName) { CustomRequestsHost(actions, scope, defaultName) { currentOnOpen(it) } }
}

/** "＋"按钮：新建请求，或从 cURL 导入。 */
@Composable
fun AddCustomRequestMenu(
    host: CustomRequestsHost,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.custom_add))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, shape = MaterialTheme.shapes.large) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.custom_new)) },
                onClick = {
                    expanded = false
                    host.create()
                },
                leadingIcon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.custom_import_curl)) },
                onClick = {
                    expanded = false
                    host.requestImport()
                },
                leadingIcon = { Icon(painterResource(R.drawable.ic_content_paste), contentDescription = null) },
            )
        }
    }
}

/**
 * 自定义请求的各行。
 *
 * @param movable 临时请求可以移动到某份 API 文档。
 */
fun LazyListScope.customRequestItems(
    requests: ImmutableList<CustomRequestItem>,
    selected: CustomRequestId?,
    movable: Boolean,
    host: CustomRequestsHost,
    onOpen: (CustomRequestId) -> Unit,
) {
    itemsIndexed(requests, key = { _, item -> "custom ${item.id.value}" }) { index, item ->
        CustomRequestRow(
            item = item,
            shapes = ListItemDefaults.segmentedShapes(index, requests.size),
            selected = item.id == selected,
            movable = movable,
            onClick = { onOpen(item.id) },
            onDialog = { host.dialog = it },
            modifier = Modifier.animateItem().padding(horizontal = ScreenPadding),
        )
    }
}

@Composable
private fun CustomRequestRow(
    item: CustomRequestItem,
    shapes: ListItemShapes,
    selected: Boolean,
    movable: Boolean,
    onClick: () -> Unit,
    onDialog: (CustomDialog) -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedListItem(
        selected = selected,
        onClick = onClick,
        shapes = shapes,
        modifier = modifier,
        leadingContent = { MethodBadge(item.method) },
        verticalAlignment = Alignment.CenterVertically,
        supportingContent = {
            Text(
                text = item.url.ifBlank { stringResource(R.string.custom_no_url) },
                style = MaterialTheme.typography.codeSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = { RowMenu(item, movable, onDialog) },
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.card),
        contentPadding = PaddingValues(horizontal = ItemPaddingHorizontal, vertical = ItemPaddingVertical),
    ) {
        Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RowMenu(
    item: CustomRequestItem,
    movable: Boolean,
    onDialog: (CustomDialog) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    fun open(dialog: CustomDialog) {
        expanded = false
        onDialog(dialog)
    }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.action_more))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, shape = MaterialTheme.shapes.large) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.custom_rename)) },
                onClick = { open(CustomDialog.Rename(item)) },
            )
            if (movable) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.custom_move)) },
                    onClick = { open(CustomDialog.Move(item)) },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_delete)) },
                onClick = { open(CustomDialog.Delete(item)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
                colors =
                    MenuDefaults.itemColors(
                        textColor = MaterialTheme.colorScheme.error,
                        leadingIconColor = MaterialTheme.colorScheme.error,
                    ),
            )
        }
    }
}

private val ItemPaddingHorizontal = 16.dp
private val ItemPaddingVertical = 4.dp
