package work.foofish.apinspector.request.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import work.foofish.apinspector.R
import work.foofish.apinspector.openapi.request.JsonNode
import work.foofish.apinspector.openapi.request.Variables
import work.foofish.apinspector.request.body.FieldSchema
import work.foofish.apinspector.request.body.JsonRow
import work.foofish.apinspector.request.body.JsonStep
import work.foofish.apinspector.request.body.JsonValueType
import work.foofish.apinspector.request.body.emptyNode
import work.foofish.apinspector.ui.component.indentGuides
import work.foofish.apinspector.ui.component.rememberSyncedTextFieldState
import work.foofish.apinspector.ui.text.label
import work.foofish.apinspector.ui.theme.cardInset
import work.foofish.apinspector.ui.theme.codeMedium

/**
 * 可视化 JSON 编辑中的一行。
 *
 * 整棵树排成一张"键 | 值"的表：每行 40dp 高，键在左、值在右，同一级的键与值各自对齐；
 * 每一级祖先在行左侧有一条引导线，行与行上下紧贴，线连成一条，层级一目了然。
 * 不常用的操作（切换类型、删除）收在行尾的菜单里，把宽度留给值。
 */
@Composable
internal fun JsonRowItem(
    row: JsonRow,
    variables: ImmutableList<String>,
    actions: JsonEditorActions,
    modifier: Modifier = Modifier,
) {
    val rowModifier =
        modifier
            .fillMaxWidth()
            .indentGuides(row.depth, Indent, Slot / 2, MaterialTheme.colorScheme.outlineVariant)
            .padding(start = Indent * row.depth)
    when (row) {
        is JsonRow.Value -> {
            ValueRow(row, variables, actions, rowModifier)
        }

        is JsonRow.Container -> {
            ContainerRow(row, variables, actions, rowModifier)
        }

        is JsonRow.Missing -> {
            AddRow(onClick = { actions.onAddMember(row.parent, row.name) }, modifier = rowModifier) {
                Text(row.name, style = MaterialTheme.typography.codeMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                row.schema.type?.let { TypeText(it, Modifier.padding(start = FieldSpacing)) }
                if (row.schema.required) RequiredMark()
            }
        }

        is JsonRow.Add -> {
            AddRow(
                onClick = { if (row.array) actions.onAddItem(row.parent) else actions.onRequestMember(row.parent) },
                modifier = rowModifier,
            ) {
                Text(
                    text = stringResource(if (row.array) R.string.request_add_item else R.string.request_add_field),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** 叶子值：键、值、行尾菜单；正在编辑时在行下显示字段说明。 */
@Composable
private fun ValueRow(
    row: JsonRow.Value,
    variables: ImmutableList<String>,
    actions: JsonEditorActions,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Column(modifier) {
        Row(modifier = Modifier.heightIn(min = RowHeight), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(Slot))
            KeyLabel(row.label, row.schema, Modifier.weight(KEY_WEIGHT).padding(end = FieldSpacing))
            Box(Modifier.weight(VALUE_WEIGHT)) { ValueCell(row, variables, actions, interactionSource) }
            RowMenu(
                type = row.type,
                removable = row.label != null,
                onSelectType = { actions.onSet(row.path, emptyNode(it, variables.firstOrNull() ?: DEFAULT_VARIABLE)) },
                onRemove = { actions.onRemove(row.path) },
            )
        }
        val description = row.schema?.description
        if (focused && !description.isNullOrBlank()) {
            Text(
                text = description,
                modifier = Modifier.padding(start = Slot, end = MenuButtonSize, bottom = DescriptionBottomPadding),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = DESCRIPTION_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ValueCell(
    row: JsonRow.Value,
    variables: ImmutableList<String>,
    actions: JsonEditorActions,
    interactionSource: MutableInteractionSource,
) {
    when (row.type) {
        JsonValueType.STRING -> {
            CompactField(
                value = row.text,
                onValid = { actions.onSet(row.path, JsonNode.StringNode(it)) },
                interactionSource = interactionSource,
                placeholder = row.schema?.type?.text,
                choices = row.schema?.choices ?: persistentListOf(),
            )
        }

        JsonValueType.NUMBER -> {
            CompactField(
                value = row.text,
                onValid = { actions.onSet(row.path, JsonNode.NumberNode(it)) },
                interactionSource = interactionSource,
                placeholder = row.schema?.type?.text,
                suffix = stringResource(R.string.json_type_number),
                isValid = NUMBER::matches,
            )
        }

        JsonValueType.VARIABLE -> {
            CompactField(
                value = row.text,
                onValid = { actions.onSet(row.path, JsonNode.VariableNode(it)) },
                interactionSource = interactionSource,
                suffix = stringResource(R.string.json_type_variable),
                choices = variables,
                isValid = Variables::isValidName,
            )
        }

        JsonValueType.BOOLEAN -> {
            Switch(checked = row.text.toBoolean(), onCheckedChange = { actions.onSet(row.path, JsonNode.BooleanNode(it)) })
        }

        JsonValueType.NULL -> {
            Text(
                text = stringResource(R.string.json_type_null),
                style = MaterialTheme.typography.codeMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        JsonValueType.OBJECT, JsonValueType.ARRAY -> {}
    }
}

/**
 * 表格里的值输入框：36dp 高、内嵌底色、无标签；聚焦时描主色边，内容不合法时描错误色边并保留输入。
 *
 * @param onValid 只在内容合法时提交。
 * @param placeholder 值为空时的提示（schema 类型）。
 * @param suffix 框内右侧的类型标注，用来区分数字 `10` 与字符串 `"10"`。
 * @param choices 可选值；不为空时框内右侧是下拉箭头，仍可手动输入。
 */
@Composable
private fun CompactField(
    value: String,
    onValid: (String) -> Unit,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    suffix: String? = null,
    choices: ImmutableList<String> = persistentListOf(),
    isValid: (String) -> Boolean = { true },
) {
    var invalid by remember { mutableStateOf(false) }
    val state =
        rememberSyncedTextFieldState(value) { text ->
            invalid = !isValid(text)
            if (!invalid) onValid(text)
        }
    val focused by interactionSource.collectIsFocusedAsState()
    val colors = MaterialTheme.colorScheme
    val borderColor =
        when {
            invalid -> colors.error
            focused -> colors.primary
            else -> Color.Transparent
        }
    val textStyle = MaterialTheme.typography.codeMedium
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(FieldHeight)
                .clip(MaterialTheme.shapes.small)
                .background(colors.cardInset)
                .border(FieldBorder, borderColor, MaterialTheme.shapes.small)
                .padding(start = FieldPaddingStart),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            state = state,
            modifier = Modifier.weight(1f),
            textStyle = textStyle.copy(color = colors.onSurface),
            lineLimits = TextFieldLineLimits.SingleLine,
            interactionSource = interactionSource,
            cursorBrush = SolidColor(colors.primary),
            decorator = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (state.text.isEmpty() && placeholder != null) {
                        Text(placeholder, style = textStyle, color = colors.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    innerTextField()
                }
            },
        )
        if (choices.isNotEmpty()) {
            ChoiceMenu(choices, onSelect = onValid)
        } else {
            Text(
                text = suffix.orEmpty(),
                modifier = Modifier.padding(horizontal = FieldSpacing),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** 输入框右侧的下拉：从可选值中选一个。 */
@Composable
private fun ChoiceMenu(
    choices: ImmutableList<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Box(
            modifier = Modifier.size(FieldHeight).clickable(onClickLabel = stringResource(R.string.action_expand)) { expanded = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_arrow_drop_down),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, shape = MaterialTheme.shapes.large) {
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(choice, style = MaterialTheme.typography.codeMedium) },
                    onClick = {
                        expanded = false
                        onSelect(choice)
                    },
                )
            }
        }
    }
}

/** 对象或数组：整行可点，展开或收起；键后面是 schema 类型与成员数。 */
@Composable
private fun ContainerRow(
    row: JsonRow.Container,
    variables: ImmutableList<String>,
    actions: JsonEditorActions,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(if (row.expanded) 0f else COLLAPSED_ROTATION, label = "chevron")
    val toggleLabel = stringResource(if (row.expanded) R.string.action_collapse else R.string.action_expand)
    Row(
        modifier =
            modifier
                .clip(MaterialTheme.shapes.small)
                .clickable(onClickLabel = toggleLabel) { actions.onToggle(row.path) }
                .heightIn(min = RowHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(Slot), contentAlignment = Alignment.Center) {
            Icon(
                painterResource(R.drawable.ic_expand_more),
                contentDescription = null,
                modifier = Modifier.size(ChevronSize).rotate(rotation),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // 键、类型与成员数占满菜单左侧的宽度：行尾菜单与叶子行的菜单上下对齐。
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            KeyLabel(row.label, row.schema)
            row.schema?.type?.let { TypeText(it, Modifier.weight(1f, fill = false).padding(start = FieldSpacing)) }
            Text(
                text =
                    stringResource(
                        if (row.type ==
                            JsonValueType.ARRAY
                        ) {
                            R.string.request_json_items
                        } else {
                            R.string.request_json_members
                        },
                        row.size,
                    ),
                modifier = Modifier.padding(start = FieldSpacing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            if (row.variants.isNotEmpty()) VariantMenu(row.variants, row.variant) { actions.onSelectVariant(row.path, it) }
        }
        RowMenu(
            type = row.type,
            removable = row.label != null,
            onSelectType = { actions.onSet(row.path, emptyNode(it, variables.firstOrNull() ?: DEFAULT_VARIABLE)) },
            onRemove = { actions.onRemove(row.path) },
        )
    }
}

/** 键名（或下标）；必填的键后面带红色 `*`；长按查看字段说明。 */
@Composable
private fun KeyLabel(
    label: JsonStep?,
    schema: FieldSchema?,
    modifier: Modifier = Modifier,
) {
    val name =
        when (label) {
            is JsonStep.Key -> label.name
            is JsonStep.Index -> "[${label.index}]"
            null -> stringResource(R.string.request_json_root)
        }
    val requiredColor = MaterialTheme.colorScheme.error
    Box(modifier) {
        DescriptionTooltip(schema?.description) {
            Text(
                text =
                    buildAnnotatedString {
                        append(name)
                        if (schema?.required == true) withStyle(SpanStyle(color = requiredColor)) { append(REQUIRED) }
                    },
                style = MaterialTheme.typography.codeMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RequiredMark(modifier: Modifier = Modifier) {
    Text(REQUIRED, modifier = modifier, style = MaterialTheme.typography.codeMedium, color = MaterialTheme.colorScheme.error)
}

/** "＋"开头的一行：添加 schema 里声明的字段、任意字段或数组元素。 */
@Composable
private fun AddRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.clip(MaterialTheme.shapes.small).clickable(onClick = onClick).heightIn(min = AddRowHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(Slot), contentAlignment = Alignment.Center) {
            Icon(
                painterResource(R.drawable.ic_add),
                contentDescription = null,
                modifier = Modifier.size(ChevronSize),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        content()
    }
}

/** 行尾菜单：切换值的类型（当前类型打勾）、删除这一项。 */
@Composable
private fun RowMenu(
    type: JsonValueType,
    removable: Boolean,
    onSelectType: (JsonValueType) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(MenuButtonSize)) {
            Icon(
                painterResource(R.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.action_more),
                modifier = Modifier.size(ChevronSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, shape = MaterialTheme.shapes.large) {
            JsonValueType.entries.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(stringResource(entry.label())) },
                    onClick = {
                        expanded = false
                        if (entry != type) onSelectType(entry)
                    },
                    trailingIcon = if (entry == type) ({ Icon(painterResource(R.drawable.ic_check), contentDescription = null) }) else null,
                )
            }
            if (removable) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_delete)) },
                    onClick = {
                        expanded = false
                        onRemove()
                    },
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
}

@Composable
private fun VariantMenu(
    variants: ImmutableList<String>,
    selected: Int?,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(
                selected?.let(variants::getOrNull) ?: stringResource(R.string.request_json_variant),
                style = MaterialTheme.typography.labelMedium,
            )
            Icon(painterResource(R.drawable.ic_arrow_drop_down), contentDescription = null, modifier = Modifier.size(SmallIcon))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, shape = MaterialTheme.shapes.large) {
            variants.forEachIndexed { index, name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        expanded = false
                        onSelect(index)
                    },
                )
            }
        }
    }
}

/** 每一级的缩进，等于箭头列的宽度：子节点的键正好排在父节点的键下方偏右一格。 */
private val Indent = 20.dp

/** 行首的箭头列；叶子行也留出这一列，同级的键才能对齐。 */
private val Slot = 20.dp
private val ChevronSize = 18.dp
private val RowHeight = 40.dp
private val AddRowHeight = 36.dp
private val FieldHeight = 34.dp
private val FieldBorder = 1.dp
private val FieldPaddingStart = 10.dp
private val MenuButtonSize = 36.dp
private val DescriptionBottomPadding = 4.dp
private const val KEY_WEIGHT = 2f
private const val VALUE_WEIGHT = 3f
private const val DESCRIPTION_LINES = 3
private const val REQUIRED = "*"
private const val COLLAPSED_ROTATION = -90f

/** 当前环境没有变量时，切换为变量类型使用的名称。 */
private const val DEFAULT_VARIABLE = "name"
private val NUMBER = Regex("""-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?""")
