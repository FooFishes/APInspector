package work.foofish.apinspector.request.body

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonPrimitive
import work.foofish.apinspector.openapi.OpenApiDocument
import work.foofish.apinspector.openapi.Schema
import work.foofish.apinspector.openapi.request.JsonMember
import work.foofish.apinspector.openapi.request.JsonNode
import work.foofish.apinspector.openapi.request.JsonTemplate
import work.foofish.apinspector.openapi.request.SchemaSampler
import work.foofish.apinspector.openapi.request.isBinary
import work.foofish.apinspector.operationdoc.TypeLabel
import work.foofish.apinspector.operationdoc.typeLabel

/** JSON 树中从根到某个节点的路径。 */
@Immutable
data class JsonPath(
    val steps: List<JsonStep>,
) {
    fun child(step: JsonStep): JsonPath = JsonPath(steps + step)

    /** 用作列表项的稳定键。 */
    val id: String get() = steps.joinToString(separator = "/", prefix = "$") { it.id }

    companion object {
        val ROOT = JsonPath(emptyList())
    }
}

sealed interface JsonStep {
    val id: String

    data class Key(
        val name: String,
    ) : JsonStep {
        override val id: String get() = "k:$name"
    }

    data class Index(
        val index: Int,
    ) : JsonStep {
        override val id: String get() = "i:$index"
    }
}

/** 可视化编辑中可选的值类型（PRD 4.3）。 */
enum class JsonValueType {
    STRING,
    NUMBER,
    BOOLEAN,
    NULL,
    OBJECT,
    ARRAY,

    /** 不带引号的 `{{name}}`。 */
    VARIABLE,
}

/** 节点对应的 schema 信息；schema 外的字段为 `null`。 */
data class FieldSchema(
    val type: TypeLabel?,
    val required: Boolean,
    val description: String?,
    /** enum 取值（文本形式）。 */
    val choices: ImmutableList<String>,
    /** 文件类型（`format: binary`）。 */
    val binary: Boolean,
) {
    companion object {
        /** [schema] 作为一个属性时的说明；[schema] 为 `$ref` 时优先使用引用处的描述。 */
        fun of(
            schema: Schema,
            required: Boolean,
            document: OpenApiDocument,
        ): FieldSchema {
            val definition = document.dereference(schema) as? Schema.Definition
            return FieldSchema(
                type = typeLabel(schema),
                required = required,
                description = (schema as? Schema.Reference)?.description ?: definition?.description,
                choices =
                    definition
                        ?.enumValues
                        .orEmpty()
                        .map { (it as? JsonPrimitive)?.content ?: it.toString() }
                        .toImmutableList(),
                binary = definition?.isBinary() == true,
            )
        }
    }
}

/** 可视化编辑的一行。 */
sealed interface JsonRow {
    val key: String
    val depth: Int

    /** 叶子值：字符串、数字、布尔、null 或变量。 */
    data class Value(
        val path: JsonPath,
        override val depth: Int,
        val label: JsonStep?,
        val type: JsonValueType,
        /** 字符串、数字的原文，或变量名。 */
        val text: String,
        val schema: FieldSchema?,
    ) : JsonRow {
        override val key: String get() = path.id
    }

    data class Container(
        val path: JsonPath,
        override val depth: Int,
        val label: JsonStep?,
        val type: JsonValueType,
        val size: Int,
        val expanded: Boolean,
        val schema: FieldSchema?,
        /** `oneOf` / `anyOf` 变体的名称；不是组合 schema 时为空。 */
        val variants: ImmutableList<String>,
        val variant: Int?,
    ) : JsonRow {
        override val key: String get() = path.id
    }

    /** schema 中声明但尚未填写的字段（"＋ 字段名"）。 */
    data class Missing(
        val parent: JsonPath,
        override val depth: Int,
        val name: String,
        val schema: FieldSchema,
    ) : JsonRow {
        override val key: String get() = parent.id + "/+" + name
    }

    /** 对象末尾的"添加字段"或数组末尾的"添加元素"。 */
    data class Add(
        val parent: JsonPath,
        override val depth: Int,
        val array: Boolean,
    ) : JsonRow {
        override val key: String get() = parent.id + "/+"
    }
}

