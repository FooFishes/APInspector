package work.foofish.apinspector.environment.data

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import okhttp3.Cookie
import work.foofish.apinspector.core.db.StorageJson
import work.foofish.apinspector.environment.Environment
import work.foofish.apinspector.environment.EnvironmentId
import work.foofish.apinspector.environment.EnvironmentNetwork
import work.foofish.apinspector.openapi.request.Credential
import work.foofish.apinspector.openapi.request.EnvironmentTemplate
import work.foofish.apinspector.openapi.request.EnvironmentVariable
import work.foofish.apinspector.spec.SpecId
import java.util.UUID

private val VariablesSerializer = ListSerializer(EnvironmentVariable.serializer())
private val CredentialsSerializer = MapSerializer(String.serializer(), Credential.serializer())

internal fun EnvironmentEntity.toEnvironment(): Environment =
    Environment(
        id = EnvironmentId(id),
        specId = SpecId(specId),
        name = name,
        baseUrl = baseUrl,
        variables = StorageJson.decodeFromString(VariablesSerializer, variables),
        credentials = StorageJson.decodeFromString(CredentialsSerializer, credentials),
        network = StorageJson.decodeFromString(EnvironmentNetwork.serializer(), network),
    )

internal fun Environment.toEntity(position: Int): EnvironmentEntity =
    EnvironmentEntity(
        id = id.value,
        specId = specId.value,
        name = name,
        baseUrl = baseUrl,
        variables = StorageJson.encodeToString(VariablesSerializer, variables),
        credentials = StorageJson.encodeToString(CredentialsSerializer, credentials),
        network = StorageJson.encodeToString(EnvironmentNetwork.serializer(), network),
        position = position,
    )

/** 由 `servers` 生成的新环境：凭证为空，网络设置取默认。 */
fun EnvironmentTemplate.toEntity(
    specId: SpecId,
    position: Int,
): EnvironmentEntity =
    Environment(
        id = EnvironmentId(UUID.randomUUID().toString()),
        specId = specId,
        name = name,
        baseUrl = baseUrl,
        variables = variables,
        credentials = emptyMap(),
        network = EnvironmentNetwork(),
    ).toEntity(position)

internal fun CookieEntity.toCookie(): Cookie =
    Cookie
        .Builder()
        .name(name)
        .value(value)
        .expiresAt(expiresAt)
        .path(path)
        .apply {
            if (hostOnly) hostOnlyDomain(domain) else domain(domain)
            if (secure) secure()
            if (httpOnly) httpOnly()
        }.build()

internal fun Cookie.toEntity(environmentId: EnvironmentId): CookieEntity =
    CookieEntity(
        environmentId = environmentId.value,
        name = name,
        value = value,
        domain = domain,
        path = path,
        expiresAt = expiresAt,
        secure = secure,
        httpOnly = httpOnly,
        hostOnly = hostOnly,
        persistent = persistent,
    )
