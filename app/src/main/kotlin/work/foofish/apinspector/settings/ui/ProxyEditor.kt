package work.foofish.apinspector.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import work.foofish.apinspector.R
import work.foofish.apinspector.settings.ProxyConfig
import work.foofish.apinspector.settings.ProxyType
import work.foofish.apinspector.ui.component.rememberSyncedTextFieldState
import work.foofish.apinspector.ui.theme.codeMedium

/** 代理设置（PRD 6）：HTTP 或 SOCKS5，主机、端口，可选的用户名与密码。 */
@Composable
fun ProxyConfigEditor(
    config: ProxyConfig,
    onChange: (ProxyConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(FieldSpacing)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            ProxyType.entries.forEachIndexed { index, type ->
                SegmentedButton(
                    selected = config.type == type,
                    onClick = { onChange(config.copy(type = type)) },
                    shape = SegmentedButtonDefaults.itemShape(index, ProxyType.entries.size),
                    label = { Text(stringResource(if (type == ProxyType.HTTP) R.string.proxy_http else R.string.proxy_socks5)) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(FieldSpacing)) {
            SettingTextField(config.host, {
                onChange(config.copy(host = it.trim()))
            }, stringResource(R.string.proxy_host), Modifier.weight(HOST_WEIGHT))
            NumberField(
                value = config.port.takeIf { it > 0 }?.toLong(),
                onValueChange = { onChange(config.copy(port = it?.toInt() ?: 0)) },
                label = stringResource(R.string.proxy_port),
                range = PORT_RANGE,
                modifier = Modifier.weight(1f),
            )
        }
        SettingTextField(config.username, {
            onChange(config.copy(username = it))
        }, stringResource(R.string.proxy_username), Modifier.fillMaxWidth())
        SecretField(
            config.password,
            { onChange(config.copy(password = it)) },
            stringResource(R.string.proxy_password),
            Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun SettingTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    isError: Boolean = false,
    code: Boolean = false,
) {
    OutlinedTextField(
        state = rememberSyncedTextFieldState(value, onValueChange),
        modifier = modifier,
        label = { Text(label) },
        supportingText = supporting?.let { { Text(it) } },
        isError = isError,
        textStyle = if (code) MaterialTheme.typography.codeMedium else MaterialTheme.typography.bodyLarge,
        lineLimits = TextFieldLineLimits.SingleLine,
    )
}

/** 密码类输入：默认隐藏（凭证本身明文保存，PRD 1）。 */
@Composable
fun SecretField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedSecureTextField(
        state = rememberSyncedTextFieldState(value, onValueChange),
        modifier = modifier,
        label = { Text(label) },
        textObfuscationMode = TextObfuscationMode.RevealLastTyped,
    )
}

/** 正整数输入；为空或超出 [range] 时标红且不提交。 */
@Composable
fun NumberField(
    value: Long?,
    onValueChange: (Long?) -> Unit,
    label: String,
    range: LongRange,
    modifier: Modifier = Modifier,
    supporting: String? = null,
) {
    val text = value?.toString().orEmpty()
    val state =
        rememberSyncedTextFieldState(text) { input ->
            val number = input.toLongOrNull()
            if (input.isEmpty()) {
                onValueChange(null)
            } else if (number != null && number in range) {
                onValueChange(number)
            }
        }
    val current = state.text.toString()
    val invalid = current.isNotEmpty() && current.toLongOrNull()?.let { it in range } != true
    OutlinedTextField(
        state = state,
        modifier = modifier,
        label = { Text(label) },
        supportingText = supporting?.let { { Text(it) } },
        isError = invalid,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        lineLimits = TextFieldLineLimits.SingleLine,
    )
}

internal val FieldSpacing = 8.dp
private const val HOST_WEIGHT = 2f
private val PORT_RANGE = 1L..65_535L
