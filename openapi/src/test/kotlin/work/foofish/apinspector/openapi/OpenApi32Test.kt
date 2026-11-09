package work.foofish.apinspector.openapi

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** OpenAPI 3.2 新增的能力。 */
class OpenApi32Test {
    private fun spec32(body: String): String = spec(body, version = "3.2.0")

    @Test
    fun `reads the query method and additional operations in document order`() {
        val result =
            parseSuccess(
                spec32(
                    """
                    paths:
                      /search:
                        get: {}
                        additionalOperations:
                          LINK: {}
                          Purge: {}
                          POST: {}
                          BAD METHOD: {}
                        query: {summary: safe query with a body}
                    """,
                ),
            )
        assertEquals(
            listOf("GET", "LINK", "Purge", "QUERY"),
            result.document.operations.map { it.key.method.name },
        )
        assertEquals(
            listOf(
                ParseWarning.InvalidValue("#/paths/~1search/additionalOperations/POST", "POST"),
                ParseWarning.InvalidValue("#/paths/~1search/additionalOperations/BAD METHOD", "BAD METHOD"),
            ),
            result.warnings,
        )
    }

    @Test
    fun `reads querystring parameters and the cookie style`() {
        val parameters =
            parseSuccess(
                spec32(
                    """
                    paths:
                      /search:
                        get:
                          parameters:
                            - name: filter
                              in: querystring
                              content:
                                application/x-www-form-urlencoded:
                                  schema: {type: object}
                            - {name: session, in: cookie, style: cookie}
                            - {name: legacy, in: cookie}
                    """,
                ),
            ).document
                .operation(HttpMethod.GET, "/search")
                .parameters
                .map { it.resolved() }

        val filter = parameters[0]
        assertEquals(ParameterLocation.QUERY_STRING, filter.location)
        assertEquals(null, filter.serialization)
        assertEquals(listOf("application/x-www-form-urlencoded"), filter.content.keys.toList())
        assertEquals(ParameterSerialization(ParameterStyle.COOKIE, explode = true), parameters[1].serialization)
        assertEquals(ParameterSerialization(ParameterStyle.FORM, explode = true), parameters[2].serialization)
    }

    @Test
    fun `reads new descriptive fields`() {
        val document =
            parseSuccess(
                spec32(
                    """
                    servers:
                      - {url: 'https://api.example.com', name: production}
                    tags:
                      - {name: pets, summary: Pets, kind: nav}
                      - {name: cats, parent: pets}
                    paths:
                      /pets:
                        get:
                          responses:
                            '200':
                              summary: OK
                              content:
                                application/json:
                                  examples:
                                    one:
                                      dataValue: {id: 1}
                                      serializedValue: '{"id":1}'
                    components:
                      securitySchemes:
                        legacy: {type: http, scheme: basic, deprecated: true}
                    """,
                ),
            ).document

        assertEquals("production", document.servers.single().name)
        assertEquals(
            listOf(
                Tag("pets", summary = "Pets", description = null, parent = null, kind = "nav"),
                Tag("cats", summary = null, description = null, parent = "pets", kind = null),
            ),
            document.tags,
        )
        val response =
            document
                .operation(HttpMethod.GET, "/pets")
                .responses
                .getValue("200")
                .resolved()
        assertEquals("OK", response.summary)
        val example =
            response.content
                .getValue("application/json")
                .resolved()
                .examples
                .getValue("one")
                .resolved()
        assertEquals(buildJsonObject { put("id", 1) }, example.dataValue)
        assertEquals("{\"id\":1}", example.serializedValue)
        assertEquals(
            true,
            document.securitySchemes
                .getValue("legacy")
                .resolved()
                .deprecated,
        )
    }

    @Test
    fun `reads item schemas of sequential media types`() {
        val content =
            parseSuccess(
                spec32(
                    """
                    paths:
                      /events:
                        get:
                          responses:
                            '200':
                              content:
                                text/event-stream:
                                  itemSchema: {type: object}
                    """,
                ),
            ).document
                .operation(HttpMethod.GET, "/events")
                .responses
                .getValue("200")
                .resolved()
                .content
                .getValue("text/event-stream")
                .resolved()
        assertEquals(null, content.schema)
        assertEquals(Schema.Definition(types = setOf(SchemaType.OBJECT)), content.itemSchema)
    }

    @Test
    fun `resolves referenced media types`() {
        val result =
            parseSuccess(
                spec32(
                    $$"""
                    paths:
                      /items:
                        post:
                          requestBody:
                            content:
                              application/json:
                                $ref: '#/components/mediaTypes/Item'
                              application/xml:
                                $ref: '#/components/mediaTypes/Missing'
                    components:
                      mediaTypes:
                        Item:
                          example: {name: item}
                    """,
                ),
            )
        val content = checkNotNull(result.document.operation(HttpMethod.POST, "/items").requestBody).resolved().content
        assertEquals(buildJsonObject { put("name", "item") }, content.getValue("application/json").resolved().example)
        assertEquals(
            Resolvable.Unresolved("#/components/mediaTypes/Missing", UnresolvedReason.NOT_FOUND),
            content.getValue("application/xml"),
        )
    }

    @Test
    fun `treats references qualified with the document's own URI as internal`() {
        val result =
            parseSuccess(
                spec32(
                    $$"""
                    $self: https://api.example.com/openapi.yaml
                    paths:
                      /items:
                        get:
                          parameters:
                            - $ref: 'https://api.example.com/openapi.yaml#/components/parameters/Page'
                            - $ref: 'https://api.example.com/openapi.yaml.bak#/components/parameters/Page'
                          responses:
                            '200':
                              content:
                                application/json:
                                  schema:
                                    $ref: 'https://api.example.com/openapi.yaml#/components/schemas/Item'
                    components:
                      parameters:
                        Page: {name: page, in: query}
                      schemas:
                        Item: {type: string, description: item}
                    """,
                ),
            )
        val operation = result.document.operation(HttpMethod.GET, "/items")
        assertEquals("page", operation.parameters[0].resolved().name)
        assertEquals(
            Resolvable.Unresolved("https://api.example.com/openapi.yaml.bak#/components/parameters/Page", UnresolvedReason.EXTERNAL),
            operation.parameters[1],
        )
        val schema =
            operation.responses
                .getValue("200")
                .resolved()
                .content
                .getValue("application/json")
                .resolved()
                .schema
        assertEquals(Schema.Reference("#/components/schemas/Item", "Item", description = null), schema)
        assertEquals("item", (result.document.schemas.getValue("#/components/schemas/Item") as Schema.Definition).description)
    }

    @Test
    fun `keeps reference descriptions like 3_1`() {
        val schema =
            parseSuccess(
                spec32(
                    $$"""
                    paths:
                      /items:
                        get:
                          responses:
                            '200':
                              content:
                                application/json:
                                  schema: {$ref: '#/components/schemas/Item', description: overridden}
                    components:
                      schemas:
                        Item: {type: string}
                    """,
                ),
            ).document
                .operation(HttpMethod.GET, "/items")
                .responses
                .getValue("200")
                .resolved()
                .content
                .getValue("application/json")
                .resolved()
                .schema
        assertEquals("overridden", (schema as Schema.Reference).description)
    }
}
