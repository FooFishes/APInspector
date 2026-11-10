package work.foofish.apinspector.openapi.request

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import work.foofish.apinspector.openapi.OpenApiDocument
import work.foofish.apinspector.openapi.Schema
import work.foofish.apinspector.openapi.SchemaType

/**
 * 按 schema 生成示例值，作为 Body 的文档默认值（PRD 4.1）。
 *
 * 优先使用 schema 自带的示例、默认值与枚举值；否则按类型生成"零值"：对象列出全部非只读属性，数组含一个元素。
 * 循环引用在第二次进入同一个引用时停止：该处为 `null`，数组中则不生成元素。
 */
public class SchemaSampler(
    private val document: OpenApiDocument,
) {
    public fun sample(schema: Schema): JsonElement = sample(schema, visiting = emptySet())

    private fun sample(
        schema: Schema,
        visiting: Set<String>,
    ): JsonElement =
        when (schema) {
            is Schema.Reference -> {
                if (schema.ref in visiting) JsonNull else sample(document.schemas.getValue(schema.ref), visiting + schema.ref)
            }

            is Schema.Unresolved -> {
                JsonNull
            }

            is Schema.Definition -> {
                schema.examples.firstOrNull()
                    ?: schema.defaultValue
                    ?: schema.enumValues.firstOrNull()
                    ?: composed(schema, visiting)
                    ?: typed(schema, visiting)
            }
        }

    /** `allOf` 合并各部分的对象属性；`oneOf` / `anyOf` 取第一个变体。 */
    private fun composed(
        schema: Schema.Definition,
        visiting: Set<String>,
    ): JsonElement? {
        if (schema.allOf.isNotEmpty()) {
            val parts = schema.allOf.map { sample(it, visiting) } + typed(schema, visiting)
            val objects = parts.filterIsInstance<JsonObject>()
            return if (objects.isEmpty()) {
                parts.firstOrNull { it != JsonNull }
            } else {
                JsonObject(
                    objects.fold(emptyMap()) { merged, part ->
                        merged +
                            part
                    },
                )
            }
        }
        return (schema.oneOf.firstOrNull() ?: schema.anyOf.firstOrNull())?.let { sample(it, visiting) }
    }

    private fun typed(
        schema: Schema.Definition,
        visiting: Set<String>,
    ): JsonElement {
        val types = schema.types - SchemaType.NULL
        return when {
            schema.isObject() || (schema.additionalProperties != null && types.isEmpty()) -> {
                JsonObject(
                    schema.properties
                        .filterValues { property -> (document.dereference(property) as? Schema.Definition)?.readOnly != true }
                        .mapValues { (_, property) -> sample(property, visiting) },
                )
            }

            SchemaType.ARRAY in types -> {
                JsonArray(schema.items?.let { items -> listOf(sample(items, visiting)).filter { it != JsonNull } }.orEmpty())
            }

            SchemaType.STRING in types -> {
                JsonPrimitive("")
            }

            SchemaType.INTEGER in types || SchemaType.NUMBER in types -> {
                JsonPrimitive(0)
            }

            SchemaType.BOOLEAN in types -> {
                JsonPrimitive(false)
            }

            else -> {
                JsonNull
            }
        }
    }
}
