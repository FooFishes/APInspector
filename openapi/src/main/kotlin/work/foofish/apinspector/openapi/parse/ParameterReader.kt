package work.foofish.apinspector.openapi.parse

import work.foofish.apinspector.openapi.Parameter
import work.foofish.apinspector.openapi.ParameterLocation
import work.foofish.apinspector.openapi.ParameterSerialization
import work.foofish.apinspector.openapi.ParameterStyle
import work.foofish.apinspector.openapi.Resolvable

/** 读取 Parameter Object，并按规范补全 `style` / `explode` 默认值。 */
internal class ParameterReader(
    private val context: ParseContext,
    private val schemas: SchemaReader,
    private val content: ContentReader,
) {
    fun parameters(node: ObjectNode): List<Resolvable<Parameter>> =
        node.objects(Field.PARAMETERS).mapNotNull { context.resolve(it, ::parameter) }

    private fun parameter(node: ObjectNode): Parameter? {
        val name = node.string(Field.NAME)
        val location = location(node)
        if (name == null) node.warnMissing(Field.NAME)
        if (name == null || location == null) return null
        val mediaTypes = content.content(node)
        return Parameter(
            name = name,
            location = location,
            description = node.string(Field.DESCRIPTION),
            // 规范要求路径参数必须是必填的。
            required = location == ParameterLocation.PATH || node.boolean(Field.REQUIRED) == true,
            deprecated = node.boolean(Field.DEPRECATED) == true,
            // style / explode 只作用于以 schema 描述的参数；querystring 参数必须用 content 描述。
            serialization =
                if (location == ParameterLocation.QUERY_STRING ||
                    mediaTypes.isNotEmpty()
                ) {
                    null
                } else {
                    serialization(node, location)
                },
            schema = schemas.field(node, Field.SCHEMA),
            content = mediaTypes,
            example = node.element(Field.EXAMPLE),
            examples = content.examples(node),
        )
    }

    private fun location(node: ObjectNode): ParameterLocation? {
        val value = node.string(Field.IN)
        if (value == null) {
            node.warnMissing(Field.IN)
            return null
        }
        return PARAMETER_LOCATIONS[value] ?: run {
            node.warnInvalidValue(Field.IN, value)
            null
        }
    }

    private fun serialization(
        node: ObjectNode,
        location: ParameterLocation,
    ): ParameterSerialization {
        val style = style(node, location)
        val explodesByDefault = style == ParameterStyle.FORM || style == ParameterStyle.COOKIE
        return ParameterSerialization(style, explode = node.boolean(Field.EXPLODE) ?: explodesByDefault)
    }

    private fun style(
        node: ObjectNode,
        location: ParameterLocation,
    ): ParameterStyle {
        val value = node.string(Field.STYLE) ?: return defaultStyle(location)
        return PARAMETER_STYLES[value] ?: defaultStyle(location).also { node.warnInvalidValue(Field.STYLE, value) }
    }

    /** cookie 参数出于兼容默认仍为 `form`（3.2 建议显式使用 `cookie`）。 */
    private fun defaultStyle(location: ParameterLocation): ParameterStyle =
        when (location) {
            ParameterLocation.QUERY, ParameterLocation.QUERY_STRING, ParameterLocation.COOKIE -> ParameterStyle.FORM
            ParameterLocation.PATH, ParameterLocation.HEADER -> ParameterStyle.SIMPLE
        }
}
