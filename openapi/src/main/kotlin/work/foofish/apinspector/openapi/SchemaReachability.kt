package work.foofish.apinspector.openapi

/**
 * [operation] 通过 `$ref` 直接或间接引用到的所有 Schema，以 [Schema.Reference.ref] 为键。
 */
public fun OpenApiDocument.reachableSchemas(operation: Operation): Map<String, Schema> {
    val reachable = LinkedHashMap<String, Schema>()
    val pending = ArrayDeque(operation.schemas().flatMap(Schema::references))
    while (pending.isNotEmpty()) {
        val ref = pending.removeFirst()
        if (ref in reachable) continue
        val target = schemas.getValue(ref)
        reachable[ref] = target
        pending += target.references()
    }
    return reachable
}

/** 接口中直接出现的所有 Schema（参数、请求体、响应体、响应头）。 */
private fun Operation.schemas(): List<Schema> =
    buildList {
        parameters.resolvedValues().forEach { parameter ->
            addAll(listOfNotNull(parameter.schema))
            addAll(parameter.content.schemas())
        }
        requestBody?.resolvedValues()?.forEach { addAll(it.content.schemas()) }
        responses.values.resolvedValues().forEach { response ->
            addAll(response.content.schemas())
            response.headers.values.resolvedValues().forEach { header ->
                addAll(listOfNotNull(header.schema))
                addAll(header.content.schemas())
            }
        }
    }

private fun Map<String, Resolvable<MediaTypeContent>>.schemas(): List<Schema> =
    values.resolvedValues().flatMap { listOfNotNull(it.schema, it.itemSchema) }

private fun <T> Resolvable<T>.resolvedValues(): List<T> = listOfNotNull((this as? Resolvable.Resolved)?.value)

private fun <T> Iterable<Resolvable<T>>.resolvedValues(): List<T> = flatMap { it.resolvedValues() }

/** 该 Schema 树中出现的所有引用（不进入引用目标）。 */
private fun Schema.references(): List<String> =
    when (this) {
        is Schema.Reference -> listOf(ref)
        is Schema.Unresolved -> emptyList()
        is Schema.Definition -> children().flatMap(Schema::references)
    }

private fun Schema.Definition.children(): List<Schema> =
    properties.values + listOfNotNull(additionalProperties, items, not) + allOf + oneOf + anyOf
