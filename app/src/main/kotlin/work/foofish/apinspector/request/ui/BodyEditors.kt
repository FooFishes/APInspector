package work.foofish.apinspector.request.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import work.foofish.apinspector.R
import work.foofish.apinspector.openapi.request.BodyInput
import work.foofish.apinspector.openapi.request.Field
import work.foofish.apinspector.openapi.request.FileRef
import work.foofish.apinspector.openapi.request.FormText
import work.foofish.apinspector.openapi.request.JsonSyntaxError
import work.foofish.apinspector.openapi.request.JsonTemplateParse
import work.foofish.apinspector.openapi.request.MultipartPart
import work.foofish.apinspector.openapi.request.PartValue
import work.foofish.apinspector.request.BodyContent
import work.foofish.apinspector.request.BodyEditor
import work.foofish.apinspector.request.BodyMode
import work.foofish.apinspector.request.FileTarget
import work.foofish.apinspector.request.body.FieldSchema
import work.foofish.apinspector.ui.component.SegmentedLabel
import work.foofish.apinspector.ui.component.rememberSyncedTextFieldState
import work.foofish.apinspector.ui.text.formatFileSize
import work.foofish.apinspector.ui.text.label
import work.foofish.apinspector.ui.theme.codeMedium

/** Body 的 content type 选择（PRD 4.3）：多个时切换；非必填时另有"无 Body"。 */
@Composable
internal fun ContentTypeSelector(
    editor: BodyEditor,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (editor.contentTypes.size == 1 && !editor.optional) return
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(FieldSpacing)) {
        if (editor.optional) {
            FilterChip(
                selected = editor.selected == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.request_no_body)) },
            )
        }
        editor.contentTypes.forEach { type ->
            FilterChip(
                selected = editor.selected == type,
                onClick = { onSelect(type) },
                label = { Text(type, style = MaterialTheme.typography.codeMedium) },
            )
        }
    }
}

/** "可视化 | 原文"切换；[visualEnabled] 为 `false` 时（原文有语法错误）不能切到可视化。 */
@Composable
internal fun BodyModeSwitch(
    mode: BodyMode,
    visualEnabled: Boolean,
    onModeChange: (BodyMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        BodyMode.entries.forEachIndexed { index, entry ->
            SegmentedButton(
                selected = mode == entry,
                onClick = { onModeChange(entry) },
                shape = SegmentedButtonDefaults.itemShape(index, BodyMode.entries.size),
                enabled = entry == BodyMode.RAW || visualEnabled,
                label = { SegmentedLabel(stringResource(entry.label())) },
            )
        }
    }
}

/** JSON 原文：等宽文本编辑器，下方显示语法错误（只提示，不阻止发送）。 */
@Composable
internal fun JsonRawEditor(
    content: BodyContent.Json,
    onTextChange: (String) -> Unit,
    onFormat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(FieldSpacing)) {
        val failure = content.parse as? JsonTemplateParse.Failure
        RawTextField(
            value = content.text,
            onValueChange = onTextChange,
            isError = failure != null && failure.error != JsonSyntaxError.EMPTY,
            supporting =
                failure
                    ?.takeIf {
                        it.error != JsonSyntaxError.EMPTY
                    }?.let { stringResource(R.string.request_json_error, it.line, it.column, stringResource(it.error.label())) },
        )
        TextButton(onClick = onFormat, enabled = content.parse is JsonTemplateParse.Success) {
            Icon(painterResource(R.drawable.ic_format_align_left), contentDescription = null, modifier = Modifier.size(SmallIcon))
            Text(stringResource(R.string.request_json_format), modifier = Modifier.padding(start = FieldSpacing))
        }
    }
}

@Composable
internal fun RawTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supporting: String? = null,
) {
    val state = rememberSyncedTextFieldState(value, onValueChange)
    OutlinedTextField(
        state = state,
        modifier = modifier.fillMaxWidth().heightIn(min = RawEditorMinHeight),
        textStyle = MaterialTheme.typography.codeMedium,
        lineLimits = TextFieldLineLimits.MultiLine(),
        isError = isError,
        supportingText = supporting?.let { { Text(it) } },
    )
}

/** urlencoded 表单（PRD 4.3）："可视化"为键值表单，"原文"为 `a=1&b=2`。 */
@Composable
internal fun FormEditor(
    content: BodyContent.Form,
    contentType: String,
    onChange: (BodyInput) -> Unit,
    onModeChange: (BodyMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(FieldSpacing)) {
        BodyModeSwitch(content.mode, visualEnabled = true, onModeChange = onModeChange)
        when (content.mode) {
            BodyMode.RAW -> {
                RawTextField(value = content.text, onValueChange = { onChange(BodyInput.Form(contentType, FormText.parse(it))) })
            }

            BodyMode.VISUAL -> {
                content.fields.forEachIndexed { index, field ->
                    KeyValueRow(
                        name = field.name,
                        value = field.value,
                        onNameChange = { name ->
                            onChange(BodyInput.Form(contentType, content.fields.replace(index, field.copy(name = name))))
                        },
                        onValueChange = { value ->
                            onChange(BodyInput.Form(contentType, content.fields.replace(index, field.copy(value = value))))
                        },
                        onRemove = { onChange(BodyInput.Form(contentType, content.fields.filterIndexed { i, _ -> i != index })) },
                    )
                }
                MissingFields(content.missing, content.schemas) { name ->
                    onChange(
                        BodyInput.Form(
                            contentType,
                            content.fields + Field(name, ""),
                        ),
                    )
                }
                AddButton(
                    stringResource(R.string.request_add_field),
                    onClick = { onChange(BodyInput.Form(contentType, content.fields + Field("", ""))) },
                )
            }
        }
    }
}

