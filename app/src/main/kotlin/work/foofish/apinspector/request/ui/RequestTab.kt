package work.foofish.apinspector.request.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.ImmutableList
import work.foofish.apinspector.R
import work.foofish.apinspector.openapi.request.AssemblyProblem
import work.foofish.apinspector.openapi.request.BodyInput
import work.foofish.apinspector.openapi.request.ExtraLocation
import work.foofish.apinspector.openapi.request.ExtraParameter
import work.foofish.apinspector.openapi.request.JsonTemplateParse
import work.foofish.apinspector.request.BodyContent
import work.foofish.apinspector.request.BodyEditor
import work.foofish.apinspector.request.FileTarget
import work.foofish.apinspector.request.RequestEditorActions
import work.foofish.apinspector.request.RequestUiState
import work.foofish.apinspector.request.RequestViewModel
import work.foofish.apinspector.request.SendProblem
import work.foofish.apinspector.request.body.JsonPath
import work.foofish.apinspector.ui.component.CardRow
import work.foofish.apinspector.ui.component.EmptyState
import work.foofish.apinspector.ui.component.LoadingState
import work.foofish.apinspector.ui.component.SectionLabel
import work.foofish.apinspector.ui.component.TextInputDialog
import work.foofish.apinspector.ui.component.cardRows
import work.foofish.apinspector.ui.text.label
import work.foofish.apinspector.ui.text.message
import work.foofish.apinspector.ui.theme.ScreenPadding
import work.foofish.apinspector.ui.theme.screen

/**
 * "请求" Tab（PRD 3、4）。
 *
 * @param onSend 开始发送后切到"响应" Tab。
 */
@Composable
fun RequestTab(
    viewModel: RequestViewModel,
    onSend: () -> Unit,
    onManageEnvironments: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actions: RequestEditorActions = viewModel
    when (val current = state) {
        RequestUiState.Loading -> {
            LoadingState(modifier)
        }

        RequestUiState.Missing -> {
            EmptyState(
                painterResource(R.drawable.ic_error),
                stringResource(R.string.operations_spec_missing),
                body = null,
                modifier = modifier,
            )
        }

        is RequestUiState.Failed -> {
            EmptyState(
                painterResource(R.drawable.ic_error),
                stringResource(R.string.operations_load_failed_title),
                current.failure.message(),
                modifier,
            )
        }

        is RequestUiState.Loaded -> {
            val currentOnSent by rememberUpdatedState(onSend)
            LaunchedEffect(current.sent) {
                if (current.sent) {
                    viewModel.onSentShown()
                    currentOnSent()
                }
            }
            RequestEditorContent(current, actions, onManageEnvironments, modifier)
        }
    }
}

@Composable
private fun RequestEditorContent(
    state: RequestUiState.Loaded,
    actions: RequestEditorActions,
    onManageEnvironments: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var fileTarget by remember { mutableStateOf<FileTarget?>(null) }
    val pickFile =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val target = fileTarget
            if (uri != null && target != null) actions.pickFile(target, uri)
            fileTarget = null
        }
    var addMemberTo by remember { mutableStateOf<JsonPath?>(null) }
    val jsonActions =
        remember(actions) {
            JsonEditorActions(
                onToggle = actions::toggleJsonNode,
                onSet = actions::setJsonNode,
                onRemove = actions::removeJsonNode,
                onAddMember = actions::addJsonMember,
                onRequestMember = { addMemberTo = it },
                onAddItem = actions::addJsonItem,
                onSelectVariant = actions::selectJsonVariant,
            )
        }

    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = ListBottomPadding)) {
            headerItems(state, actions, onManageEnvironments)
            parameterSections(state, actions)
            extraSection(state.extras, actions)
            state.body?.let { body ->
                bodySection(body, state.variables, actions, jsonActions) { target ->
                    fileTarget = target
                    pickFile.launch(arrayOf(ANY_FILE))
                }
            }
        }
        SendBar(sending = state.sending, onSend = actions::send, onCancel = actions::cancel)
    }

    state.problems?.let { ProblemsDialog(it, onDismiss = actions::dismissProblems, onManageEnvironments = onManageEnvironments) }
    addMemberTo?.let { parent ->
        TextInputDialog(
            title = stringResource(R.string.request_add_field),
            label = stringResource(R.string.request_field_name),
            confirmText = stringResource(R.string.action_add),
            onConfirm = { name ->
                actions.addJsonMember(parent, name)
                addMemberTo = null
            },
            onDismiss = { addMemberTo = null },
        )
    }
}

