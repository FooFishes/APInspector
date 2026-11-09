package work.foofish.apinspector.openapi.parse

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull
import work.foofish.apinspector.openapi.JsonType
import work.foofish.apinspector.openapi.ParseWarning
import java.math.BigDecimal

/**
 * 文档中的一个对象节点及其位置，提供按类型读取字段的方法。
 *
 * 读取是宽松的：字段缺失或显式为 `null` 时返回 `null`；类型不符时记录 [ParseWarning.UnexpectedType] 并返回 `null`。
 * 标量字段按内容读取，因此 YAML 中未加引号的 `version: 1.0` 也能读成字符串。
 *
 * 每个方法对应一种字段类型，拆分只会让调用方在多个类之间来回查找，因此不受函数数量限制。
 */
@Suppress("TooManyFunctions")
internal class ObjectNode(
    val json: JsonObject,
    val pointer: JsonPointer,
    private val context: ParseContext,
) {
    operator fun contains(key: String): Boolean = key in json

    fun reference(): String? = string(Field.REF)

    fun string(key: String): String? = primitive(key, JsonType.STRING)?.content

    fun boolean(key: String): Boolean? = primitive(key, JsonType.BOOLEAN)?.let { it.booleanOrNull ?: mismatch(key, JsonType.BOOLEAN) }

    fun long(key: String): Long? = primitive(key, JsonType.INTEGER)?.let { it.longOrNull ?: mismatch(key, JsonType.INTEGER) }

    fun decimal(key: String): BigDecimal? =
        primitive(key, JsonType.NUMBER)?.let { it.content.toBigDecimalOrNull() ?: mismatch(key, JsonType.NUMBER) }

    /** 原样返回字段值；显式的 `null` 也会返回 [JsonNull]，用于示例值等允许为 `null` 的字段。 */
    fun element(key: String): JsonElement? = json[key]

    fun obj(key: String): ObjectNode? =
        when (val value = json[key]) {
            null, JsonNull -> null
            is JsonObject -> ObjectNode(value, pointer.child(key), context)
            else -> mismatch(key, JsonType.OBJECT)
        }

    /** 把字段值当作"名称 → 对象"的映射读取，跳过不是对象的条目。 */
    fun children(
        key: String,
        allowsExtensions: Boolean = false,
    ): List<Pair<String, ObjectNode>> = obj(key)?.children(allowsExtensions).orEmpty()

    /**
     * 把本节点当作"名称 → 对象"的映射读取，跳过不是对象的条目。
     *
     * [allowsExtensions] 用于 Paths、Responses 这类同时允许 `x-` 规范扩展字段的对象：扩展字段不是映射条目，直接跳过。
     */
    fun children(allowsExtensions: Boolean = false): List<Pair<String, ObjectNode>> =
        json.mapNotNull { (name, value) ->
            when {
                allowsExtensions && name.startsWith(EXTENSION_PREFIX) -> null
                value is JsonObject -> name to ObjectNode(value, pointer.child(name), context)
                else -> unexpected(pointer.child(name), JsonType.OBJECT)
            }
        }

    /** 数组中的元素及其位置。 */
    fun array(key: String): List<Pair<JsonElement, JsonPointer>> =
        when (val value = json[key]) {
            null, JsonNull -> null
            is JsonArray -> value.mapIndexed { index, element -> element to pointer.child(key).child(index) }
            else -> mismatch(key, JsonType.ARRAY)
        }.orEmpty()

    fun objects(key: String): List<ObjectNode> =
        array(key).mapNotNull { (element, elementPointer) ->
            when (element) {
                is JsonObject -> ObjectNode(element, elementPointer, context)
                else -> unexpected(elementPointer, JsonType.OBJECT)
            }
        }

    fun strings(key: String): List<String> =
        array(key).mapNotNull { (element, elementPointer) ->
            when (element) {
                is JsonPrimitive if element != JsonNull -> element.content
                else -> unexpected(elementPointer, JsonType.STRING)
            }
        }

    fun warnInvalidValue(
        key: String,
        value: String,
    ) {
        warnInvalidValue(pointer.child(key), value)
    }

    fun warnInvalidValue(
        at: JsonPointer,
        value: String,
    ) {
        context.warn(ParseWarning.InvalidValue(at.toFragment(), value))
    }

    fun warnMissing(key: String) {
        context.warn(ParseWarning.MissingField(pointer.toFragment(), key))
    }

    private fun primitive(
        key: String,
        expected: JsonType,
    ): JsonPrimitive? =
        when (val value = json[key]) {
            null, JsonNull -> null
            is JsonPrimitive -> value
            else -> mismatch(key, expected)
        }

    private fun mismatch(
        key: String,
        expected: JsonType,
    ): Nothing? = unexpected(pointer.child(key), expected)

    private fun unexpected(
        at: JsonPointer,
        expected: JsonType,
    ): Nothing? {
        context.warn(ParseWarning.UnexpectedType(at.toFragment(), expected))
        return null
    }

    private companion object {
        const val EXTENSION_PREFIX = "x-"
    }
}
