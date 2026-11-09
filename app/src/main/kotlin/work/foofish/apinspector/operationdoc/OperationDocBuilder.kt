package work.foofish.apinspector.operationdoc

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import work.foofish.apinspector.openapi.Header
import work.foofish.apinspector.openapi.MediaTypeContent
import work.foofish.apinspector.openapi.OpenApiDocument
import work.foofish.apinspector.openapi.Operation
import work.foofish.apinspector.openapi.Parameter
import work.foofish.apinspector.openapi.ParameterLocation
import work.foofish.apinspector.openapi.RequestBody
import work.foofish.apinspector.openapi.Resolvable
import work.foofish.apinspector.openapi.Response
import work.foofish.apinspector.openapi.Schema
import work.foofish.apinspector.openapi.SchemaType

/** 许多文档的 description 只是重复 summary，此时不再重复显示。 */
fun Operation.overview(): OperationOverview =
    OperationOverview(
        key = key,
        summary = summary,
        description = description?.takeUnless { it.trim() == summary?.trim() },
        operationId = operationId,
        deprecated = deprecated,
        tags = tags.toImmutableList(),
        security = security.map { requirement -> requirement.schemes.keys.toImmutableList() }.toImmutableList(),
    )

/**
 * 把接口文档摊平为 [DocItem] 列表。
 *
 * Schema 树只展开用户展开过的节点（媒体类型的根节点默认展开），`$ref` 在展开时才解引用一层，
 * 因此循环引用不会导致无限展开。
 */
fun buildOperationDoc(
    operation: Operation,
    document: OpenApiDocument,
    state: DocViewState,
): ImmutableList<DocItem> = OperationDocBuilder(document, state).build(operation)