/**
 * 按 schema 把 JSON 树展开为可视化编辑的行（PRD 4.3）。
 *
 * [collapsed] 中的容器只显示自身一行。组合 schema（`allOf` 合并；`oneOf` / `anyOf` 取与现有字段最吻合的变体）用于
 * 提示字段类型、必填与可选值。
 */
class JsonTreeBuilder(
    private val document: OpenApiDocument,
    private val collapsed: Set<JsonPath>,
) {
    private val rows = mutableListOf<JsonRow>()

    fun build(
        root: JsonNode,
        schema: Schema?,
    ): ImmutableList<JsonRow> {
        rows.clear()
        node(root, JsonPath.ROOT, depth = 0, label = null, schema = schema, field = schema?.let { fieldSchema(it, required = false) })
        return rows.toImmutableList()
    }

    private fun node(
        node: JsonNode,
        path: JsonPath,
        depth: Int,
        label: JsonStep?,
        schema: Schema?,
        field: FieldSchema?,
    ) {
        when (node) {
            is JsonNode.ObjectNode -> objectNode(node, path, depth, label, schema, field)
            is JsonNode.ArrayNode -> arrayNode(node, path, depth, label, schema, field)
            is JsonNode.StringNode -> rows += JsonRow.Value(path, depth, label, JsonValueType.STRING, node.value, field)
            is JsonNode.NumberNode -> rows += JsonRow.Value(path, depth, label, JsonValueType.NUMBER, node.literal, field)
            is JsonNode.BooleanNode -> rows += JsonRow.Value(path, depth, label, JsonValueType.BOOLEAN, node.value.toString(), field)
            JsonNode.NullNode -> rows += JsonRow.Value(path, depth, label, JsonValueType.NULL, "", field)
            is JsonNode.VariableNode -> rows += JsonRow.Value(path, depth, label, JsonValueType.VARIABLE, node.name, field)
        }
    }

    private fun objectNode(
        node: JsonNode.ObjectNode,
        path: JsonPath,
        depth: Int,
        label: JsonStep?,
        schema: Schema?,
        field: FieldSchema?,
    ) {
        val variants = variants(schema)
        val resolved = SchemaView.of(schema, document, node)
        val expanded = path !in collapsed
        rows +=
            JsonRow.Container(
                path = path,
                depth = depth,
                label = label,
                type = JsonValueType.OBJECT,
                size = node.members.size,
                expanded = expanded,
                schema = field,
                variants = variants.map { it.name }.toImmutableList(),
                variant = resolved.variant,
            )
        if (!expanded) return
        node.members.forEach { member ->
            val propertySchema = resolved.properties[member.key] ?: resolved.additionalProperties
            val childField = propertySchema?.let { fieldSchema(it, required = member.key in resolved.required) }
            node(member.value, path.child(JsonStep.Key(member.key)), depth + 1, JsonStep.Key(member.key), propertySchema, childField)
        }
        val present = node.members.mapTo(HashSet()) { it.key }
        resolved.properties
            .filterKeys { it !in present }
            .filterValues { (document.dereference(it) as? Schema.Definition)?.readOnly != true }
            .forEach { (name, property) ->
                rows +=
                    JsonRow.Missing(path, depth + 1, name, fieldSchema(property, required = name in resolved.required))
            }
        rows += JsonRow.Add(path, depth + 1, array = false)
    }

    private fun arrayNode(
        node: JsonNode.ArrayNode,
        path: JsonPath,
        depth: Int,
        label: JsonStep?,
        schema: Schema?,
        field: FieldSchema?,
    ) {
        val expanded = path !in collapsed
        rows +=
            JsonRow.Container(path, depth, label, JsonValueType.ARRAY, node.items.size, expanded, field, persistentListOf(), variant = null)
        if (!expanded) return
        val items = SchemaView.of(schema, document, node).items
        node.items.forEachIndexed { index, item ->
            node(
                item,
                path.child(JsonStep.Index(index)),
                depth + 1,
                JsonStep.Index(index),
                items,
                items?.let { fieldSchema(it, required = false) },
            )
        }
        rows += JsonRow.Add(path, depth + 1, array = true)
    }

    private fun variants(schema: Schema?): List<Variant> {
        val definition = schema?.let(document::dereference) as? Schema.Definition ?: return emptyList()
        return (definition.oneOf.ifEmpty { definition.anyOf }).mapIndexed {
            index,
            variant,
            ->
            Variant(variantName(variant, index), variant)
        }
    }

    private fun fieldSchema(
        schema: Schema,
        required: Boolean,
    ): FieldSchema = FieldSchema.of(schema, required, document)

    private data class Variant(
        val name: String,
        val schema: Schema,
    )
}

