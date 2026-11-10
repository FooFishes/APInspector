package work.foofish.apinspector.environment.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import okhttp3.Cookie
import work.foofish.apinspector.R
import work.foofish.apinspector.environment.Environment
import work.foofish.apinspector.environment.EnvironmentNetwork
import work.foofish.apinspector.environment.ProxyChoice
import work.foofish.apinspector.openapi.ApiKeyLocation
import work.foofish.apinspector.openapi.SecurityScheme
import work.foofish.apinspector.openapi.request.Credential
import work.foofish.apinspector.openapi.request.EnvironmentVariable
import work.foofish.apinspector.openapi.request.accepts
import work.foofish.apinspector.openapi.request.blankCredential
import work.foofish.apinspector.request.ui.AddButton
import work.foofish.apinspector.request.ui.RemoveButton
import work.foofish.apinspector.request.ui.ValueField
import work.foofish.apinspector.settings.AppSettings
import work.foofish.apinspector.settings.ProxyConfig
import work.foofish.apinspector.settings.ProxyType
import work.foofish.apinspector.settings.ui.FieldSpacing
import work.foofish.apinspector.settings.ui.NumberField
import work.foofish.apinspector.settings.ui.ProxyConfigEditor
import work.foofish.apinspector.settings.ui.SecretField
import work.foofish.apinspector.settings.ui.SettingTextField
import work.foofish.apinspector.ui.component.BackButton
import work.foofish.apinspector.ui.component.ConfirmDialog
import work.foofish.apinspector.ui.component.EmptyState
import work.foofish.apinspector.ui.component.LoadingState
import work.foofish.apinspector.ui.text.formatDateTime
import work.foofish.apinspector.ui.theme.codeSmall
import java.time.Instant

/** 编辑环境（PRD 5、6）：基本、变量、鉴权、网络与 Cookie，编辑即时保存。 */
@Composable
fun EnvironmentScreen(
    viewModel: EnvironmentViewModel,
    showBack: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmClearCookies by remember { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(state.environment?.name.orEmpty()) },
                navigationIcon = {
                    if (showBack) {
                        BackButton(onClick = onBack)
                    }
                },
            )
        },
    ) { innerPadding ->
        val environment = state.environment
        when {
            state.missing -> {
                Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    EmptyState(painterResource(R.drawable.ic_error), stringResource(R.string.environment_missing), body = null)
                }
            }

            environment == null -> {
                LoadingState(Modifier.padding(innerPadding))
            }

            else -> {
                LazyColumn(
                    contentPadding =
                        PaddingValues(
                            top = innerPadding.calculateTopPadding(),
                            bottom = BottomPadding,
                            start = ScreenPadding,
                            end = ScreenPadding,
                        ),
                    verticalArrangement = Arrangement.spacedBy(FieldSpacing),
                ) {
                    basicSection(environment, viewModel::update)
                    variablesSection(environment.variables.toImmutableList(), viewModel::update)
                    authSection(environment, state.schemes, viewModel::update)
                    networkSection(environment.network) { network -> viewModel.update { it.copy(network = network) } }
                    cookieSection(state.cookies, viewModel::deleteCookie) { confirmClearCookies = true }
                }
            }
        }
    }
    if (confirmClearCookies) {
        ConfirmDialog(
            title = stringResource(R.string.cookie_clear_title),
            body = null,
            confirmText = stringResource(R.string.cookie_clear),
            onConfirm = {
                viewModel.clearCookies()
                confirmClearCookies = false
            },
            onDismiss = { confirmClearCookies = false },
        )
    }
}