private class OperationDocBuilder(
    private val document: OpenApiDocument,
    private val state: DocViewState,
) {
    private val items = mutableListOf<DocItem>()

    fun build(operation: Operation): ImmutableList<DocItem> {
        parameters(operation.parameters)
        requestBody(operation.requestBody)
        responses(operation.responses)
        return items.toImmutableList()
    }

    /** 默认展开的节点被切换后收起，反之亦然。 */
    private fun isExpanded(
        id: String,
        default: Boolean,
    ): Boolean = default != (id in state.toggled)

    private fun parameters(parameters: List<Resolvable<Parameter>>) {
        items += DocItem.SectionTitle(PARAMETERS_ID, DocSection.PARAMETERS)
        if (parameters.isEmpty()) {
            items += DocItem.Note("$PARAMETERS_ID/none", DocNote.NO_PARAMETERS, depth = 0)
            return
        }
        val resolved = parameters.filterIsInstance<Resolvable.Resolved<Parameter>>().map { it.value }
        LOCATION_ORDER.forEach { location ->
            val group = resolved.filter { it.location == location }
            if (group.isEmpty()) return@forEach
            items += DocItem.ParameterGroupTitle("$PARAMETERS_ID/${location.name}", location)
            group.forEach(::parameter)
        }
        parameters.filterIsInstance<Resolvable.Unresolved>().forEachIndexed { index, unresolved ->
            items += DocItem.UnresolvedRow("$PARAMETERS_ID/unresolved/$index", depth = 0, unresolved.ref, unresolved.reason)
        }
    }

    private fun parameter(parameter: Parameter) {
        val id = "$PARAMETERS_ID/${parameter.location.name}/${parameter.name}"
        // 以 content 描述的参数（如 querystring）取第一个 content type 的 Schema。
        val schema =
            parameter.schema ?: parameter.content.values
                .firstResolved()
                ?.schema
        val target = schema?.let(document::dereference) as? Schema.Definition
        val children = schema?.let { children(it) }.orEmpty()
        val expanded = children.isNotEmpty() && isExpanded(id, default = false)
        items +=
            DocItem.ParameterRow(
                id = id,
                name = parameter.name,
                required = parameter.required,
                deprecated = parameter.deprecated,
                type = schema?.let(::typeLabel),
                description = parameter.description ?: (schema as? Schema.Reference)?.description ?: target?.description,
                facts = facts(target, parameter.example).toImmutableList(),
                expandable = children.isNotEmpty(),
                expanded = expanded,
            )
        if (expanded) children.forEach { schemaNode("$id/${it.segment}", depth = 1, it) }
    }

    private fun requestBody(body: Resolvable<RequestBody>?) {
        items += DocItem.SectionTitle(REQUEST_BODY_ID, DocSection.REQUEST_BODY)
        when (body) {
            null -> {
                items += DocItem.Note("$REQUEST_BODY_ID/none", DocNote.NO_REQUEST_BODY, depth = 0)
            }

            is Resolvable.Unresolved -> {
                items += DocItem.UnresolvedRow("$REQUEST_BODY_ID/unresolved", depth = 0, body.ref, body.reason)
            }

            is Resolvable.Resolved -> {
                body.value.description?.let { items += DocItem.Description("$REQUEST_BODY_ID/description", it, depth = 0) }
                if (body.value.required) items += DocItem.Note("$REQUEST_BODY_ID/required", DocNote.BODY_REQUIRED, depth = 0)
                content(REQUEST_BODY_ID, depth = 0, body.value.content)
            }
        }
    }

    private fun responses(responses: Map<String, Resolvable<Response>>) {
        items += DocItem.SectionTitle(RESPONSES_ID, DocSection.RESPONSES)
        if (responses.isEmpty()) {
            items += DocItem.Note("$RESPONSES_ID/none", DocNote.NO_RESPONSES, depth = 0)
            return
        }
        responses.entries.forEachIndexed { index, (status, response) ->
            val id = "$RESPONSES_ID/$status"
            when (response) {
                is Resolvable.Unresolved -> {
                    items += DocItem.ResponseRow(id, status, summary = null, description = null, expandable = false, expanded = false)
                    items += DocItem.UnresolvedRow("$id/unresolved", depth = 1, response.ref, response.reason)
                }

                is Resolvable.Resolved -> {
                    response(id, status, response.value, defaultExpanded = index == 0)
                }
            }
        }
    }

    private fun response(
        id: String,
        status: String,
        response: Response,
        defaultExpanded: Boolean,
    ) {
        val expandable = response.headers.isNotEmpty() || response.content.isNotEmpty()
        val expanded = expandable && isExpanded(id, defaultExpanded)
        items += DocItem.ResponseRow(id, status, response.summary, response.description, expandable, expanded)
        if (!expanded) return
        if (response.headers.isNotEmpty()) {
            items += DocItem.Note("$id/headers", DocNote.RESPONSE_HEADERS, depth = 1)
            response.headers.forEach { (name, header) -> header("$id/header/$name", name, header) }
        }
        content(id, depth = 1, response.content)
    }

    private fun header(
        id: String,
        name: String,
        header: Resolvable<Header>,
    ) {
        items +=
            when (header) {
                is Resolvable.Unresolved -> {
                    DocItem.UnresolvedRow(id, depth = 1, header.ref, header.reason)
                }

                is Resolvable.Resolved -> {
                    val schema =
                        header.value.schema ?: header.value.content.values
                            .firstResolved()
                            ?.schema
                    DocItem.HeaderRow(id, depth = 1, name, header.value.required, schema?.let(::typeLabel), header.value.description)
                }
            }
    }

    private fun content(
        ownerId: String,
        depth: Int,
        content: Map<String, Resolvable<MediaTypeContent>>,
    ) {
        if (content.isEmpty()) return
        val selected = state.selectedContentTypes[ownerId]?.takeIf { it in content } ?: content.keys.first()
        items += DocItem.MediaTypes("$ownerId/media", ownerId, content.keys.toImmutableList(), selected, depth)
        val mediaId = "$ownerId/$selected"
        when (val media = content.getValue(selected)) {
            is Resolvable.Unresolved -> {
                items += DocItem.UnresolvedRow("$mediaId/unresolved", depth, media.ref, media.reason)
            }

            is Resolvable.Resolved -> {
                media.value.schema?.let { schemaRoot("$mediaId/schema", depth, label = null, it) }
                media.value.itemSchema?.let { schemaRoot("$mediaId/itemSchema", depth, NodeLabel.ItemSchema, it) }
                examples(mediaId, depth, media.value)
            }
        }
    }

    private fun examples(
        mediaId: String,
        depth: Int,
        media: MediaTypeContent,
    ) {
        media.example?.let { items += DocItem.ExampleBlock("$mediaId/example", depth, ExampleTitle.Single, it.toPrettyJson()) }
        media.examples.forEach { (name, example) ->
            val id = "$mediaId/examples/$name"
            when (example) {
                is Resolvable.Unresolved -> {
                    items += DocItem.UnresolvedRow(id, depth, example.ref, example.reason)
                }

                is Resolvable.Resolved -> {
                    val value = example.value
                    (value.dataValue ?: value.value)?.let {
                        items += DocItem.ExampleBlock("$id/value", depth, ExampleTitle.Named(name, value.summary), it.toPrettyJson())
                    }
                    value.serializedValue?.let { items += DocItem.ExampleBlock("$id/serialized", depth, ExampleTitle.Serialized(name), it) }
                    value.externalValue?.let { items += DocItem.ExampleBlock("$id/external", depth, ExampleTitle.External(name), it) }
                }
            }
        }
    }

    /** 媒体类型下的根 Schema，默认展开。 */
    private fun schemaRoot(
        id: String,
        depth: Int,
        label: NodeLabel?,
        schema: Schema,
    ) {
        schemaNode(id, depth, Child(segment = "", label = label, schema = schema, required = false), defaultExpanded = true)
    }

    private fun schemaNode(
        id: String,
        depth: Int,
        node: Child,
        defaultExpanded: Boolean = false,
    ) {
        val schema = node.schema
        if (schema is Schema.Unresolved) {
            items += DocItem.UnresolvedRow(id, depth, schema.ref, schema.reason)
            return
        }
        val target = document.dereference(schema)
        val definition = target as? Schema.Definition
        val children = children(schema)
        val expanded = children.isNotEmpty() && isExpanded(id, defaultExpanded)
        items +=
            DocItem.SchemaRow(
                id = id,
                depth = depth,
                label = node.label,
                type = typeLabel(schema),
                required = node.required,
                flags =
                    SchemaFlags(
                        deprecated = definition?.deprecated == true,
                        readOnly = definition?.readOnly == true,
                        writeOnly = definition?.writeOnly == true,
                    ),
                description = (schema as? Schema.Reference)?.description ?: definition?.description,
                facts = facts(definition, example = null).toImmutableList(),
                expandable = children.isNotEmpty(),
                expanded = expanded,
            )
        if (expanded) children.forEach { schemaNode("$id/${it.segment}", depth + 1, it) }
    }

    private data class Child(
        val segment: String,
        val label: NodeLabel?,
        val schema: Schema,
        val required: Boolean,
    )

    /**
     * 节点展开后的直接子节点。数组不单独占一层，直接展开为元素 Schema 的子节点，
     * [seen] 防止 `A = array<A>` 这类元素链循环。
     */
    private fun children(
        schema: Schema,
        seen: Set<String> = emptySet(),
    ): List<Child> {
        if (schema is Schema.Reference && schema.ref in seen) return emptyList()
        val definition = document.dereference(schema) as? Schema.Definition ?: return emptyList()
        val visited = if (schema is Schema.Reference) seen + schema.ref else seen
        return buildList {
            definition.properties.forEach { (name, property) ->
                add(Child("$PROPERTY_SEGMENT$name", NodeLabel.Property(name), property, required = name in definition.required))
            }
            definition.items?.let { items ->
                children(items, visited).forEach { add(it.copy(segment = "$ITEMS_SEGMENT/${it.segment}")) }
            }
            definition.additionalProperties?.takeUnless { it.isBooleanSchema() }?.let {
                add(Child(ADDITIONAL_SEGMENT, NodeLabel.AdditionalProperties, it, required = false))
            }
            composition(ALL_OF, definition.allOf)
            composition(ONE_OF, definition.oneOf)
            composition(ANY_OF, definition.anyOf)
            definition.not?.takeUnless { it.isBooleanSchema() }?.let { add(Child(NOT_SEGMENT, NodeLabel.Not, it, required = false)) }
        }
    }

    private fun MutableList<Child>.composition(
        keyword: String,
        schemas: List<Schema>,
    ) {
        schemas.forEachIndexed { index, schema ->
            add(Child("$keyword:$index", NodeLabel.Composition(keyword, index + 1), schema, required = false))
        }
    }

    private fun typeLabel(schema: Schema): TypeLabel =
        when (schema) {
            is Schema.Reference -> TypeLabel(schema.name, TypeLabel.Kind.REFERENCE)
            is Schema.Unresolved -> TypeLabel(schema.ref, TypeLabel.Kind.UNRESOLVED)
            is Schema.Definition -> TypeLabel(typeText(schema), TypeLabel.Kind.PLAIN)
        }

    private fun facts(
        definition: Schema.Definition?,
        example: JsonElement?,
    ): List<SchemaFact> =
        buildList {
            if (definition != null) {
                if (definition.enumValues.isNotEmpty()) add(SchemaFact.Enum(definition.enumValues.map { it.toDisplay() }.toImmutableList()))
                definition.defaultValue?.let { add(SchemaFact.Default(it.toDisplay())) }
                definition.minimum?.let { add(SchemaFact.Minimum(it.value.toPlainString(), it.exclusive)) }
                definition.maximum?.let { add(SchemaFact.Maximum(it.value.toPlainString(), it.exclusive)) }
                definition.minLength?.let { add(SchemaFact.MinLength(it)) }
                definition.maxLength?.let { add(SchemaFact.MaxLength(it)) }
                definition.minItems?.let { add(SchemaFact.MinItems(it)) }
                definition.maxItems?.let { add(SchemaFact.MaxItems(it)) }
                if (definition.uniqueItems) add(SchemaFact.UniqueItems)
                definition.pattern?.let { add(SchemaFact.Pattern(it)) }
            }
            (example ?: definition?.examples?.firstOrNull())?.let { add(SchemaFact.Example(it.toDisplay())) }
        }

    private companion object {
        const val PARAMETERS_ID = "parameters"
        const val REQUEST_BODY_ID = "requestBody"
        const val RESPONSES_ID = "responses"
        const val PROPERTY_SEGMENT = "p:"
        const val ITEMS_SEGMENT = "items"
        const val ADDITIONAL_SEGMENT = "additional"
        const val NOT_SEGMENT = "not"

        val LOCATION_ORDER =
            listOf(
                ParameterLocation.PATH,
                ParameterLocation.QUERY,
                ParameterLocation.QUERY_STRING,
                ParameterLocation.HEADER,
                ParameterLocation.COOKIE,
            )
    }
}

