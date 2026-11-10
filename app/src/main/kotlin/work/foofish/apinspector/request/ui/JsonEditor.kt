package work.foofish.apinspector.request.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import work.foofish.apinspector.R
import work.foofish.apinspector.openapi.request.JsonNode
import work.foofish.apinspector.openapi.request.Variables
import work.foofish.apinspector.request.body.FieldSchema
import work.foofish.apinspector.request.body.JsonPath
import work.foofish.apinspector.request.body.JsonRow
import work.foofish.apinspector.request.body.JsonStep
import work.foofish.apinspector.request.body.JsonValueType
import work.foofish.apinspector.request.body.emptyNode
import work.foofish.apinspector.ui.text.label
import work.foofish.apinspector.ui.theme.codeMedium

/** 可视化 JSON 编辑中的一行：按深度缩进。 */
@Composable
internal fun JsonRowItem(
    row: JsonRow,
    variables: ImmutableList<String>,
    actions: JsonEditorActions,
    modifier: Modifier = Modifier,
) {
    val indented = modifier.padding(start = Indent * row.depth)
    when (row) {
        is JsonRow.Value -> {
            ValueRow(row, variables, actions, indented)
        }

        is JsonRow.Container -> {
            ContainerRow(row, variables, actions, indented)
        }

        is JsonRow.Missing -> {
            TextButton(onClick = { actions.onAddMember(row.parent, row.name) }, modifier = indented) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(SmallIcon))
                Text(row.name, style = MaterialTheme.typography.codeMedium, modifier = Modifier.padding(start = FieldSpacing))
                row.schema.type?.let { TypeText(it, Modifier.padding(start = FieldSpacing)) }
                if (row.schema.required) {
                    Marker(
                        stringResource(R.string.doc_required),
                        MaterialTheme.colorScheme.error,
                        Modifier.padding(start = FieldSpacing),
                    )
                }
            }
        }

        is JsonRow.Add -> {
            AddButton(
                text = stringResource(if (row.array) R.string.request_add_item else R.string.request_add_field),
                onClick = { if (row.array) actions.onAddItem(row.parent) else actions.onRequestMember(row.parent) },
                modifier = indented,
            )
        }
    }
}

@Composable
private fun ValueRow(
    row: JsonRow.Value,
    variables: ImmutableList<String>,
    actions: JsonEditorActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FieldSpacing)) {
        RowHeading(row.path, row.label, row.type, row.schema, variables, actions) {
            if (row.type == JsonValueType.BOOLEAN) {
                Switch(checked = row.text.toBoolean(), onCheckedChange = { actions.onSet(row.path, JsonNode.BooleanNode(it)) })
            }
        }
        when (row.type) {
            JsonValueType.STRING -> {
                ValueField(
                    value = row.text,
                    onValueChange = { actions.onSet(row.path, JsonNode.StringNode(it)) },
                    choices = row.schema?.choices ?: persistentListOf(),
                    label = null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            JsonValueType.NUMBER -> {
                ValidatedField(row.text, persistentListOf(), NUMBER::matches) { actions.onSet(row.path, JsonNode.NumberNode(it)) }
            }

            JsonValueType.VARIABLE -> {
                ValidatedField(row.text, variables, Variables::isValidName) { actions.onSet(row.path, JsonNode.VariableNode(it)) }
            }

            JsonValueType.BOOLEAN, JsonValueType.NULL, JsonValueType.OBJECT, JsonValueType.ARRAY -> {}
        }
    }
}

/** 只在内容合法时提交；不合法时保留输入并标红。 */
@Composable
private fun ValidatedField(
    value: String,
    choices: ImmutableList<String>,
    isValid: (String) -> Boolean,
    onValid: (String) -> Unit,
) {
    var invalid by remember { mutableStateOf(false) }
    ValueField(
        value = value,
        onValueChange = { text ->
            invalid = !isValid(text)
            if (!invalid) onValid(text)
        },
        choices = choices,
        label = null,
        modifier = Modifier.fillMaxWidth(),
        isError = invalid,
    )
}

@Composable
private fun ContainerRow(
    row: JsonRow.Container,
    variables: ImmutableList<String>,
    actions: JsonEditorActions,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(if (row.expanded) 0f else COLLAPSED_ROTATION, label = "chevron")
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { actions.onToggle(row.path) }) {
            Icon(
                painterResource(R.drawable.ic_expand_more),
                contentDescription = stringResource(if (row.expanded) R.string.action_collapse else R.string.action_expand),
                modifier = Modifier.rotate(rotation),
            )
        }
        Box(Modifier.weight(1f)) {
            RowHeading(row.path, row.label, row.type, row.schema, variables, actions) {
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
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (row.variants.isNotEmpty()) VariantMenu(row.variants, row.variant) { actions.onSelectVariant(row.path, it) }
            }
        }
    }
}

/** 键名（或下标）、类型选择、schema 类型与必填标记、删除按钮。 */
@Composable
private fun RowHeading(
    path: JsonPath,
    label: JsonStep?,
    type: JsonValueType,
    schema: FieldSchema?,
    variables: ImmutableList<String>,
    actions: JsonEditorActions,
    trailing: @Composable () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(FieldSpacing), verticalAlignment = Alignment.CenterVertically) {
        DescriptionTooltip(schema?.description) {
            Text(
                text =
                    when (label) {
                        is JsonStep.Key -> label.name
                        is JsonStep.Index -> "[${label.index}]"
                        null -> stringResource(R.string.request_json_root)
                    },
                style = MaterialTheme.typography.codeMedium,
            )
        }
        TypeMenu(type) { actions.onSet(path, emptyNode(it, variables.firstOrNull() ?: DEFAULT_VARIABLE)) }
        schema?.type?.let { TypeText(it) }
        if (schema?.required == true) Marker(stringResource(R.string.doc_required), MaterialTheme.colorScheme.error)
        trailing()
        if (label != null) RemoveButton({ actions.onRemove(path) })
    }
}

@Composable
private fun TypeMenu(
    type: JsonValueType,
    onSelect: (JsonValueType) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(stringResource(type.label()), style = MaterialTheme.typography.labelMedium)
            Icon(painterResource(R.drawable.ic_arrow_drop_down), contentDescription = null, modifier = Modifier.size(SmallIcon))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            JsonValueType.entries.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(stringResource(entry.label())) },
                    onClick = {
                        expanded = false
                        if (entry != type) onSelect(entry)
                    },
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
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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

private val Indent = 16.dp
private const val COLLAPSED_ROTATION = -90f

/** 当前环境没有变量时，切换为变量类型使用的名称。 */
private const val DEFAULT_VARIABLE = "name"
private val NUMBER = Regex("""-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?""")
