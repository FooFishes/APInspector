package work.foofish.apinspector.openapi.parse

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.snakeyaml.engine.v2.api.LoadSettings
import org.snakeyaml.engine.v2.api.lowlevel.Compose
import org.snakeyaml.engine.v2.exceptions.MarkedYamlEngineException
import org.snakeyaml.engine.v2.exceptions.YamlEngineException
import org.snakeyaml.engine.v2.nodes.AnchorNode
import org.snakeyaml.engine.v2.nodes.MappingNode
import org.snakeyaml.engine.v2.nodes.Node
import org.snakeyaml.engine.v2.nodes.ScalarNode
import org.snakeyaml.engine.v2.nodes.SequenceNode
import org.snakeyaml.engine.v2.nodes.Tag
import org.snakeyaml.engine.v2.schema.CoreSchema
import work.foofish.apinspector.openapi.OpenApiParseError
import work.foofish.apinspector.openapi.SourceFormat
import work.foofish.apinspector.openapi.TextPosition
import java.math.BigInteger
import java.util.IdentityHashMap

/**
 * 把 JSON 或 YAML 源文本读成统一的 [JsonElement] 树。
 *
 * YAML 按 1.2 Core Schema 解析，并且基于节点树而不是 Java 对象转换：
 * 带引号的 `"123"` 保持为字符串，`1.10` 保持原有精度。
 */
internal object SourceReader {
    private const val BYTE_ORDER_MARK = ""
    private const val JSON_OBJECT_START = '{'

    /** 远大于常见的大型文档（数 MB），同时避免误导入超大文件耗尽内存。 */
    private const val MAX_YAML_CODE_POINTS = 64 * 1024 * 1024

    fun read(source: String): JsonElement {
        val text = source.removePrefix(BYTE_ORDER_MARK)
        return if (text.trimStart().startsWith(JSON_OBJECT_START)) readJson(text) else readYaml(text)
    }

    private fun readJson(text: String): JsonElement =
        try {
            Json.parseToJsonElement(text)
        } catch (e: SerializationException) {
            throw ParseFailure(OpenApiParseError.Syntax(SourceFormat.JSON, e.message.orEmpty(), position = null), e)
        }

    private fun readYaml(text: String): JsonElement {
        val settings =
            LoadSettings
                .builder()
                .setSchema(CoreSchema())
                .setCodePointLimit(MAX_YAML_CODE_POINTS)
                .build()
        val root =
            try {
                Compose(settings).composeString(text)
            } catch (e: YamlEngineException) {
                val position = (e as? MarkedYamlEngineException)?.problemMark?.map { TextPosition(it.line + 1, it.column + 1) }
                throw ParseFailure(OpenApiParseError.Syntax(SourceFormat.YAML, e.message.orEmpty(), position?.orElse(null)), e)
            }
        return root.map(YamlConverter()::convert).orElse(JsonNull)
    }
}

/** 把 YAML 节点树转换为 [JsonElement]；同一锚点被多处引用时共享同一个转换结果。 */
private class YamlConverter {
    private val converted = IdentityHashMap<Node, JsonElement>()
    private val inProgress = IdentityHashMap<Node, Unit>()

    fun convert(node: Node): JsonElement {
        converted[node]?.let { return it }
        if (inProgress.put(node, Unit) != null) throw ParseFailure(OpenApiParseError.RecursiveAlias(node.position()))
        val element =
            when (node) {
                is ScalarNode -> scalar(node)
                is MappingNode -> mapping(node)
                is SequenceNode -> JsonArray(node.value.map(::convert))
                is AnchorNode -> convert(node.realNode)
                else -> error("snakeyaml-engine 产生了未知的节点类型：${node.javaClass.name}")
            }
        inProgress.remove(node)
        converted[node] = element
        return element
    }

    private fun mapping(node: MappingNode): JsonObject {
        val entries = LinkedHashMap<String, JsonElement>(node.value.size)
        for (tuple in node.value) {
            // snakeyaml-engine 默认拒绝非标量键（作为语法错误报告），这里只会遇到标量。
            val keyNode = tuple.keyNode as? ScalarNode ?: error("snakeyaml-engine 未拒绝非标量键：${tuple.keyNode}")
            val key = keyNode.value
            if (key in entries) throw ParseFailure(OpenApiParseError.DuplicateKey(key, keyNode.position()))
            entries[key] = convert(tuple.valueNode)
        }
        return JsonObject(entries)
    }

    private fun scalar(node: ScalarNode): JsonElement {
        val text = node.value
        return when (node.tag) {
            Tag.NULL -> JsonNull

            // Core Schema 的布尔值为 true / True / TRUE 及对应的 false。
            Tag.BOOL -> JsonPrimitive(text.lowercase().toBooleanStrict())

            Tag.INT -> integer(text)?.let(::JsonPrimitive)

            // .inf / .nan 无法用 JSON 数值表示，退化为字符串。
            Tag.FLOAT -> text.toBigDecimalOrNull()?.let(::JsonPrimitive)

            else -> null
        } ?: JsonPrimitive(text)
    }

    /** Core Schema 的整数：十进制（可带符号）、`0o` 八进制、`0x` 十六进制。 */
    private fun integer(text: String): BigInteger? =
        when {
            text.startsWith(HEX_PREFIX) -> text.substring(HEX_PREFIX.length).toBigIntegerOrNull(HEX_RADIX)
            text.startsWith(OCTAL_PREFIX) -> text.substring(OCTAL_PREFIX.length).toBigIntegerOrNull(OCTAL_RADIX)
            else -> text.removePrefix(PLUS_SIGN).toBigIntegerOrNull()
        }

    private fun Node.position(): TextPosition? = startMark.map { TextPosition(it.line + 1, it.column + 1) }.orElse(null)

    private companion object {
        const val HEX_PREFIX = "0x"
        const val OCTAL_PREFIX = "0o"
        const val PLUS_SIGN = "+"
        const val HEX_RADIX = 16
        const val OCTAL_RADIX = 8
    }
}