/** 类型简写；数组元素为引用时只显示引用名，不展开，因此不会递归。 */
private fun typeText(definition: Schema.Definition): String {
    val core = definition.types - SchemaType.NULL
    val base = coreTypeText(definition, core)
    val formatted = definition.format?.let { "$base($it)" } ?: base
    return when {
        SchemaType.NULL !in definition.types -> formatted
        core.isEmpty() -> NULL_TYPE
        else -> "$formatted$TYPE_SEPARATOR$NULL_TYPE"
    }
}

/** 不含 `null` 的类型；未声明 type 时按关键字推断。 */
private fun coreTypeText(
    definition: Schema.Definition,
    core: Set<SchemaType>,
): String {
    val items = definition.items
    return when {
        SchemaType.ARRAY in core && items != null -> "array<${items.shortName()}>"
        core.isNotEmpty() -> core.joinToString(TYPE_SEPARATOR) { it.name.lowercase() }
        else -> inferredTypeText(definition)
    }
}

private fun inferredTypeText(definition: Schema.Definition): String =
    when {
        definition.properties.isNotEmpty() || definition.additionalProperties != null -> OBJECT_TYPE
        definition.allOf.isNotEmpty() -> ALL_OF
        definition.oneOf.isNotEmpty() -> ONE_OF
        definition.anyOf.isNotEmpty() -> ANY_OF
        else -> ANY_TYPE
    }

