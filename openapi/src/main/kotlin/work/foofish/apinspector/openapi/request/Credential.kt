package work.foofish.apinspector.openapi.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import work.foofish.apinspector.openapi.ApiKeyLocation
import work.foofish.apinspector.openapi.SecurityRequirement
import work.foofish.apinspector.openapi.SecurityScheme

/**
 * 环境中为某个 `securitySchemes` 方案填写的凭证（PRD 5）。值中可以引用 `{{变量}}`。
 */
@Serializable
public sealed interface Credential {
    /** 是否已填写到可以注入的程度。 */
    public val isFilled: Boolean

    /** `http bearer`，以及手动填写 Access Token 的 `oauth2` / `openIdConnect`。 */
    @Serializable
    @SerialName("bearer")
    public data class Bearer(
        val token: String,
    ) : Credential {
        override val isFilled: Boolean get() = token.isNotBlank()
    }

    @Serializable
    @SerialName("basic")
    public data class Basic(
        val username: String,
        val password: String,
    ) : Credential {
        override val isFilled: Boolean get() = username.isNotBlank()
    }

    /** 名称与位置默认取文档声明，可以修改。 */
    @Serializable
    @SerialName("apiKey")
    public data class ApiKey(
        val value: String,
        val name: String,
        val location: ApiKeyLocation,
    ) : Credential {
        override val isFilled: Boolean get() = value.isNotBlank() && name.isNotBlank()
    }
}

/** 方案对应的空白凭证；不支持的方案（如 `http digest`、`mutualTLS`）返回 `null`。 */
public fun SecurityScheme.blankCredential(): Credential? =
    when (this) {
        is SecurityScheme.ApiKey -> {
            Credential.ApiKey(value = "", name = name, location = location)
        }

        is SecurityScheme.Http -> {
            when (scheme) {
                BEARER -> Credential.Bearer(token = "")
                BASIC -> Credential.Basic(username = "", password = "")
                else -> null
            }
        }

        is SecurityScheme.Other -> {
            if (type in TOKEN_TYPES) Credential.Bearer(token = "") else null
        }
    }

/** 凭证是否适用于该方案：方案类型变化后（如刷新文档）不再适用的凭证会被忽略。 */
public fun SecurityScheme.accepts(credential: Credential): Boolean =
    when (val blank = blankCredential()) {
        null -> false
        else -> blank::class == credential::class
    }

/**
 * 按接口的 `security` 顺序，选第一组"所有方案都已填凭证"的选项（PRD 5）。
 *
 * 接口无需鉴权、只有匿名选项 `{}`、或没有任何一组凭证齐全时，返回空列表，即不注入。
 */
public fun selectCredentials(
    security: List<SecurityRequirement>,
    schemes: Map<String, SecurityScheme>,
    credentials: Map<String, Credential>,
): List<Credential> =
    security
        .asSequence()
        .filter { it.schemes.isNotEmpty() }
        .map { requirement ->
            requirement.schemes.keys.map { name ->
                credentials[name]?.takeIf { credential -> credential.isFilled && schemes[name]?.accepts(credential) == true }
            }
        }.firstOrNull { candidates -> candidates.all { it != null } }
        ?.filterNotNull()
        .orEmpty()

private const val BEARER = "bearer"
private const val BASIC = "basic"
private val TOKEN_TYPES = setOf("oauth2", "openIdConnect")