/** 列表顶部：环境与预设，其下是请求预览。 */
private fun LazyListScope.headerItems(
    state: RequestUiState.Loaded,
    actions: RequestEditorActions,
    onManageEnvironments: () -> Unit,
) {
    item(key = HEADER_KEY) {
        RequestHeader(
            state,
            actions,
            onManageEnvironments,
            Modifier.padding(horizontal = ScreenPadding).padding(top = HeaderSpacing),
        )
    }
    item(key = PREVIEW_KEY) {
        RequestPreviewCard(
            preview = state.preview,
            onManageEnvironments = onManageEnvironments,
            modifier = Modifier.animateItem().padding(horizontal = ScreenPadding).padding(top = HeaderSpacing),
        )
    }
}

private fun LazyListScope.parameterSections(
    state: RequestUiState.Loaded,
    actions: RequestEditorActions,
) {
    state.groups.forEach { group ->
        sectionLabel("group/${group.location}") { stringResource(group.location.label()) }
        val reserveToggle = group.fields.any { !it.alwaysSent }
        cardRows(
            rows =
                group.fields.map { field ->
                    CardRow("parameter/${field.location}/${field.name}") {
                        ParameterFieldRow(
                            field = field,
                            onValueChange = { actions.setParameterValue(field.location, field.name, it) },
                            onEnabledChange = { actions.setParameterEnabled(field.location, field.name, it) },
                            reserveToggle = reserveToggle,
                        )
                    }
                },
            startInset = ControlInset,
        )
    }
}

private fun LazyListScope.extraSection(
    extras: ImmutableList<ExtraParameter>,
    actions: RequestEditorActions,
) {
    // 没有额外参数时不显示空卡片：添加按钮就在分区标题的右侧。
    item(key = EXTRAS_KEY) {
        Row(
            modifier = Modifier.animateItem().fillMaxWidth().padding(start = ScreenPadding, end = ExtrasEndPadding),
            verticalAlignment = Alignment.Bottom,
        ) {
            SectionLabel(stringResource(R.string.request_extras), Modifier.weight(1f))
            AddExtraButtons(onAdd = actions::addExtra)
        }
    }
    val rows =
        extras.mapIndexed { index, extra ->
            CardRow("extra/$index") {
                ExtraParameterRow(extra = extra, onChange = { actions.updateExtra(index, it) }, onRemove = { actions.removeExtra(index) })
            }
        }
    cardRows(rows, startInset = ControlInset)
}

@Composable
private fun AddExtraButtons(onAdd: (ExtraParameter) -> Unit) {
    Row {
        ExtraLocation.entries.forEach { location ->
            AddButton(
                text = stringResource(if (location == ExtraLocation.HEADER) R.string.request_add_header else R.string.request_add_query),
                onClick = { onAdd(ExtraParameter(location, name = "", value = "", enabled = true)) },
            )
        }
    }
}

private fun LazyListScope.bodySection(
    body: BodyEditor,
    variables: ImmutableList<String>,
    actions: RequestEditorActions,
    jsonActions: JsonEditorActions,
    onPickFile: (FileTarget) -> Unit,
) {
    sectionLabel(BODY_KEY) { stringResource(R.string.doc_request_body) }
    val rows =
        buildList {
            if (body.contentTypes.size > 1 || body.optional) {
                add(CardRow(BODY_TYPE_KEY) { ContentTypeSelector(body, actions::selectContentType) })
            }
            body.selected?.let { addAll(bodyContentRows(body, it, variables, actions, jsonActions, onPickFile)) }
        }
    cardRows(rows)
}

