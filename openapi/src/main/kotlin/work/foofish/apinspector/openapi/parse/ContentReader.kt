package work.foofish.apinspector.openapi.parse

import work.foofish.apinspector.openapi.Example
import work.foofish.apinspector.openapi.Header
import work.foofish.apinspector.openapi.MediaTypeContent
import work.foofish.apinspector.openapi.RequestBody
import work.foofish.apinspector.openapi.Resolvable
import work.foofish.apinspector.openapi.Response

/** 读取请求体、响应、响应头，以及它们共用的 content 与示例。 */
internal class ContentReader(
    private val context: ParseContext,
    private val schemas: SchemaReader,
) {
    fun requestBody(node: ObjectNode): Resolvable<RequestBody>? =
        context.resolve(node) {
            RequestBody(
                description = it.string(Field.DESCRIPTION),
                required = it.boolean(Field.REQUIRED) == true,
                content = content(it),
            )
        }

    fun response(node: ObjectNode): Resolvable<Response>? =
        context.resolve(node) { response ->
            Response(
                summary = response.string(Field.SUMMARY),
                description = response.string(Field.DESCRIPTION),
                headers = resolveAll(response.children(Field.HEADERS), ::header),
                content = content(response),
            )
        }

    /** 读取 `content` 字段：content type → Media Type Object（3.2 起可为引用），保持文档中的顺序。 */
    fun content(node: ObjectNode): Map<String, Resolvable<MediaTypeContent>> =
        resolveAll(node.children(Field.CONTENT)) { mediaType ->
            context.resolve(mediaType) {
                MediaTypeContent(
                    schema = schemas.field(it, Field.SCHEMA),
                    itemSchema = schemas.field(it, Field.ITEM_SCHEMA),
                    example = it.element(Field.EXAMPLE),
                    examples = examples(it),
                )
            }
        }

    /** 读取 `examples` 字段：名称 → Example Object。 */
    fun examples(node: ObjectNode): Map<String, Resolvable<Example>> =
        resolveAll(node.children(Field.EXAMPLES)) { example ->
            context.resolve(example) {
                Example(
                    summary = it.string(Field.SUMMARY),
                    description = it.string(Field.DESCRIPTION),
                    dataValue = it.element(Field.DATA_VALUE),
                    serializedValue = it.string(Field.SERIALIZED_VALUE),
                    value = it.element(Field.VALUE),
                    externalValue = it.string(Field.EXTERNAL_VALUE),
                )
            }
        }

    private fun header(node: ObjectNode): Resolvable<Header>? =
        context.resolve(node) {
            Header(
                description = it.string(Field.DESCRIPTION),
                required = it.boolean(Field.REQUIRED) == true,
                deprecated = it.boolean(Field.DEPRECATED) == true,
                schema = schemas.field(it, Field.SCHEMA),
                content = content(it),
            )
        }
}

/** 逐个解析"名称 → 对象"映射中的条目，丢弃本身不合法的对象，保持顺序。 */
internal fun <T : Any> resolveAll(
    entries: List<Pair<String, ObjectNode>>,
    resolve: (ObjectNode) -> Resolvable<T>?,
): Map<String, Resolvable<T>> = entries.mapNotNull { (name, node) -> resolve(node)?.let { name to it } }.toMap()