/** multipart 表单（PRD 4.3）：字段可为文本或本机文件。 */
@Composable
internal fun MultipartEditor(
    content: BodyContent.Multipart,
    contentType: String,
    onChange: (BodyInput) -> Unit,
    onPickFile: (FileTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    fun update(parts: List<MultipartPart>) = onChange(BodyInput.Multipart(contentType, parts))
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(FieldSpacing)) {
        content.parts.forEachIndexed { index, part ->
            Column(verticalArrangement = Arrangement.spacedBy(FieldSpacing)) {
                Row(horizontalArrangement = Arrangement.spacedBy(FieldSpacing), verticalAlignment = Alignment.CenterVertically) {
                    ValueField(
                        value = part.name,
                        onValueChange = { name -> update(content.parts.replace(index, part.copy(name = name))) },
                        choices = persistentListOf(),
                        label = stringResource(R.string.request_name),
                        modifier = Modifier.weight(1f),
                    )
                    PartTypeSwitch(part.value is PartValue.File) { file ->
                        update(content.parts.replace(index, part.copy(value = if (file) PartValue.File(null) else PartValue.Text(""))))
                    }
                    RemoveButton({ update(content.parts.filterIndexed { i, _ -> i != index }) })
                }
                when (val value = part.value) {
                    is PartValue.Text -> {
                        ValueField(
                            value = value.text,
                            onValueChange = { text -> update(content.parts.replace(index, part.copy(value = PartValue.Text(text)))) },
                            choices = content.schemas[part.name]?.choices ?: persistentListOf(),
                            label = stringResource(R.string.request_value),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    is PartValue.File -> {
                        FilePickerButton(value.file, onClick = { onPickFile(FileTarget.Part(index)) })
                    }
                }
            }
        }
        MissingFields(content.missing, content.schemas) { name ->
            val binary = content.schemas[name]?.binary == true
            update(content.parts + MultipartPart(name, if (binary) PartValue.File(null) else PartValue.Text("")))
        }
        AddButton(stringResource(R.string.request_add_field), onClick = { update(content.parts + MultipartPart("", PartValue.Text(""))) })
    }
}

@Composable
private fun PartTypeSwitch(
    file: Boolean,
    onChange: (Boolean) -> Unit,
) {
    SingleChoiceSegmentedButtonRow {
        listOf(false, true).forEachIndexed { index, isFile ->
            SegmentedButton(
                selected = file == isFile,
                onClick = { onChange(isFile) },
                shape = SegmentedButtonDefaults.itemShape(index, 2),
                label = { SegmentedLabel(stringResource(if (isFile) R.string.request_part_file else R.string.request_part_text)) },
            )
        }
    }
}

/** 选择本机文件；显示已选文件的名称与大小。 */
@Composable
internal fun FilePickerButton(
    file: FileRef?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    OutlinedButton(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Icon(painterResource(R.drawable.ic_attach_file), contentDescription = null, modifier = Modifier.size(SmallIcon))
        Text(
            text =
                file?.let { selected -> selected.size?.let { "${selected.name}（${formatFileSize(context, it)}）" } ?: selected.name }
                    ?: stringResource(R.string.request_choose_file),
            modifier = Modifier.padding(start = FieldSpacing),
        )
    }
}

/** schema 中声明但尚未填写的字段："＋ 字段名（类型，必填）"。 */
@Composable
internal fun MissingFields(
    missing: ImmutableList<String>,
    schemas: ImmutableMap<String, FieldSchema>,
    onAdd: (String) -> Unit,
) {
    missing.forEach { name ->
        val schema = schemas[name]
        TextButton(onClick = { onAdd(name) }) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(SmallIcon))
            Text(text = name, style = MaterialTheme.typography.codeMedium, modifier = Modifier.padding(start = FieldSpacing))
            schema?.type?.let { TypeText(it, Modifier.padding(start = FieldSpacing)) }
            if (schema?.required ==
                true
            ) {
                Marker(stringResource(R.string.doc_required), MaterialTheme.colorScheme.error, Modifier.padding(start = FieldSpacing))
            }
        }
    }
}

private fun <T> List<T>.replace(
    index: Int,
    value: T,
): List<T> = mapIndexed { i, old -> if (i == index) value else old }

private val RawEditorMinHeight = 160.dp
