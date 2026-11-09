package work.foofish.apinspector.openapi.parse

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import work.foofish.apinspector.openapi.JsonType
import work.foofish.apinspector.openapi.OpenApiVersion
import work.foofish.apinspector.openapi.ParseWarning
import work.foofish.apinspector.openapi.Schema
import work.foofish.apinspector.openapi.UnresolvedReason

/**
 * 读取 Schema Object，并登记所有内部引用的目标。
 *
 * 引用不内联展开：遇到 `$ref` 时只记下目标，待 [registry] 时逐个读取（目标中的引用继续入队）。
 * 每个目标只读一次，所以循环引用自然终止。
 */
internal class SchemaReader(
    private val context: ParseContext,
) {
    private val registry = LinkedHashMap<String, Schema>()
    private val pending = ArrayDeque<RefTarget.Found>()
    private val enqueued = mutableSetOf<String>()

    /** 读取一个 Schema；既不是对象也不是布尔值时记录警告并返回 `null`。 */
    fun read(
        element: JsonElement,
        pointer: JsonPointer,
    ): Schema? =
        when (element) {
            is JsonObject -> {
                readObject(ObjectNode(element, pointer, context))
            }

            is JsonPrimitive if !element.isString && element.booleanOrNull != null -> {
                booleanSchema(element.booleanOrNull == true)
            }

            else -> {
                context.warn(ParseWarning.UnexpectedType(pointer.toFragment(), JsonType.OBJECT))
                null
            }
        }

    fun field(
        node: ObjectNode,
        key: String,
    ): Schema? = node.element(key)?.takeUnless { it == JsonNull }?.let { read(it, node.pointer.child(key)) }

    /** 读取所有待处理的引用目标，返回完整的登记表；应在文档其余部分读完后调用。 */
    fun registry(): Map<String, Schema> {
        while (pending.isNotEmpty()) {
            val target = pending.removeFirst()
            val ref = target.pointer.toFragment()
            registry[ref] = read(target.element, target.pointer) ?: unresolved(target.pointer, ref, UnresolvedReason.INVALID_TARGET)
        }
        return registry.toMap()
    }

    private fun readObject(node: ObjectNode): Schema = node.reference()?.let { reference(node, it) } ?: definition(node)

    private fun reference(
        node: ObjectNode,
        ref: String,
    ): Schema =
        when (val target = context.locate(ref)) {
            is RefTarget.Missing -> {
                unresolved(node.pointer.child(Field.REF), ref, target.reason)
            }

            is RefTarget.Found -> {
                val canonical = target.pointer.toFragment()
                if (enqueued.add(canonical)) pending += target
                Schema.Reference(
                    ref = canonical,
                    name =
                        target.pointer.tokens
                            .lastOrNull()
                            .orEmpty(),
                    // 3.0 中 $ref 的兄弟字段一律忽略；3.1 起 Schema 采用 JSON Schema 2020-12，允许覆盖 description。
                    description = node.string(Field.DESCRIPTION).takeIf { context.version >= OpenApiVersion.V3_1 },
                )
            }
        }

    private fun unresolved(
        at: JsonPointer,
        ref: String,
        reason: UnresolvedReason,
    ): Schema.Unresolved {
        context.warn(ParseWarning.UnresolvedReference(at.toFragment(), ref, reason))
        return Schema.Unresolved(ref, reason)
    }

    private fun definition(node: ObjectNode): Schema.Definition =
        Schema.Definition(
            title = node.string(Field.TITLE),
            description = node.string(Field.DESCRIPTION),
            types = node.schemaTypes(),
            format = node.string(Field.FORMAT),
            properties = properties(node),
            required = node.strings(Field.REQUIRED).toSet(),
            additionalProperties = field(node, Field.ADDITIONAL_PROPERTIES),
            items = field(node, Field.ITEMS),
            allOf = list(node, Field.ALL_OF),
            oneOf = list(node, Field.ONE_OF),
            anyOf = list(node, Field.ANY_OF),
            not = field(node, Field.NOT),
            enumValues = node.enumValues(),
            defaultValue = node.element(Field.DEFAULT),
            examples = node.schemaExamples(),
            minimum = node.numericBound(Field.MINIMUM, Field.EXCLUSIVE_MINIMUM),
            maximum = node.numericBound(Field.MAXIMUM, Field.EXCLUSIVE_MAXIMUM),
            minLength = node.long(Field.MIN_LENGTH),
            maxLength = node.long(Field.MAX_LENGTH),
            pattern = node.string(Field.PATTERN),
            minItems = node.long(Field.MIN_ITEMS),
            maxItems = node.long(Field.MAX_ITEMS),
            uniqueItems = node.boolean(Field.UNIQUE_ITEMS) == true,
            readOnly = node.boolean(Field.READ_ONLY) == true,
            writeOnly = node.boolean(Field.WRITE_ONLY) == true,
            deprecated = node.boolean(Field.DEPRECATED) == true,
        )

    private fun properties(node: ObjectNode): Map<String, Schema> {
        val properties = node.obj(Field.PROPERTIES) ?: return emptyMap()
        return properties.json.entries
            .mapNotNull { (name, element) -> read(element, properties.pointer.child(name))?.let { name to it } }
            .toMap()
    }

    private fun list(
        node: ObjectNode,
        key: String,
    ): List<Schema> = node.array(key).mapNotNull { (element, pointer) -> read(element, pointer) }

    private fun booleanSchema(accepts: Boolean): Schema.Definition =
        if (accepts) Schema.Definition() else Schema.Definition(not = Schema.Definition())
}
