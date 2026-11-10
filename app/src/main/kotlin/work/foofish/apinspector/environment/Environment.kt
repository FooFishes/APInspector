package work.foofish.apinspector.environment

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import work.foofish.apinspector.openapi.request.Credential
import work.foofish.apinspector.openapi.request.EnvironmentVariable
import work.foofish.apinspector.openapi.request.RequestEnvironment
import work.foofish.apinspector.openapi.request.Variables
import work.foofish.apinspector.openapi.request.isAbsoluteHttpUrl
import work.foofish.apinspector.settings.ProxyConfig
import work.foofish.apinspector.spec.SpecId
import java.time.Duration

/** 环境的标识。 */
@JvmInline
@Serializable
value class EnvironmentId(
    val value: String,
)

/**
 * 环境（PRD 5）：baseUrl、变量、鉴权凭证与网络设置。
 *
 * @property credentials 文档 `securitySchemes` 中的方案名 → 凭证。
 */
data class Environment(
    val id: EnvironmentId,
    val specId: SpecId,
    val name: String,
    val baseUrl: String,
    val variables: List<EnvironmentVariable>,
    val credentials: Map<String, Credential>,
    val network: EnvironmentNetwork,
) {
    /** 名称为空的变量不参与替换；同名变量以后出现的为准。 */
    val variableValues: Map<String, String> get() = variables.filter { it.name.isNotEmpty() }.associate { it.name to it.value }

    /** 替换变量后的 baseUrl 不是 http / https 绝对地址（PRD 5"baseUrl 不完整"）。 */
    val isBaseUrlIncomplete: Boolean
        get() = !isAbsoluteHttpUrl(Variables.resolve(baseUrl, variableValues))

    fun toRequestEnvironment(): RequestEnvironment = RequestEnvironment(baseUrl, variableValues, credentials)
}

/** 环境级的网络设置（PRD 6）。 */
@Serializable
data class EnvironmentNetwork(
    /** 开启时界面以警示色标识。 */
    val skipTlsVerification: Boolean = false,
    /** 覆盖全局超时；`null` 为跟随全局。 */
    val timeoutSeconds: Long? = null,
    val proxy: ProxyChoice = ProxyChoice.FollowGlobal,
) {
    val timeout: Duration? get() = timeoutSeconds?.let(Duration::ofSeconds)
}

/** 环境的代理选择。 */
@Serializable
sealed interface ProxyChoice {
    @Serializable
    @SerialName("global")
    data object FollowGlobal : ProxyChoice

    @Serializable
    @SerialName("direct")
    data object Direct : ProxyChoice

    @Serializable
    @SerialName("custom")
    data class Custom(
        val config: ProxyConfig,
    ) : ProxyChoice
}
