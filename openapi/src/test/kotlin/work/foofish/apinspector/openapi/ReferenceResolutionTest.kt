package work.foofish.apinspector.openapi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReferenceResolutionTest {
    private fun parametersOf(
        parameters: String,
        components: String,
    ): OpenApiParseResult.Success =
        parseSuccess(
            spec(
                """
                |paths:
                |  /items:
                |    get:
                |      parameters:
                |${parameters.trimIndent().prependIndent("        ")}
                |components:
                |  parameters:
                |${components.trimIndent().prependIndent("    ")}
                """.trimMargin(),
            ),
        )

    @Test
    fun `follows a chain of references`() {
        val result =
            parametersOf(
                parameters = $$"- $ref: '#/components/parameters/Alias'",
                components =
                    $$"""
                    Alias:
                      $ref: '#/components/parameters/Real'
                    Real: {name: real, in: query}
                    """,
            )
        assertEquals(
            "real",
            result.document
                .operation(HttpMethod.GET, "/items")
                .parameters
                .single()
                .resolved()
                .name,
        )
        assertEquals(emptyList<ParseWarning>(), result.warnings)
    }

    @Test
    fun `marks circular reference chains as unresolved`() {
        val result =
            parametersOf(
                parameters = $$"- $ref: '#/components/parameters/A'",
                components =
                    $$"""
                    A:
                      $ref: '#/components/parameters/B'
                    B:
                      $ref: '#/components/parameters/A'
                    """,
            )
        assertEquals(
            listOf(Resolvable.Unresolved("#/components/parameters/A", UnresolvedReason.CIRCULAR)),
            result.document.operation(HttpMethod.GET, "/items").parameters,
        )
        assertEquals(
            listOf(
                ParseWarning.UnresolvedReference("#/components/parameters/B/\$ref", "#/components/parameters/A", UnresolvedReason.CIRCULAR),
            ),
            result.warnings,
        )
    }

    @Test
    fun `marks external, missing and invalid targets as unresolved without affecting siblings`() {
        val result =
            parametersOf(
                parameters =
                    $$"""
                    - $ref: 'common.yaml#/parameters/Page'
                    - $ref: '#/components/parameters/Missing'
                    - $ref: '#/components/parameters/NotAnObject'
                    - $ref: '#/components/parameters/Bad~2Escape'
                    - {name: ok, in: query}
                    """,
                components = "NotAnObject: just a string",
            )
        assertEquals(
            listOf(
                Resolvable.Unresolved("common.yaml#/parameters/Page", UnresolvedReason.EXTERNAL),
                Resolvable.Unresolved("#/components/parameters/Missing", UnresolvedReason.NOT_FOUND),
                Resolvable.Unresolved("#/components/parameters/NotAnObject", UnresolvedReason.INVALID_TARGET),
                Resolvable.Unresolved("#/components/parameters/Bad~2Escape", UnresolvedReason.INVALID_POINTER),
            ),
            result.document
                .operation(HttpMethod.GET, "/items")
                .parameters
                .dropLast(1),
        )
        assertEquals(
            "ok",
            result.document
                .operation(HttpMethod.GET, "/items")
                .parameters
                .last()
                .resolved()
                .name,
        )
        assertEquals(
            listOf("#/paths/~1items/get/parameters/0/\$ref", "#/paths/~1items/get/parameters/1/\$ref"),
            result.warnings.take(2).map(ParseWarning::pointer),
        )
    }

    @Test
    fun `resolves references to any location in the document`() {
        val result =
            parseSuccess(
                spec(
                    $$"""
                    paths:
                      /a:
                        get:
                          parameters:
                            - {name: shared, in: query}
                      /b:
                        get:
                          parameters:
                            - $ref: '#/paths/~1a/get/parameters/0'
                    """,
                ),
            )
        assertEquals(
            "shared",
            result.document
                .operation(HttpMethod.GET, "/b")
                .parameters
                .single()
                .resolved()
                .name,
        )
    }

    @Test
    fun `resolves referenced path items`() {
        val result =
            parseSuccess(
                spec(
                    $$"""
                    paths:
                      /items:
                        $ref: '#/components/pathItems/Items'
                      /remote:
                        $ref: 'remote.yaml#/Items'
                    components:
                      pathItems:
                        Items:
                          get: {summary: from component}
                    """,
                ),
            )
        assertEquals(listOf(OperationKey(HttpMethod.GET, "/items")), result.document.operations.map(Operation::key))
        assertEquals(
            listOf(ParseWarning.UnresolvedReference("#/paths/~1remote/\$ref", "remote.yaml#/Items", UnresolvedReason.EXTERNAL)),
            result.warnings,
        )
    }

    @Test
    fun `resolves referenced responses, headers and examples`() {
        val response =
            parseSuccess(
                spec(
                    $$"""
                    paths:
                      /items:
                        get:
                          responses:
                            '200':
                              $ref: '#/components/responses/Ok'
                    components:
                      responses:
                        Ok:
                          description: ok
                          headers:
                            X-Rate-Limit:
                              $ref: '#/components/headers/RateLimit'
                          content:
                            application/json:
                              examples:
                                sample:
                                  $ref: '#/components/examples/Sample'
                      headers:
                        RateLimit: {description: limit, required: true}
                      examples:
                        Sample: {summary: sample, value: 42}
                    """,
                ),
            ).document
                .operation(HttpMethod.GET, "/items")
                .responses
                .getValue("200")
                .resolved()
        assertEquals(
            "limit",
            response.headers
                .getValue("X-Rate-Limit")
                .resolved()
                .description,
        )
        val example =
            response.content
                .getValue("application/json")
                .resolved()
                .examples
                .getValue("sample")
                .resolved()
        assertEquals("sample", example.summary)
        assertEquals("42", example.value.toString())
    }
}