/** 所选 content type 的编辑器；可视化 JSON 的每个节点各占一行，其余编辑器各为一行。 */
private fun bodyContentRows(
    body: BodyEditor,
    contentType: String,
    variables: ImmutableList<String>,
    actions: RequestEditorActions,
    jsonActions: JsonEditorActions,
    onPickFile: (FileTarget) -> Unit,
): List<CardRow> =
    when (val content = body.content) {
        null -> {
            emptyList()
        }

        is BodyContent.Json -> {
            val mode =
                CardRow(BODY_MODE_KEY) {
                    BodyModeSwitch(
                        content.mode,
                        visualEnabled = content.parse is JsonTemplateParse.Success,
                        onModeChange = actions::setBodyMode,
                    )
                }
            val editor =
                content.rows?.map { row -> CardRow("json/${row.key}", flush = true) { JsonRowItem(row, variables, jsonActions) } }
                    ?: listOf(
                        CardRow(BODY_EDITOR_KEY) {
                            JsonRawEditor(
                                content,
                                onTextChange = { actions.setBody(BodyInput.Json(contentType, it)) },
                                onFormat = actions::formatJson,
                            )
                        },
                    )
            listOf(mode) + editor
        }

        is BodyContent.Form -> {
            listOf(CardRow(BODY_EDITOR_KEY) { FormEditor(content, contentType, actions::setBody, actions::setBodyMode) })
        }

        is BodyContent.Multipart -> {
            listOf(CardRow(BODY_EDITOR_KEY) { MultipartEditor(content, contentType, actions::setBody, onPickFile) })
        }

        is BodyContent.Binary -> {
            listOf(CardRow(BODY_EDITOR_KEY) { FilePickerButton(content.file, onClick = { onPickFile(FileTarget.Body) }) })
        }

        is BodyContent.Text -> {
            listOf(
                CardRow(BODY_EDITOR_KEY) {
                    RawTextField(content.text, onValueChange = { actions.setBody(BodyInput.Text(contentType, it)) })
                },
            )
        }
    }

private fun LazyListScope.sectionLabel(
    key: String,
    text: @Composable () -> String,
) {
    item(key = key) { SectionLabel(text(), Modifier.animateItem().padding(horizontal = ScreenPadding)) }
}

/**
 * 底部固定的发送按钮；请求进行中变为"取消"（PRD 6）。
 *
 * 发送是这一页唯一的主操作：使用中号按钮（按下时圆角收紧），始终停靠在拇指可及的底部。
 */
@Composable
private fun SendBar(
    sending: Boolean,
    onSend: () -> Unit,
    onCancel: () -> Unit,
) {
    val height = ButtonDefaults.MediumContainerHeight
    val buttonModifier = Modifier.fillMaxWidth().heightIn(min = height)
    Surface(color = MaterialTheme.colorScheme.screen) {
        Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = ScreenPadding, vertical = SendBarPadding)) {
            if (sending) {
                FilledTonalButton(
                    onClick = onCancel,
                    shapes = ButtonDefaults.shapesFor(height),
                    modifier = buttonModifier,
                    contentPadding = ButtonDefaults.contentPaddingFor(height, hasStartIcon = true),
                ) {
                    LoadingIndicator(Modifier.size(SendIndicatorSize))
                    Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(height)))
                    Text(stringResource(R.string.action_cancel), style = ButtonDefaults.textStyleFor(height))
                }
            } else {
                Button(
                    onClick = onSend,
                    shapes = ButtonDefaults.shapesFor(height),
                    modifier = buttonModifier,
                    contentPadding = ButtonDefaults.contentPaddingFor(height, hasStartIcon = true),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_send),
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)),
                    )
                    Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(height)))
                    Text(stringResource(R.string.request_send), style = ButtonDefaults.textStyleFor(height))
                }
            }
        }
    }
}

/** 阻止发送的原因（PRD 5：列出缺失的变量；baseUrl 不完整时提供"去编辑环境"）。 */
@Composable
private fun ProblemsDialog(
    problems: ImmutableList<SendProblem>,
    onDismiss: () -> Unit,
    onManageEnvironments: () -> Unit,
) {
    val environmentRelated =
        problems.any { problem ->
            problem is SendProblem.NoEnvironment || (problem is SendProblem.Assembly && problem.problem !is AssemblyProblem.FileNotSelected)
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.ic_warning), contentDescription = null) },
        title = { Text(stringResource(R.string.request_blocked_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(FieldSpacing)) {
                problems.forEach { Text(it.message()) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
        dismissButton =
            if (environmentRelated) {
                {
                    TextButton(
                        onClick = {
                            onDismiss()
                            onManageEnvironments()
                        },
                    ) { Text(stringResource(R.string.request_edit_environment)) }
                }
            } else {
                null
            },
    )
}

private const val HEADER_KEY = "header"
private const val PREVIEW_KEY = "preview"
private const val EXTRAS_KEY = "extras"
private const val BODY_KEY = "body"
private const val BODY_TYPE_KEY = "body/type"
private const val BODY_MODE_KEY = "body/mode"
private const val BODY_EDITOR_KEY = "body/editor"
private const val ANY_FILE = "*/*"
private val HeaderSpacing = 12.dp
private val ExtrasEndPadding = 8.dp
private val ListBottomPadding = 24.dp
private val SendBarPadding = 12.dp
private val SendIndicatorSize = 28.dp

/** 左侧自带勾选框或按钮的行：控件自身已有留白，行内容更贴近卡片左缘。 */
private val ControlInset = 4.dp
