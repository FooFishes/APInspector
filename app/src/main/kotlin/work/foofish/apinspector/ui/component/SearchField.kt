package work.foofish.apinspector.ui.component

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import work.foofish.apinspector.R

/** 圆角搜索框；输入内容变化时回调 [onQueryChange]。输入状态可跨配置变更保存。 */
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
    TextField(
        state = state,
        modifier = modifier,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
        trailingIcon =
            if (state.text.isNotEmpty()) {
                {
                    IconButton(onClick = state::clearText) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.action_clear_search))
                    }
                }
            } else {
                null
            },
        lineLimits = TextFieldLineLimits.SingleLine,
        shape = CircleShape,
        colors =
            TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
    )
}