private fun LazyListScope.sectionTitle(
    key: String,
    title: Int,
) {
    item(key = key) {
        Text(
            text = stringResource(title),
            modifier = Modifier.padding(top = SectionSpacing),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private fun LazyListScope.basicSection(
    environment: Environment,
    onUpdate: ((Environment) -> Environment) -> Unit,
) {
    sectionTitle("basic", R.string.environment_basic)
    item(key = "name") {
        SettingTextField(environment.name, { name ->
            onUpdate {
                it.copy(name = name)
            }
        }, stringResource(R.string.environment_name), Modifier.fillMaxWidth())
    }
    item(key = "baseUrl") {
        SettingTextField(
            value = environment.baseUrl,
            onValueChange = { url -> onUpdate { it.copy(baseUrl = url.trim()) } },
            label = stringResource(R.string.environment_base_url),
            modifier = Modifier.fillMaxWidth(),
            supporting = if (environment.isBaseUrlIncomplete) stringResource(R.string.environment_incomplete_hint) else null,
            isError = environment.isBaseUrlIncomplete,
            code = true,
        )
    }
}

/** 变量（PRD 5）：由 server 变量生成的变量提供 enum 候选。 */
private fun LazyListScope.variablesSection(
    variables: ImmutableList<EnvironmentVariable>,
    onUpdate: ((Environment) -> Environment) -> Unit,
) {
    fun update(transform: (List<EnvironmentVariable>) -> List<EnvironmentVariable>) =
        onUpdate { it.copy(variables = transform(it.variables)) }
    sectionTitle("variables", R.string.environment_variables)
    itemsIndexed(variables, key = { index, _ -> "variable/$index" }) { index, variable ->
        Row(horizontalArrangement = Arrangement.spacedBy(FieldSpacing), verticalAlignment = Alignment.CenterVertically) {
            ValueField(
                value = variable.name,
                onValueChange = { name ->
                    update { list ->
                        list.mapIndexed { i, old ->
                            if (i ==
                                index
                            ) {
                                old.copy(name = name.trim())
                            } else {
                                old
                            }
                        }
                    }
                },
                choices = NoChoices,
                label = stringResource(R.string.request_name),
                modifier = Modifier.weight(NAME_WEIGHT),
            )
            ValueField(
                value = variable.value,
                onValueChange = { value ->
                    update { list ->
                        list.mapIndexed { i, old ->
                            if (i ==
                                index
                            ) {
                                old.copy(value = value)
                            } else {
                                old
                            }
                        }
                    }
                },
                choices = variable.candidates.toImmutableList(),
                label = stringResource(R.string.request_value),
                modifier = Modifier.weight(VALUE_WEIGHT),
            )
            RemoveButton({ update { list -> list.filterIndexed { i, _ -> i != index } } })
        }
    }
    item(key = "variables/add") {
        AddButton(stringResource(R.string.environment_add_variable), onClick = { update { it + EnvironmentVariable("", "") } })
    }
}

/** 鉴权（PRD 5）：按文档的 `securitySchemes` 每个方案一份凭证。 */
private fun LazyListScope.authSection(
    environment: Environment,
    schemes: ImmutableList<SchemeOption>,
    onUpdate: ((Environment) -> Environment) -> Unit,
) {
    sectionTitle("auth", R.string.environment_auth)
    if (schemes.isEmpty()) {
        item(key = "auth/none") { Muted(stringResource(R.string.environment_auth_none)) }
        return
    }
    items(schemes, key = { "auth/${it.name}" }) { option ->
        val credential = environment.credentials[option.name]?.takeIf(option.scheme::accepts) ?: option.scheme.blankCredential()
        Column(verticalArrangement = Arrangement.spacedBy(FieldSpacing)) {
            Text("${option.name} · ${option.scheme.describe()}", style = MaterialTheme.typography.labelLarge)
            if (credential == null) {
                Muted(stringResource(R.string.environment_auth_unsupported))
            } else {
                CredentialEditor(credential, onChange = { updated ->
                    onUpdate {
                        it.copy(
                            credentials =
                                it.credentials + (option.name to updated),
                        )
                    }
                })
            }
        }
    }
}

@Composable
private fun CredentialEditor(
    credential: Credential,
    onChange: (Credential) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(FieldSpacing)) {
        CredentialFields(credential, onChange)
    }
}

@Composable
private fun ColumnScope.CredentialFields(
    credential: Credential,
    onChange: (Credential) -> Unit,
) {
    when (credential) {
        is Credential.Bearer -> {
            SecretField(
                credential.token,
                { onChange(credential.copy(token = it)) },
                stringResource(R.string.auth_token),
                Modifier.fillMaxWidth(),
            )
        }

        is Credential.Basic -> {
            SettingTextField(credential.username, {
                onChange(credential.copy(username = it))
            }, stringResource(R.string.auth_username), Modifier.fillMaxWidth())
            SecretField(credential.password, {
                onChange(credential.copy(password = it))
            }, stringResource(R.string.auth_password), Modifier.fillMaxWidth())
        }

        is Credential.ApiKey -> {
            SecretField(credential.value, {
                onChange(credential.copy(value = it))
            }, stringResource(R.string.auth_api_key_value), Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(FieldSpacing), verticalAlignment = Alignment.CenterVertically) {
                SettingTextField(credential.name, {
                    onChange(credential.copy(name = it.trim()))
                }, stringResource(R.string.auth_api_key_name), Modifier.weight(1f), code = true)
                SingleChoiceSegmentedButtonRow {
                    ApiKeyLocation.entries.forEachIndexed { index, location ->
                        SegmentedButton(
                            selected = credential.location == location,
                            onClick = { onChange(credential.copy(location = location)) },
                            shape = SegmentedButtonDefaults.itemShape(index, ApiKeyLocation.entries.size),
                            label = { Text(stringResource(location.label())) },
                        )
                    }
                }
            }
        }
    }
}

/** 网络（PRD 6）：跳过证书校验、超时覆盖与代理。 */
private fun LazyListScope.networkSection(
    network: EnvironmentNetwork,
    onChange: (EnvironmentNetwork) -> Unit,
) {
    sectionTitle("network", R.string.environment_network)
    item(key = "network/tls") {
        SwitchRow(
            title = stringResource(R.string.environment_skip_tls),
            description = stringResource(R.string.environment_skip_tls_hint),
            checked = network.skipTlsVerification,
            warning = network.skipTlsVerification,
        ) { onChange(network.copy(skipTlsVerification = it)) }
    }
    item(key = "network/timeout") { TimeoutOverride(network.timeoutSeconds, onChange = { onChange(network.copy(timeoutSeconds = it)) }) }
    item(key = "network/proxy") { ProxyChoiceEditor(network.proxy, onChange = { onChange(network.copy(proxy = it)) }) }
}

/** 超时：跟随全局设置（`null`）或覆盖为指定秒数。 */
@Composable
private fun TimeoutOverride(
    seconds: Long?,
    onChange: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(FieldSpacing)) {
        SwitchRow(stringResource(R.string.environment_timeout_follow), description = null, checked = seconds == null) { follow ->
            onChange(if (follow) null else AppSettings.DEFAULT_TIMEOUT_SECONDS)
        }
        seconds?.let {
            NumberField(
                value = it,
                onValueChange = { value -> value?.let(onChange) },
                label = stringResource(R.string.settings_timeout_seconds),
                range = AppSettings.TIMEOUT_RANGE_SECONDS,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 代理：跟随全局、直连或自定义。 */
@Composable
private fun ProxyChoiceEditor(
    proxy: ProxyChoice,
    onChange: (ProxyChoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    val choices = listOf(R.string.proxy_follow_global, R.string.proxy_direct, R.string.proxy_custom)
    val selected =
        when (proxy) {
            ProxyChoice.FollowGlobal -> FOLLOW_GLOBAL
            ProxyChoice.Direct -> DIRECT
            is ProxyChoice.Custom -> CUSTOM
        }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(FieldSpacing)) {
        Text(stringResource(R.string.settings_proxy), style = MaterialTheme.typography.bodyLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            choices.forEachIndexed { index, label ->
                SegmentedButton(
                    selected = selected == index,
                    onClick = {
                        onChange(
                            when (index) {
                                FOLLOW_GLOBAL -> ProxyChoice.FollowGlobal
                                DIRECT -> ProxyChoice.Direct
                                else -> proxy as? ProxyChoice.Custom ?: ProxyChoice.Custom(ProxyConfig(ProxyType.HTTP, host = "", port = 0))
                            },
                        )
                    },
                    shape = SegmentedButtonDefaults.itemShape(index, choices.size),
                    label = { Text(stringResource(label)) },
                )
            }
        }
        (proxy as? ProxyChoice.Custom)?.let { custom ->
            ProxyConfigEditor(custom.config, { onChange(ProxyChoice.Custom(it)) })
        }
    }
}

/** Cookie 罐（PRD 6）：查看、删除单个与清空，不能手动新增。 */
private fun LazyListScope.cookieSection(
    cookies: ImmutableList<Cookie>,
    onDelete: (Cookie) -> Unit,
    onClear: () -> Unit,
) {
    item(key = "cookies") {
        Row(Modifier.fillMaxWidth().padding(top = SectionSpacing), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.environment_cookies, cookies.size),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            TextButton(onClick = onClear, enabled = cookies.isNotEmpty()) { Text(stringResource(R.string.cookie_clear)) }
        }
    }
    cookies.groupBy { it.domain }.forEach { (domain, group) ->
        item(key = "cookies/$domain") { Text(domain, style = MaterialTheme.typography.labelLarge) }
        items(group, key = { "cookie/${it.domain}/${it.path}/${it.name}" }) { cookie ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${cookie.name}=${cookie.value}", style = MaterialTheme.typography.codeSmall, maxLines = 2)
                    Muted(
                        if (cookie.persistent) {
                            stringResource(R.string.cookie_expires, cookie.path, formatDateTime(Instant.ofEpochMilli(cookie.expiresAt)))
                        } else {
                            stringResource(R.string.cookie_session, cookie.path)
                        },
                    )
                }
                RemoveButton({ onDelete(cookie) })
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    description: String?,
    checked: Boolean,
    warning: Boolean = false,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            description?.let { Muted(it) }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SecurityScheme.describe(): String =
    when (this) {
        is SecurityScheme.ApiKey -> stringResource(R.string.auth_type_api_key)
        is SecurityScheme.Http -> "HTTP ${scheme.replaceFirstChar(Char::uppercase)}"
        is SecurityScheme.Other -> type
    }

private fun ApiKeyLocation.label(): Int =
    when (this) {
        ApiKeyLocation.HEADER -> R.string.doc_location_header
        ApiKeyLocation.QUERY -> R.string.doc_location_query
        ApiKeyLocation.COOKIE -> R.string.doc_location_cookie
    }

private val NoChoices = emptyList<String>().toImmutableList()
private val ScreenPadding = 16.dp
private val SectionSpacing = 16.dp
private val BottomPadding = 32.dp
private const val NAME_WEIGHT = 2f
private const val VALUE_WEIGHT = 3f
private const val FOLLOW_GLOBAL = 0
private const val DIRECT = 1
private const val CUSTOM = 2
