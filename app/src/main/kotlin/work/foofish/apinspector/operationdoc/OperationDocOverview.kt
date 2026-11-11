package work.foofish.apinspector.operationdoc

import android.content.ClipData
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import work.foofish.apinspector.R
import work.foofish.apinspector.ui.theme.card
import work.foofish.apinspector.ui.theme.codeLabel
import work.foofish.apinspector.ui.theme.codeMedium

/**
 * 文档页顶部的概览：摘要作为标题，其下是可点按复制的完整路径、可展开的描述，
 * 以及一排元信息胶囊（已弃用、tag、operationId、鉴权方式）。方法与路径已在顶栏，这里不再重复方法徽章。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Overview(
    overview: OperationOverview,
    modifier: Modifier = Modifier,
) {
    val copy = rememberCopy()
    Column(modifier = modifier.padding(horizontal = OverviewInset), verticalArrangement = Arrangement.spacedBy(OverviewSpacing)) {
        overview.summary?.let { Text(it, style = MaterialTheme.typography.headlineSmallEmphasized) }
        CopyablePath(overview.key.path, overview.deprecated, onCopy = { copy(overview.key.path) })
        overview.description?.takeIf { it.isNotBlank() }?.let { ExpandableDescription(it) }
        FlowRow(
            modifier = Modifier.padding(top = ChipsTopSpacing),
            horizontalArrangement = Arrangement.spacedBy(ChipSpacing),
            verticalArrangement = Arrangement.spacedBy(ChipSpacing),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            if (overview.deprecated) {
                MetaChip(
                    text = stringResource(R.string.doc_deprecated),
                    icon = R.drawable.ic_warning,
                    container = MaterialTheme.colorScheme.errorContainer,
                    content = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            overview.tags.forEach { tag ->
                MetaChip(
                    text = tag,
                    icon = null,
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            overview.operationId?.let { id ->
                MetaChip(
                    text = id,
                    icon = R.drawable.ic_api,
                    container = MaterialTheme.colorScheme.card,
                    content = MaterialTheme.colorScheme.onSurface,
                    textStyle = MaterialTheme.typography.codeLabel,
                    onClick = { copy(id) },
                    onClickLabel = stringResource(R.string.doc_copy_operation_id),
                )
            }
            SecurityChips(overview)
        }
    }
}

/** 完整路径：点按整行复制。 */
@Composable
private fun CopyablePath(
    path: String,
    deprecated: Boolean,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .clip(MaterialTheme.shapes.small)
                .clickable(onClickLabel = stringResource(R.string.doc_copy_path), onClick = onCopy)
                .padding(vertical = PathPaddingVertical),
        horizontalArrangement = Arrangement.spacedBy(ChipSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = path,
            modifier = Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.codeMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textDecoration = if (deprecated) TextDecoration.LineThrough else null,
        )
        Icon(
            painter = painterResource(R.drawable.ic_content_copy),
            contentDescription = null,
            modifier = Modifier.size(ChipIconSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 描述：较长时只显示前几行，点按展开 / 收起。 */
@Composable
private fun ExpandableDescription(
    text: String,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(text) { mutableStateOf(false) }
    var overflowing by remember(text) { mutableStateOf(false) }
    val toggleable = expanded || overflowing
    Column(
        modifier =
            modifier
                .animateContentSize()
                .then(if (toggleable) Modifier.clickable { expanded = !expanded } else Modifier),
        verticalArrangement = Arrangement.spacedBy(DescriptionToggleSpacing),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else DESCRIPTION_COLLAPSED_LINES,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflowing = it.hasVisualOverflow },
        )
        if (toggleable) {
            Text(
                text = stringResource(if (expanded) R.string.action_collapse else R.string.action_expand),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * 鉴权方式：每种可选方式一枚胶囊（需同时满足的方案以 `+` 相连，空项为匿名）；多于一种时注明满足其一即可。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowScope.SecurityChips(overview: OperationOverview) {
    val colors = MaterialTheme.colorScheme
    if (overview.security.isEmpty()) {
        MetaChip(stringResource(R.string.doc_security_none), icon = null, container = colors.card, content = colors.onSurfaceVariant)
        return
    }
    val combined = stringResource(R.string.doc_security_combined_separator)
    val anonymous = stringResource(R.string.doc_security_anonymous)
    overview.security.forEach { schemes ->
        MetaChip(
            text = schemes.joinToString(combined).ifEmpty { anonymous },
            icon = R.drawable.ic_lock.takeIf { schemes.isNotEmpty() },
            container = colors.card,
            content = colors.onSurface,
        )
    }
    if (overview.security.size > 1) {
        Text(
            text = stringResource(R.string.doc_security_any),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
        )
    }
}

/** 概览里的一枚元信息胶囊；给出 [onClick] 时可点按（复制）。 */
@Composable
private fun MetaChip(
    text: String,
    @DrawableRes icon: Int?,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.labelMedium,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
) {
    Surface(modifier = modifier, shape = CircleShape, color = container, contentColor = content) {
        Row(
            modifier =
                Modifier
                    .then(if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, onClick = onClick) else Modifier)
                    .padding(horizontal = ChipPaddingHorizontal, vertical = ChipPaddingVertical),
            horizontalArrangement = Arrangement.spacedBy(ChipIconSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let { Icon(painterResource(it), contentDescription = null, modifier = Modifier.size(ChipIconSize)) }
            Text(text, style = textStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 把文本放进剪贴板（系统自带"已复制"的提示）。 */
@Composable
internal fun rememberCopy(): (String) -> Unit {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    return remember(clipboard, scope) {
        { text -> scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(text, text))) } }
    }
}

private const val DESCRIPTION_COLLAPSED_LINES = 4
private val OverviewInset = 8.dp
private val OverviewSpacing = 6.dp
private val PathPaddingVertical = 2.dp
private val DescriptionToggleSpacing = 2.dp
private val ChipsTopSpacing = 4.dp
private val ChipSpacing = 8.dp
private val ChipPaddingHorizontal = 10.dp
private val ChipPaddingVertical = 5.dp
private val ChipIconSpacing = 6.dp
private val ChipIconSize = 16.dp