/** `oneOf` / `anyOf` 变体的显示名：引用名，否则为序号。 */
private fun variantName(
    schema: Schema,
    index: Int,
): String = (schema as? Schema.Reference)?.name ?: (schema as? Schema.Definition)?.title ?: "#${index + 1}"

/** 把组合 schema 合并为便于查询属性的视图。 */
private class SchemaView(
    val properties: Map<String, Schema>,
    val required: Set<String>,
    val additionalProperties: Schema?,
    val items: Schema?,
    /** 选中的 `oneOf` / `anyOf` 变体序号。 */
    val variant: Int?,
) {
    companion object {
        fun of(
            schema: Schema?,
            document: OpenApiDocument,
            node: JsonNode,
        ): SchemaView {
            val definition = schema?.let(document::dereference) as? Schema.Definition ?: return EMPTY
            val parts = mutableListOf(definition)
            definition.allOf.mapNotNullTo(parts) { document.dereference(it) as? Schema.Definition }
            val alternatives = definition.oneOf.ifEmpty { definition.anyOf }
            val variant = bestVariant(alternatives, document, node)
            variant?.let { alternatives[it] }?.let { document.dereference(it) as? Schema.Definition }?.let(parts::add)
            return SchemaView(
                properties = parts.fold(emptyMap()) { merged, part -> merged + part.properties },
                required = parts.flatMapTo(HashSet()) { it.required },
                additionalProperties = parts.firstNotNullOfOrNull { it.additionalProperties },
                items = parts.firstNotNullOfOrNull { it.items },
                variant = variant,
            )
        }

        /** 与现有字段重合最多的变体；没有变体时为 `null`，都不重合时取第一个。 */
        private fun bestVariant(
            alternatives: List<Schema>,
            document: OpenApiDocument,
            node: JsonNode,
        ): Int? {
            if (alternatives.isEmpty()) return null
            val keys = (node as? JsonNode.ObjectNode)?.members?.mapTo(HashSet()) { it.key }.orEmpty()
            return alternatives.indices.maxBy { index ->
                ((document.dereference(alternatives[index]) as? Schema.Definition)?.properties?.keys.orEmpty() intersect keys).size
            }
        }

        private val EMPTY = SchemaView(emptyMap(), emptySet(), null, null, null)
    }
}

/** 由 schema 生成节点（添加字段、数组元素或切换变体时使用）。 */
fun sampleNode(
    schema: Schema?,
    document: OpenApiDocument,
): JsonNode = schema?.let { JsonTemplate.fromElement(SchemaSampler(document).sample(it)) } ?: JsonNode.StringNode("")

/** 第 [index] 个 `oneOf` / `anyOf` 变体的示例。 */
fun variantSample(
    schema: Schema?,
    index: Int,
    document: OpenApiDocument,
): JsonNode? {
    val definition = schema?.let(document::dereference) as? Schema.Definition ?: return null
    return (definition.oneOf.ifEmpty { definition.anyOf }).getOrNull(index)?.let { sampleNode(it, document) }
}

/** [path] 处节点所对应的 schema（按现有内容选择组合 schema 的变体）。 */
fun schemaAt(
    root: JsonNode,
    rootSchema: Schema?,
    path: JsonPath,
    document: OpenApiDocument,
): Schema? {
    var node: JsonNode? = root
    var schema = rootSchema
    path.steps.forEach { step ->
        val current = node ?: return null
        val view = SchemaView.of(schema, document, current)
        schema =
            when (step) {
                is JsonStep.Key -> view.properties[step.name] ?: view.additionalProperties
                is JsonStep.Index -> view.items
            }
        node = current.child(step)
    }
    return schema
}

