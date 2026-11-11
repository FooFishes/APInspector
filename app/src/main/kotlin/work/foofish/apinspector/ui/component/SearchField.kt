package work.foofish.apinspector.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import work.foofish.apinspector.R
import work.foofish.apinspector.ui.theme.card

/** 页面底色上的胶囊形搜索框；输入内容变化时回调 [onQueryChange]。输入状态可跨配置变更保存。 */
@Composable
fun SearchField(
    placeholder: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberTextFieldState()
    val currentOnQueryChange by rememberUpdatedState(onQueryChange)
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }.collect { currentOnQueryChange(it) }
    }
    PillSearchField(state, placeholder, modifier)
}

/**
 * 紧凑的胶囊形搜索框（高 48dp，比默认输入框矮一档，把纵向空间留给内容）。
 *
 * @param onClose 不为 `null` 时搜索框是临时展开的：始终显示关闭按钮，点击后清空并回调；展开时自动获得焦点。
 */
@Composable
fun PillSearchField(
    state: TextFieldState,
    placeholder: String,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
) {
    val focusRequester = remember { FocusRequester() }
    if (onClose != null) LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
    val textStyle = MaterialTheme.typography.bodyLarge
    Surface(modifier = modifier.height(SearchFieldHeight), shape = CircleShape, color = MaterialTheme.colorScheme.card) {
        Row(modifier = Modifier.padding(start = EdgePadding), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(R.drawable.ic_search),
                contentDescription = null,
                modifier = Modifier.size(SearchIconSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BasicTextField(
                state = state,
                modifier = Modifier.weight(1f).padding(start = TextPadding).focusRequester(focusRequester),
                textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                lineLimits = TextFieldLineLimits.SingleLine,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorator = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (state.text.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = textStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            if (onClose != null || state.text.isNotEmpty()) {
                IconButton(
                    onClick = {
                        state.clearText()
                        onClose?.invoke()
                    },
                ) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.action_clear_search))
                }
            } else {
                Spacer(Modifier.width(EdgePadding))
            }
        }
    }
}

private val SearchFieldHeight = 48.dp
private val SearchIconSize = 20.dp
private val EdgePadding = 16.dp
private val TextPadding = 12.dp
