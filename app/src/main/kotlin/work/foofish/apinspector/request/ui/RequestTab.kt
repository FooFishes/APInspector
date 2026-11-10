package work.foofish.apinspector.request.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import work.foofish.apinspector.ui.component.EmptyState
import work.foofish.apinspector.ui.component.LoadingState
import work.foofish.apinspector.ui.component.TextInputDialog
import work.foofish.apinspector.ui.text.label
import work.foofish.apinspector.ui.text.message

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
            item(key = HEADER_KEY) {
                RequestHeader(state, actions, onManageEnvironments, Modifier.padding(horizontal = EditorPadding, vertical = FieldSpacing))
            }
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

private fun LazyListScope.parameterSections(
    state: RequestUiState.Loaded,
    actions: RequestEditorActions,
) {
    state.groups.forEach { group ->
        item(
            key = "group/${group.location}",
        ) { SectionTitle(stringResource(group.location.label()), Modifier.padding(horizontal = EditorPadding)) }
        items(group.fields, key = { "parameter/${it.location}/${it.name}" }) { field ->
            ParameterFieldRow(
                field = field,
                onValueChange = { actions.setParameterValue(field.location, field.name, it) },
                onEnabledChange = { actions.setParameterEnabled(field.location, field.name, it) },
                modifier = Modifier.padding(end = EditorPadding, bottom = FieldSpacing),
            )
        }
    }
}

private fun LazyListScope.extraSection(
    extras: ImmutableList<ExtraParameter>,
    actions: RequestEditorActions,
) {
    item(key = EXTRAS_KEY) { SectionTitle(stringResource(R.string.request_extras), Modifier.padding(horizontal = EditorPadding)) }
    itemsIndexed(extras, key = { index, _ -> "extra/$index" }) { index, extra ->
        ExtraParameterRow(
            extra = extra,
            onChange = { actions.updateExtra(index, it) },
            onRemove = { actions.removeExtra(index) },
            modifier = Modifier.padding(end = EditorPadding, bottom = FieldSpacing),
        )
    }
    item(key = EXTRAS_ADD_KEY) {
        FlowRow(Modifier.padding(horizontal = FieldSpacing)) {
            ExtraLocation.entries.forEach { location ->
                AddButton(
                    text =
                        stringResource(
                            if (location ==
                                ExtraLocation.HEADER
                            ) {
                                R.string.request_add_header
                            } else {
                                R.string.request_add_query
                            },
                        ),
                    onClick = { actions.addExtra(ExtraParameter(location, name = "", value = "", enabled = true)) },
                )
            }
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
    item(key = BODY_KEY) {
        Column(Modifier.padding(horizontal = EditorPadding), verticalArrangement = Arrangement.spacedBy(FieldSpacing)) {
            SectionTitle(stringResource(R.string.doc_request_body))
            ContentTypeSelector(body, actions::selectContentType)
        }
    }
    val contentType = body.selected ?: return
    val padded = Modifier.padding(horizontal = EditorPadding, vertical = FieldSpacing)
    when (val content = body.content) {
        null -> {}

        is BodyContent.Json -> {
            item(key = BODY_MODE_KEY) {
                BodyModeSwitch(
                    content.mode,
                    visualEnabled = content.parse is JsonTemplateParse.Success,
                    onModeChange = actions::setBodyMode,
                    modifier = padded,
                )
            }
            val rows = content.rows
            if (rows == null) {
                item(key = BODY_EDITOR_KEY) {
                    JsonRawEditor(content, onTextChange = {
                        actions.setBody(BodyInput.Json(contentType, it))
                    }, onFormat = actions::formatJson, modifier = padded)
                }
            } else {
                items(rows, key = { "json/${it.key}" }) { row ->
                    JsonRowItem(row, variables, jsonActions, Modifier.padding(start = EditorPadding, end = EditorPadding))
                }
            }
        }

        is BodyContent.Form -> {
            item(key = BODY_EDITOR_KEY) { FormEditor(content, contentType, actions::setBody, actions::setBodyMode, padded) }
        }

        is BodyContent.Multipart -> {
            item(key = BODY_EDITOR_KEY) { MultipartEditor(content, contentType, actions::setBody, onPickFile, padded) }
        }

        is BodyContent.Binary -> {
            item(key = BODY_EDITOR_KEY) { FilePickerButton(content.file, onClick = { onPickFile(FileTarget.Body) }, modifier = padded) }
        }

        is BodyContent.Text -> {
            item(key = BODY_EDITOR_KEY) {
                RawTextField(content.text, onValueChange = { actions.setBody(BodyInput.Text(contentType, it)) }, modifier = padded)
            }
        }
    }
}

@Composable
private fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier.padding(top = SectionSpacing, bottom = FieldSpacing),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** 底部固定的发送按钮；请求进行中变为"取消"（PRD 6）。 */
@Composable
private fun SendBar(
    sending: Boolean,
    onSend: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(tonalElevation = SendBarElevation) {
        Column {
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = EditorPadding, vertical = FieldSpacing),
                horizontalArrangement = Arrangement.spacedBy(FieldSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (sending) {
                    LoadingIndicator(Modifier.size(SendIndicatorSize))
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_cancel)) }
                } else {
                    Button(onClick = onSend, modifier = Modifier.weight(1f), contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                        Icon(
                            painterResource(R.drawable.ic_send),
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Text(stringResource(R.string.request_send), modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
                    }
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
private const val EXTRAS_KEY = "extras"
private const val EXTRAS_ADD_KEY = "extras/add"
private const val BODY_KEY = "body"
private const val BODY_MODE_KEY = "body/mode"
private const val BODY_EDITOR_KEY = "body/editor"
private const val ANY_FILE = "*/*"
private val SectionSpacing = 16.dp
private val ListBottomPadding = 24.dp
private val SendBarElevation = 3.dp
private val SendIndicatorSize = 40.dp
