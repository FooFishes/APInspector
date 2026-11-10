package work.foofish.apinspector.request.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import work.foofish.apinspector.R
import work.foofish.apinspector.openapi.request.ExtraLocation
import work.foofish.apinspector.openapi.request.ExtraParameter
import work.foofish.apinspector.openapi.request.Field
import work.foofish.apinspector.openapi.request.ParameterShape
import work.foofish.apinspector.openapi.request.ParameterValue
import work.foofish.apinspector.operationdoc.TypeLabel
import work.foofish.apinspector.request.ParameterField
import work.foofish.apinspector.ui.component.rememberSyncedTextFieldState
import work.foofish.apinspector.ui.theme.codeMedium
import work.foofish.apinspector.ui.theme.codeSmall

/**
 * 一个声明参数的编辑行（PRD 4.1）：名称、类型与必填标记；长按名称查看描述。
 * 非必填参数有勾选框，不勾选则不发送但保留值。
 */
@Composable
internal fun ParameterFieldRow(
    field: ParameterField,
    onValueChange: (ParameterValue) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(CheckboxSlot), contentAlignment = Alignment.Center) {
            if (!field.alwaysSent) Checkbox(checked = field.enabled, onCheckedChange = onEnabledChange)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FieldSpacing)) {
            ParameterHeading(field)
            when (field.shape) {
                ParameterShape.TEXT -> {
                    ValueField(
                        value = (field.value as? ParameterValue.Text)?.text.orEmpty(),
                        onValueChange = { onValueChange(ParameterValue.Text(it)) },
                        choices = field.choices,
                        label = null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                ParameterShape.ITEMS -> {
                    ItemsEditor(
                        (field.value as? ParameterValue.Items)?.items.orEmpty(),
                        field.choices,
                    ) { onValueChange(ParameterValue.Items(it)) }
                }

                ParameterShape.FIELDS -> {
                    FieldsEditor((field.value as? ParameterValue.Fields)?.fields.orEmpty()) { onValueChange(ParameterValue.Fields(it)) }
                }
            }
        }
    }
}

@Composable
private fun ParameterHeading(field: ParameterField) {
    DescriptionTooltip(field.description) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(FieldSpacing), itemVerticalAlignment = Alignment.CenterVertically) {
            Text(
                text = field.name,
                style = MaterialTheme.typography.codeMedium,
                textDecoration = if (field.deprecated) TextDecoration.LineThrough else null,
            )
            field.type?.let { TypeText(it) }
            if (field.required) Marker(stringResource(R.string.doc_required), MaterialTheme.colorScheme.error)
            if (field.ignored) Marker(stringResource(R.string.request_ignored_header), MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 长按显示描述；没有描述时不响应。 */
@Composable
internal fun DescriptionTooltip(
    description: String?,
    content: @Composable () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(description.orEmpty()) } },
        state = rememberTooltipState(isPersistent = true),
        enableUserInput = !description.isNullOrBlank(),
        content = content,
    )
}

