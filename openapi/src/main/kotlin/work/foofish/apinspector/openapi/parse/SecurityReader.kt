package work.foofish.apinspector.openapi.parse

import work.foofish.apinspector.openapi.Resolvable
import work.foofish.apinspector.openapi.SecurityRequirement
import work.foofish.apinspector.openapi.SecurityScheme
import java.util.Locale

/** 读取鉴权方案与鉴权要求。 */
internal class SecurityReader(
    private val context: ParseContext,
) {
    fun schemes(components: ObjectNode?): Map<String, Resolvable<SecurityScheme>> =
        resolveAll(components?.children(Field.SECURITY_SCHEMES).orEmpty()) { context.resolve(it, ::scheme) }

    /** 读取 `security` 字段；未声明时返回 `null`，以区分"继承顶层"与"声明为无需鉴权（`[]`）"。 */
    fun requirements(node: ObjectNode): List<SecurityRequirement>? {
        if (Field.SECURITY !in node) return null
        return node.objects(Field.SECURITY).map { requirement ->
            SecurityRequirement(requirement.json.keys.associateWith(requirement::strings))
        }
    }

    private fun scheme(node: ObjectNode): SecurityScheme? {
        val type = node.string(Field.TYPE)
        val description = node.string(Field.DESCRIPTION)
        val deprecated = node.boolean(Field.DEPRECATED) == true
        return when (type) {
            null -> null.also { node.warnMissing(Field.TYPE) }
            SecuritySchemeType.API_KEY -> apiKey(node, description, deprecated)
            SecuritySchemeType.HTTP -> http(node, description, deprecated)
            in SecuritySchemeType.UNSUPPORTED -> SecurityScheme.Other(type, description, deprecated)
            else -> null.also { node.warnInvalidValue(Field.TYPE, type) }
        }
    }

    private fun apiKey(
        node: ObjectNode,
        description: String?,
        deprecated: Boolean,
    ): SecurityScheme.ApiKey? {
        val name = node.string(Field.NAME) ?: return null.also { node.warnMissing(Field.NAME) }
        val locationValue = node.string(Field.IN) ?: return null.also { node.warnMissing(Field.IN) }
        val location = API_KEY_LOCATIONS[locationValue] ?: return null.also { node.warnInvalidValue(Field.IN, locationValue) }
        return SecurityScheme.ApiKey(name, location, description, deprecated)
    }

    private fun http(
        node: ObjectNode,
        description: String?,
        deprecated: Boolean,
    ): SecurityScheme.Http? {
        val scheme = node.string(Field.SCHEME) ?: return null.also { node.warnMissing(Field.SCHEME) }
        return SecurityScheme.Http(scheme.lowercase(Locale.ROOT), node.string(Field.BEARER_FORMAT), description, deprecated)
    }
}
