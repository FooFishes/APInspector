package work.foofish.apinspector.openapi.request

import kotlinx.serialization.Serializable
import work.foofish.apinspector.openapi.OpenApiDocument
import work.foofish.apinspector.openapi.Server

/**
 * 环境中的一个变量。
 *
 * @property candidates 编辑时提供的候选值（来自 server 变量的 `enum`）。
 */
@Serializable
public data class EnvironmentVariable(
    val name: String,
    val value: String,
    val candidates: List<String> = emptyList(),
)

/** 由某个 `servers` 条目生成的环境内容。 */
public data class EnvironmentTemplate(
    val name: String,
    val baseUrl: String,
    val variables: List<EnvironmentVariable>,
)

/**
 * 导入时每个 `servers` 条目生成一个环境（PRD 5）：server 变量 `{x}` 改写为 `{{x}}`，
 * 并生成同名环境变量（取其 `default`，`enum` 作为候选）。相对地址原样保留。
 *
 * 环境名依次取 server 的 `name`、`description`、地址；重名时追加序号。
 */
public fun OpenApiDocument.environmentTemplates(): List<EnvironmentTemplate> {
    val used = HashMap<String, Int>()
    return servers.map { server ->
        val base = server.displayName()
        val count = used.merge(base, 1, Int::plus) ?: 1
        EnvironmentTemplate(
            name = if (count == 1) base else "$base ($count)",
            baseUrl = SERVER_VARIABLE.replace(server.url) { Variables.reference(it.groupValues[1]) },
            variables = server.variables.map { (name, variable) -> EnvironmentVariable(name, variable.defaultValue, variable.enumValues) },
        )
    }
}

private fun Server.displayName(): String = name?.takeIf { it.isNotBlank() } ?: description?.takeIf { it.isNotBlank() } ?: url

/** Server URL 中的 `{name}`；不匹配已经是 `{{name}}` 的写法。 */
private val SERVER_VARIABLE = Regex("""(?<!\{)\{([^\{\}]+)\}(?!\})""")
