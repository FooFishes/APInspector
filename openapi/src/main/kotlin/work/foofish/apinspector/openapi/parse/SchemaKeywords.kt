package work.foofish.apinspector.openapi.parse

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import work.foofish.apinspector.openapi.NumericBound
import work.foofish.apinspector.openapi.SchemaType

// Schema 关键字中 OpenAPI 3.0 与 3.1 写法不同的部分，在这里归一化。

/** 3.1 的 `type` 可以是数组；3.0 的 `nullable: true` 只在声明了类型时才有意义。 */
internal fun ObjectNode.schemaTypes(): Set<SchemaType> {
    val typePointer = pointer.child(Field.TYPE)
    val declared =
        when (val type = element(Field.TYPE)) {
            null, JsonNull -> emptyList()
            is JsonArray -> type.mapIndexedNotNull { index, element -> schemaType(element, typePointer.child(index)) }
            else -> listOfNotNull(schemaType(type, typePointer))
        }
    val nullable = boolean(Field.NULLABLE) == true && declared.isNotEmpty()
    return if (nullable) declared.toSet() + SchemaType.NULL else declared.toSet()
}

private fun ObjectNode.schemaType(
    element: JsonElement,
    at: JsonPointer,
): SchemaType? {
    val type = (element as? JsonPrimitive)?.content?.let(SCHEMA_TYPES::get)
    if (type == null) warnInvalidValue(at, element.toString())
    return type
}

/** `const` 是只有一个取值的 `enum`。 */
internal fun ObjectNode.enumValues(): List<JsonElement> {
    val values = array(Field.ENUM).map { (element, _) -> element }
    return values.ifEmpty { listOfNotNull(element(Field.CONST)) }
}

/** 3.0 的 `example` 与 3.1 的 `examples` 数组合并。 */
internal fun ObjectNode.schemaExamples(): List<JsonElement> =
    buildList {
        element(Field.EXAMPLE)?.let(::add)
        array(Field.EXAMPLES).forEach { (element, _) -> add(element) }
    }

/**
 * 3.0：`exclusiveMinimum: true` 修饰 `minimum`；3.1：`exclusiveMinimum` 本身就是开区间的界。
 * 两种写法都接受，与版本无关；3.1 写法同时给出两者时以开区间为准。
 */
internal fun ObjectNode.numericBound(
    inclusiveKey: String,
    exclusiveKey: String,
): NumericBound? {
    val exclusiveFlag = (element(exclusiveKey) as? JsonPrimitive)?.booleanOrNull
    val inclusive = decimal(inclusiveKey)
    return if (exclusiveFlag != null) {
        inclusive?.let { NumericBound(it, exclusive = exclusiveFlag) }
    } else {
        decimal(exclusiveKey)?.let { NumericBound(it, exclusive = true) } ?: inclusive?.let { NumericBound(it, exclusive = false) }
    }
}