@Composable
internal fun TypeText(
    type: TypeLabel,
    modifier: Modifier = Modifier,
) {
    Text(
        text = type.text,
        modifier = modifier,
        style = MaterialTheme.typography.codeSmall,
        color = if (type.kind == TypeLabel.Kind.UNRESOLVED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
    )
}

@Composable
internal fun Marker(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Text(text = text, modifier = modifier, style = MaterialTheme.typography.labelSmall, color = color)
}

/** 单个值的输入框；有可选值时可以从下拉中选择，也可以手动输入。 */
@Composable
internal fun ValueField(
    value: String,
    onValueChange: (String) -> Unit,
    choices: ImmutableList<String>,
    label: String?,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    val state = rememberSyncedTextFieldState(value, onValueChange)
    if (choices.isEmpty()) {
        OutlinedTextField(
            state = state,
            modifier = modifier,
            label = label?.let { { Text(it) } },
            textStyle = MaterialTheme.typography.codeMedium,
            lineLimits = TextFieldLineLimits.SingleLine,
            isError = isError,
        )
        return
    }
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            state = state,
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
            label = label?.let { { Text(it) } },
            textStyle = MaterialTheme.typography.codeMedium,
            lineLimits = TextFieldLineLimits.SingleLine,
            isError = isError,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(choice, style = MaterialTheme.typography.codeMedium) },
                    onClick = {
                        onValueChange(choice)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

/** 数组参数：可增删的值列表（PRD 4.1）。 */
@Composable
private fun ItemsEditor(
    items: List<String>,
    choices: ImmutableList<String>,
    onChange: (List<String>) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(FieldSpacing)) {
        items.forEachIndexed { index, item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                ValueField(
                    value = item,
                    onValueChange = { text -> onChange(items.mapIndexed { i, old -> if (i == index) text else old }) },
                    choices = choices,
                    label = null,
                    modifier = Modifier.weight(1f),
                )
                RemoveButton(onClick = { onChange(items.filterIndexed { i, _ -> i != index }) })
            }
        }
        AddButton(stringResource(R.string.request_add_item), onClick = { onChange(items + "") })
    }
}

/** 对象参数：键值列表。 */
@Composable
internal fun FieldsEditor(
    fields: List<Field>,
    onChange: (List<Field>) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(FieldSpacing)) {
        fields.forEachIndexed { index, field ->
            KeyValueRow(
                name = field.name,
                value = field.value,
                onNameChange = { name -> onChange(fields.mapIndexed { i, old -> if (i == index) old.copy(name = name) else old }) },
                onValueChange = { value -> onChange(fields.mapIndexed { i, old -> if (i == index) old.copy(value = value) else old }) },
                onRemove = { onChange(fields.filterIndexed { i, _ -> i != index }) },
            )
        }
        AddButton(stringResource(R.string.request_add_field), onClick = { onChange(fields + Field("", "")) })
    }
}

@Composable
internal fun KeyValueRow(
    name: String,
    value: String,
    onNameChange: (String) -> Unit,
    onValueChange: (String) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(FieldSpacing), verticalAlignment = Alignment.CenterVertically) {
        ValueField(name, onNameChange, persistentListOf(), stringResource(R.string.request_name), Modifier.weight(KEY_WEIGHT))
        ValueField(value, onValueChange, persistentListOf(), stringResource(R.string.request_value), Modifier.weight(VALUE_WEIGHT))
        RemoveButton(onRemove)
    }
}

/** 额外参数（PRD 4.1）：每行可选 Header / Query，有勾选框。 */
@Composable
internal fun ExtraParameterRow(
    extra: ExtraParameter,
    onChange: (ExtraParameter) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(CheckboxSlot), contentAlignment = Alignment.Center) {
            Checkbox(checked = extra.enabled, onCheckedChange = { onChange(extra.copy(enabled = it)) })
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FieldSpacing)) {
            Text(
                text =
                    stringResource(
                        if (extra.location ==
                            ExtraLocation.HEADER
                        ) {
                            R.string.doc_location_header
                        } else {
                            R.string.doc_location_query
                        },
                    ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            KeyValueRow(
                name = extra.name,
                value = extra.value,
                onNameChange = { onChange(extra.copy(name = it)) },
                onValueChange = { onChange(extra.copy(value = it)) },
                onRemove = onRemove,
            )
        }
    }
}

@Composable
internal fun RemoveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(painterResource(R.drawable.ic_remove), contentDescription = stringResource(R.string.action_delete))
    }
}

@Composable
internal fun AddButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(onClick = onClick, modifier = modifier) {
        Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(SmallIcon))
        Spacer(Modifier.width(FieldSpacing))
        Text(text)
    }
}

internal val FieldSpacing = 8.dp
internal val CheckboxSlot = 48.dp
internal val SmallIcon = 18.dp
private const val KEY_WEIGHT = 2f
private const val VALUE_WEIGHT = 3f

/** 参数区左右留白；与勾选框列对齐。 */
internal val EditorPadding = 16.dp