private fun Schema.shortName(): String =
    when (this) {
        is Schema.Reference -> name
        is Schema.Unresolved -> UNKNOWN_TYPE
        is Schema.Definition -> typeText(this)
    }

/** 布尔 Schema：`true`（任意值）与 `false`（不允许任何值）没有可展开的内容。 */
private fun Schema.isBooleanSchema(): Boolean = this == ANY_SCHEMA || this == NOTHING_SCHEMA

private fun Collection<Resolvable<MediaTypeContent>>.firstResolved(): MediaTypeContent? =
    firstNotNullOfOrNull { (it as? Resolvable.Resolved)?.value }

private fun JsonElement.toDisplay(): String = if (this is JsonPrimitive) content else toString()

private fun JsonElement.toPrettyJson(): String = PrettyJson.encodeToString(JsonElement.serializer(), this)

private val PrettyJson = Json { prettyPrint = true }
private val ANY_SCHEMA = Schema.Definition()
private val NOTHING_SCHEMA = Schema.Definition(not = Schema.Definition())
private const val TYPE_SEPARATOR = " | "
private const val UNKNOWN_TYPE = "?"
private const val OBJECT_TYPE = "object"
private const val ANY_TYPE = "any"
private const val NULL_TYPE = "null"
private const val ALL_OF = "allOf"
private const val ONE_OF = "oneOf"
private const val ANY_OF = "anyOf"
