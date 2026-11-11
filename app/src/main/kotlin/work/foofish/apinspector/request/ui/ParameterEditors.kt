package work.foofish.apinspector.request.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.material3.TextFieldLabelScope
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
import androidx.compose.ui.text.style.TextOverflow
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
import work.foofish.apinspector.ui.component.FieldShape
import work.foofish.apinspector.ui.component.fieldColors
import work.foofish.apinspector.ui.component.rememberSyncedTextFieldState
import work.foofish.apinspector.ui.theme.codeMedium
import work.foofish.apinspector.ui.theme.codeSmall

/**
 * 一个声明参数的编辑行（PRD 4.1）：名称、类型与必填标记；长按名称查看描述。
 * 非必填参数有勾选框，不勾选则不发送但保留值。
 *
 * @param reserveToggle 是否为勾选框留出一列；整个分区都是必填参数时不必留，输入框可以用满宽度。
 */
@Composable
internal fun ParameterFieldRow(
    field: ParameterField,
    onValueChange: (ParameterValue) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    reserveToggle: Boolean = true,
) {
    // 普通的单值参数只占一行：名称、类型与必填标记作为输入框的标签。
    // 已废弃或会被忽略的参数、以及数组 / 对象参数仍然单独显示标题行。
    val compact = field.shape == ParameterShape.TEXT && !field.deprecated && !field.ignored
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        if (reserveToggle) {
            Box(
                modifier = Modifier.padding(top = if (compact) FieldLabelOffset else NoOffset).size(CheckboxSlot),
                contentAlignment = Alignment.Center,
            ) {
                if (!field.alwaysSent) Checkbox(checked = field.enabled, onCheckedChange = onEnabledChange)
            }
        } else {
            Spacer(Modifier.width(NoToggleInset))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FieldSpacing)) {
            if (!compact) ParameterHeading(field)
            when (field.shape) {
                ParameterShape.TEXT -> {
                    ValueField(
                        value = (field.value as? ParameterValue.Text)?.text.orEmpty(),
                        onValueChange = { onValueChange(ParameterValue.Text(it)) },
                        choices = field.choices,
                        label = if (compact) field.fieldLabel() else null,
                        modifier = Modifier.fillMaxWidth(),
                        description = field.description.takeIf { compact },
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

/** 输入框的标签，例如 `petId · integer(int64) *`（`*` 表示必填）。 */
private fun ParameterField.fieldLabel(): String =
    listOfNotNull(name, type?.text).joinToString(LABEL_SEPARATOR) + if (required) REQUIRED_MARK else ""

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
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
internal fun Marker(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Text(text = text, modifier = modifier, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
}

/**
 * 单个值的输入框；有可选值时可以从下拉中选择，也可以手动输入。
 *
 * @param description 字段说明：只在输入框获得焦点时显示在下方，正要填这个值的时候才需要它。
 */
@Composable
internal fun ValueField(
    value: String,
    onValueChange: (String) -> Unit,
    choices: ImmutableList<String>,
    label: String?,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    description: String? = null,
) {
    val state = rememberSyncedTextFieldState(value, onValueChange)
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val supportingText: (@Composable () -> Unit)? =
        description?.takeIf { focused && it.isNotBlank() }?.let {
            { Text(it, maxLines = DESCRIPTION_MAX_LINES, overflow = TextOverflow.Ellipsis) }
        }
    val labelContent: (@Composable TextFieldLabelScope.() -> Unit)? =
        label?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
    if (choices.isEmpty()) {
        OutlinedTextField(
            shape = FieldShape,
            colors = fieldColors(),
            state = state,
            modifier = modifier,
            label = labelContent,
            supportingText = supportingText,
            textStyle = MaterialTheme.typography.codeMedium,
            lineLimits = TextFieldLineLimits.SingleLine,
            isError = isError,
            interactionSource = interactionSource,
        )
        return
    }
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            shape = FieldShape,
            colors = fieldColors(),
            state = state,
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
            label = labelContent,
            supportingText = supportingText,
            textStyle = MaterialTheme.typography.codeMedium,
            lineLimits = TextFieldLineLimits.SingleLine,
            isError = isError,
            interactionSource = interactionSource,
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
private const val DESCRIPTION_MAX_LINES = 3
internal const val LABEL_SEPARATOR = " · "
internal const val REQUIRED_MARK = " *"

/** 带标签的描边输入框，其描边框比组件顶部低这么多（为浮起的标签留位）。 */
private val FieldLabelOffset = 8.dp
private val NoOffset = 0.dp

/** 不留勾选框列时，把行内容补到与卡片内边距对齐。 */
private val NoToggleInset = 12.dp
private const val KEY_WEIGHT = 2f
private const val VALUE_WEIGHT = 3f

/** 参数区左右留白；与勾选框列对齐。 */
internal val EditorPadding = 16.dp
