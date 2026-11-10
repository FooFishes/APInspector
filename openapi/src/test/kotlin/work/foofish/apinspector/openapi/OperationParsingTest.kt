package work.foofish.apinspector.openapi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import work.foofish.apinspector.openapi.request.isIgnoredHeader

class OperationParsingTest {
    @Test
    fun `keeps method order and ignores non-method path item fields`() {
        val result =
            parseSuccess(
                spec(
                    """
                    paths:
                      /items:
                        summary: shared
                        description: shared
                        post: {}
                        get: {}
                        servers: []
                    """,
                ),
            )
        assertEquals(listOf(HttpMethod.POST, HttpMethod.GET), result.document.operations.map { it.key.method })
        assertEquals(emptyList<ParseWarning>(), result.warnings)
    }

    @Test
    fun `operation parameters override path item parameters with the same name and location`() {
        val document =
            parseSuccess(
                spec(
                    """
                    paths:
                      /items:
                        parameters:
                          - {name: q, in: query, description: shared}
                          - {name: q, in: header}
                        get:
                          parameters:
                            - {name: q, in: query, description: own}
                    """,
                ),
            ).document
        val parameters = document.operation(HttpMethod.GET, "/items").parameters.map { it.resolved() }
        assertEquals(
            listOf(Triple("q", ParameterLocation.HEADER, null), Triple("q", ParameterLocation.QUERY, "own")),
            parameters.map { Triple(it.name, it.location, it.description) },
        )
    }

    @Test
    fun `fills style and explode defaults by location`() {
        val document =
            parseSuccess(
                spec(
                    """
                    paths:
                      /items/{id}:
                        get:
                          parameters:
                            - {name: id, in: path, required: false}
                            - {name: q, in: query}
                            - {name: X-Trace, in: header}
                            - {name: session, in: cookie}
                            - {name: filter, in: query, style: deepObject, explode: false}
                    """,
                ),
            ).document
        val parameters = document.operation(HttpMethod.GET, "/items/{id}").parameters.map { it.resolved() }
        assertEquals(
            listOf(
                ParameterSerialization(ParameterStyle.SIMPLE, explode = false) to true,
                ParameterSerialization(ParameterStyle.FORM, explode = true) to false,
                ParameterSerialization(ParameterStyle.SIMPLE, explode = false) to false,
                ParameterSerialization(ParameterStyle.FORM, explode = true) to false,
                ParameterSerialization(ParameterStyle.DEEP_OBJECT, explode = false) to false,
            ),
            parameters.map { it.serialization to it.required },
        )
    }

    @Test
    fun `keeps header parameters that the specification reserves, marking them ignored`() {
        val document =
            parseSuccess(
                spec(
                    """
                    paths:
                      /items:
                        get:
                          parameters:
                            - {name: Authorization, in: header}
                            - {name: content-type, in: header}
                            - {name: Accept, in: header}
                            - {name: Accept, in: query}
                    """,
                ),
            ).document
        val parameters = document.operation(HttpMethod.GET, "/items").parameters.map { it.resolved() }
        assertEquals(listOf(true, true, true, false), parameters.map { it.isIgnoredHeader })
    }

    @Test
    fun `drops invalid parameters with a warning`() {
        val result =
            parseSuccess(
                spec(
                    """
                    paths:
                      /items:
                        post:
                          parameters:
                            - {name: body, in: body}
                            - {in: query}
                            - {name: style, in: query, style: fancy}
                    """,
                ),
            )
        val parameters =
            result.document
                .operation(HttpMethod.POST, "/items")
                .parameters
                .map { it.resolved() }
        assertEquals(listOf("style" to ParameterStyle.FORM), parameters.map { it.name to it.serialization?.style })
        assertEquals(
            listOf(
                ParseWarning.InvalidValue("#/paths/~1items/post/parameters/0/in", "body"),
                ParseWarning.MissingField("#/paths/~1items/post/parameters/1", "name"),
                ParseWarning.InvalidValue("#/paths/~1items/post/parameters/2/style", "fancy"),
            ),
            result.warnings,
        )
    }

    @Test
    fun `skips paths that do not start with a slash`() {
        val result = parseSuccess(spec("paths:\n  items:\n    get: {}"))
        assertEquals(emptyList<Operation>(), result.document.operations)
        assertEquals(listOf(ParseWarning.InvalidValue("#/paths/items", "items")), result.warnings)
    }

    @Test
    fun `skips specification extensions in paths and responses`() {
        val result =
            parseSuccess(
                spec(
                    """
                    paths:
                      x-internal: true
                      /items:
                        x-owner: team
                        get:
                          responses:
                            x-note: shared
                            '200': {description: ok}
                    """,
                ),
            )
        assertEquals(
            listOf("200"),
            result.document
                .operation(HttpMethod.GET, "/items")
                .responses.keys
                .toList(),
        )
        assertEquals(emptyList<ParseWarning>(), result.warnings)
    }

    @Test
    fun `reads operation metadata`() {
        val operation =
            parseSuccess(
                spec(
                    """
                    paths:
                      /items:
                        get:
                          operationId: listItems
                          summary: List
                          description: Lists items.
                          tags: [a, b]
                          deprecated: true
                    """,
                ),
            ).document.operation(HttpMethod.GET, "/items")
        assertEquals(
            listOf("listItems", "List", "Lists items.", listOf("a", "b"), true),
            listOf(operation.operationId, operation.summary, operation.description, operation.tags, operation.deprecated),
        )
    }

    @Test
    fun `keeps content types in document order`() {
        val body =
            parseSuccess(
                spec(
                    """
                    paths:
                      /items:
                        post:
                          requestBody:
                            content:
                              application/json: {}
                              application/x-www-form-urlencoded: {}
                              multipart/form-data: {}
                    """,
                ),
            ).document.operation(HttpMethod.POST, "/items").requestBody
        assertEquals(
            listOf("application/json", "application/x-www-form-urlencoded", "multipart/form-data"),
            checkNotNull(body)
                .resolved()
                .content.keys
                .toList(),
        )
    }

    @Test
    fun `reads every kind of security scheme`() {
        val result =
            parseSuccess(
                spec(
                    """
                    components:
                      securitySchemes:
                        basic: {type: http, scheme: Basic}
                        query: {type: apiKey, name: token, in: query}
                        oauth: {type: oauth2, description: OAuth}
                        broken: {type: magic}
                    """,
                ),
            )
        assertEquals(
            mapOf(
                "basic" to Resolvable.Resolved(SecurityScheme.Http("basic", bearerFormat = null, description = null, deprecated = false)),
                "query" to
                    Resolvable.Resolved(
                        SecurityScheme.ApiKey("token", ApiKeyLocation.QUERY, description = null, deprecated = false),
                    ),
                "oauth" to Resolvable.Resolved(SecurityScheme.Other("oauth2", description = "OAuth", deprecated = false)),
            ),
            result.document.securitySchemes,
        )
        assertEquals(listOf(ParseWarning.InvalidValue("#/components/securitySchemes/broken/type", "magic")), result.warnings)
    }

    @Test
    fun `reads optional security requirements with scopes`() {
        val operation =
            parseSuccess(
                spec(
                    """
                    paths:
                      /items:
                        get:
                          security:
                            - {}
                            - oauth: [read, write]
                              key: []
                    """,
                ),
            ).document.operation(HttpMethod.GET, "/items")
        assertEquals(
            listOf(SecurityRequirement(emptyMap()), SecurityRequirement(mapOf("oauth" to listOf("read", "write"), "key" to emptyList()))),
            operation.security,
        )
    }
}