/** 该类型的初始值；变量使用 [variableName]。 */
fun emptyNode(
    type: JsonValueType,
    variableName: String,
): JsonNode =
    when (type) {
        JsonValueType.STRING -> JsonNode.StringNode("")
        JsonValueType.NUMBER -> JsonNode.NumberNode("0")
        JsonValueType.BOOLEAN -> JsonNode.BooleanNode(false)
        JsonValueType.NULL -> JsonNode.NullNode
        JsonValueType.OBJECT -> JsonNode.ObjectNode(emptyList())
        JsonValueType.ARRAY -> JsonNode.ArrayNode(emptyList())
        JsonValueType.VARIABLE -> JsonNode.VariableNode(variableName)
    }

/** 把 [path] 处的节点替换为 [replacement]。路径不存在时原样返回。 */
fun JsonNode.replace(
    path: JsonPath,
    replacement: JsonNode,
): JsonNode = update(path.steps) { replacement } ?: this

/** 删除 [path] 处的节点；根节点不能删除。 */
fun JsonNode.remove(path: JsonPath): JsonNode {
    val parentPath = JsonPath(path.steps.dropLast(1))
    val last = path.steps.lastOrNull() ?: return this
    return update(parentPath.steps) { parent ->
        when {
            parent is JsonNode.ObjectNode && last is JsonStep.Key -> {
                JsonNode.ObjectNode(parent.members.filterNot { it.key == last.name })
            }

            parent is JsonNode.ArrayNode && last is JsonStep.Index -> {
                JsonNode.ArrayNode(
                    parent.items.filterIndexed { index, _ ->
                        index !=
                            last.index
                    },
                )
            }

            else -> {
                parent
            }
        }
    } ?: this
}

/** 在 [path] 处的对象末尾添加字段（同名字段已存在时替换其值）或在数组末尾添加元素。 */
fun JsonNode.append(
    path: JsonPath,
    name: String?,
    value: JsonNode,
): JsonNode =
    update(path.steps) { container ->
        when (container) {
            is JsonNode.ObjectNode -> {
                val key = name ?: return@update container
                if (container.members.any { it.key == key }) {
                    JsonNode.ObjectNode(container.members.map { if (it.key == key) JsonMember(key, value) else it })
                } else {
                    JsonNode.ObjectNode(container.members + JsonMember(key, value))
                }
            }

            is JsonNode.ArrayNode -> {
                JsonNode.ArrayNode(container.items + value)
            }

            else -> {
                container
            }
        }
    } ?: this

/** 节点的值类型。 */
val JsonNode.valueType: JsonValueType
    get() =
        when (this) {
            is JsonNode.ObjectNode -> JsonValueType.OBJECT
            is JsonNode.ArrayNode -> JsonValueType.ARRAY
            is JsonNode.StringNode -> JsonValueType.STRING
            is JsonNode.NumberNode -> JsonValueType.NUMBER
            is JsonNode.BooleanNode -> JsonValueType.BOOLEAN
            JsonNode.NullNode -> JsonValueType.NULL
            is JsonNode.VariableNode -> JsonValueType.VARIABLE
        }

private fun JsonNode.child(step: JsonStep): JsonNode? =
    when {
        this is JsonNode.ObjectNode && step is JsonStep.Key -> members.lastOrNull { it.key == step.name }?.value
        this is JsonNode.ArrayNode && step is JsonStep.Index -> items.getOrNull(step.index)
        else -> null
    }

/** 沿 [steps] 找到节点并以 [transform] 替换；路径不存在时返回 `null`。 */
private fun JsonNode.update(
    steps: List<JsonStep>,
    transform: (JsonNode) -> JsonNode,
): JsonNode? {
    val step = steps.firstOrNull() ?: return transform(this)
    val rest = steps.drop(1)
    return when {
        this is JsonNode.ObjectNode && step is JsonStep.Key -> {
            val index = members.indexOfLast { it.key == step.name }
            members.getOrNull(index)?.value?.update(rest, transform)?.let { updated ->
                JsonNode.ObjectNode(members.toMutableList().apply { set(index, JsonMember(step.name, updated)) })
            }
        }

        this is JsonNode.ArrayNode && step is JsonStep.Index -> {
            items.getOrNull(step.index)?.update(rest, transform)?.let { updated ->
                JsonNode.ArrayNode(items.toMutableList().apply { set(step.index, updated) })
            }
        }

        else -> {
            null
        }
    }
}
