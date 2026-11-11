package work.foofish.apinspector.request.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import work.foofish.apinspector.R
import work.foofish.apinspector.extraction.ExtractionRule
import work.foofish.apinspector.openapi.HttpMethod
import work.foofish.apinspector.openapi.request.EnvironmentVariable
import work.foofish.apinspector.openapi.request.JsonPath
import work.foofish.apinspector.openapi.request.Variables
import work.foofish.apinspector.request.CustomEditor
import work.foofish.apinspector.request.CustomRequestActions
import work.foofish.apinspector.request.ExtractionActions
import work.foofish.apinspector.ui.component.CardRow
import work.foofish.apinspector.ui.component.MethodBadge
import work.foofish.apinspector.ui.component.SectionCard
import work.foofish.apinspector.ui.component.SectionLabel
import work.foofish.apinspector.ui.component.cardRows
import work.foofish.apinspector.ui.theme.ScreenPadding

/** 自定义请求的方法与地址（PRD 4.2）：点方法徽章换方法，地址可以引用变量。 */
internal fun LazyListScope.customAddressItem(
    custom: CustomEditor,
    actions: CustomRequestActions,
) {
    item(key = ADDRESS_KEY) {
        SectionCard(Modifier.padding(horizontal = ScreenPadding).padding(top = SectionTopSpacing)) {
            Row(horizontalArrangement = Arrangement.spacedBy(FieldSpacing), verticalAlignment = Alignment.CenterVertically) {
                MethodMenu(custom.method, onSelect = actions::setMethod)
                ValueField(
                    value = custom.url,
                    onValueChange = actions::setUrl,
                    choices = persistentListOf(),
                    label = stringResource(if (custom.scratch) R.string.custom_url else R.string.custom_url_or_path),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MethodMenu(
    method: HttpMethod,
    onSelect: (HttpMethod) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        MethodBadge(
            method,
            Modifier.clip(CircleShape).clickable(onClickLabel = stringResource(R.string.custom_change_method)) { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, shape = MaterialTheme.shapes.large) {
            HttpMethod.PATH_ITEM_METHODS.forEach { entry ->
                DropdownMenuItem(
                    text = { MethodBadge(entry) },
                    onClick = {
                        expanded = false
                        onSelect(entry)
                    },
                )
            }
        }
    }
}

/** 临时请求自己的变量（ADR-0002）：只作用于这个临时请求。 */
internal fun LazyListScope.variablesSection(
    variables: ImmutableList<EnvironmentVariable>,
    onChange: (List<EnvironmentVariable>) -> Unit,
) {
    item(key = VARIABLES_KEY) {
        SectionHeader(
            title = stringResource(R.string.custom_variables),
            addText = stringResource(R.string.environment_add_variable),
            onAdd = { onChange(variables + EnvironmentVariable("", "")) },
            modifier = Modifier.animateItem(),
        )
    }
    cardRows(
        variables.mapIndexed { index, variable ->
            CardRow("variable/$index") {
                KeyValueRow(
                    name = variable.name,
                    value = variable.value,
                    onNameChange = { name -> onChange(variables.replaced(index, variable.copy(name = name.trim()))) },
                    onValueChange = { value -> onChange(variables.replaced(index, variable.copy(value = value))) },
                    onRemove = { onChange(variables.filterIndexed { i, _ -> i != index }) },
                )
            }
        },
    )
}

/**
 * 提取规则（PRD 5）：2xx 响应后按 JSONPath 从 Body 取值写入变量。
 *
 * @param variables 可写入的现有变量，作为变量名的候选。
 */
internal fun LazyListScope.rulesSection(
    rules: ImmutableList<ExtractionRule>,
    variables: ImmutableList<String>,
    actions: ExtractionActions,
) {
    item(key = RULES_KEY) {
        SectionHeader(
            title = stringResource(R.string.extraction_rules),
            addText = stringResource(R.string.extraction_add_rule),
            onAdd = { actions.addRule(jsonPath = "", variable = "") },
            modifier = Modifier.animateItem(),
        )
    }
    cardRows(
        rules.map { rule ->
            CardRow("rule/${rule.id.value}") {
                RuleRow(rule, variables, onChange = actions::updateRule, onRemove = { actions.removeRule(rule.id) })
            }
        },
    )
}

/** 一条规则：JSONPath 写不对、变量名不合法时标红；没填完或标红的规则发送后不会提取。 */
@Composable
private fun RuleRow(
    rule: ExtractionRule,
    variables: ImmutableList<String>,
    onChange: (ExtractionRule) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(FieldSpacing), verticalAlignment = Alignment.CenterVertically) {
        ValueField(
            value = rule.jsonPath,
            onValueChange = { onChange(rule.copy(jsonPath = it)) },
            choices = persistentListOf(),
            label = stringResource(R.string.extraction_json_path),
            modifier = Modifier.weight(PATH_WEIGHT),
            isError = rule.jsonPath.isNotBlank() && JsonPath.parse(rule.jsonPath) == null,
        )
        ValueField(
            value = rule.variable,
            onValueChange = { onChange(rule.copy(variable = it.trim())) },
            choices = variables,
            label = stringResource(R.string.extraction_variable),
            modifier = Modifier.weight(VARIABLE_WEIGHT),
            isError = rule.variable.isNotEmpty() && !Variables.isValidName(rule.variable),
        )
        RemoveButton(onRemove)
    }
}

/** 分区标题，右侧是"添加"按钮：没有内容时不显示空卡片。 */
@Composable
private fun SectionHeader(
    title: String,
    addText: String,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = ScreenPadding, end = HeaderEndPadding),
        verticalAlignment = Alignment.Bottom,
    ) {
        SectionLabel(title, Modifier.weight(1f))
        AddButton(addText, onClick = onAdd)
    }
}

private fun <T> List<T>.replaced(
    index: Int,
    value: T,
): List<T> = mapIndexed { i, old -> if (i == index) value else old }

private const val ADDRESS_KEY = "custom/address"
private const val VARIABLES_KEY = "custom/variables"
private const val RULES_KEY = "rules"
private const val PATH_WEIGHT = 3f
private const val VARIABLE_WEIGHT = 2f
private val SectionTopSpacing = 12.dp
private val HeaderEndPadding = 8.dp
