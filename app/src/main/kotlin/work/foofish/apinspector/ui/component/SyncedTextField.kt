package work.foofish.apinspector.ui.component

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.drop

/**
 * 与外部状态（ViewModel）双向同步的输入框状态。
 *
 * 输入在本地立即生效并通知 [onValueChange]；外部值的回传若只是此前输入的回声则忽略，
 * 以免异步回传覆盖之后的输入；其他外部变化（如加载预设、重置）会替换输入框内容。
 */
@Composable
fun rememberSyncedTextFieldState(
    value: String,
    onValueChange: (String) -> Unit,
): TextFieldState {
    val state = rememberTextFieldState(value)
    val echoes = remember { ArrayDeque<String>() }
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }.drop(1).collect { text ->
            echoes.addLast(text)
            if (echoes.size > MAX_PENDING_ECHOES) echoes.removeFirst()
            currentOnValueChange(text)
        }
    }
    LaunchedEffect(value) {
        val echo = echoes.indexOf(value)
        if (echo >= 0) {
            repeat(echo + 1) { echoes.removeFirst() }
        } else if (state.text.toString() != value) {
            echoes.clear()
            state.setTextAndPlaceCursorAtEnd(value)
        }
    }
    return state
}

/** 连续输入时，尚未回传的值最多保留这么多个。 */
private const val MAX_PENDING_ECHOES = 64
