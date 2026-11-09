package work.foofish.apinspector.openapi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OperationDiffTest {
    private fun document(body: String): OpenApiDocument = parseSuccess(spec(body)).document

    private val get = OperationKey(HttpMethod.GET, "/items")
    private val post = OperationKey(HttpMethod.POST, "/items")
    private val delete = OperationKey(HttpMethod.DELETE, "/items/{id}")

    @Test
    fun `finds added, removed and modified operations by method and path`() {
        val old =
            document(
                """
                paths:
                  /items:
                    get: {summary: List}
                    post: {summary: Create}
                  /items/{id}:
                    delete: {summary: Delete}
                """,
            )
        val new =
            document(
                """
                paths:
                  /items:
                    get: {summary: List all}
                  /items/{id}:
                    delete: {summary: Delete}
                    put: {summary: Replace}
                """,
            )
        assertEquals(
            OperationDiff(
                added = listOf(OperationKey(HttpMethod.PUT, "/items/{id}")),
                removed = listOf(post),
                modified = listOf(get),
            ),
            OperationDiff.between(old, new),
        )
    }

    @Test
    fun `treats a changed referenced schema as a modification`() {
        fun withItemSchema(type: String) =
            document(
                $$"""
                paths:
                  /items:
                    get:
                      responses:
                        '200':
                          content:
                            application/json:
                              schema: {$ref: '#/components/schemas/Page'}
                    post: {summary: unaffected}
                components:
                  schemas:
                    Page:
                      type: object
                      properties:
                        items: {type: array, items: {$ref: '#/components/schemas/Item'}}
                    Item: {type: $$type}
                """,
            )

        assertEquals(listOf(get), OperationDiff.between(withItemSchema("string"), withItemSchema("integer")).modified)
    }

    @Test
    fun `reports no difference for identical documents`() {
        val body = "paths:\n  /items:\n    get: {}\n  /items/{id}:\n    delete: {}"
        assertTrue(OperationDiff.between(document(body), document(body)).isEmpty)
    }

    @Test
    fun `collects schemas reachable through references transitively, tolerating cycles`() {
        val document =
            document(
                $$"""
                paths:
                  /items/{id}:
                    delete:
                      parameters:
                        - {name: id, in: path, schema: {$ref: '#/components/schemas/Id'}}
                      responses:
                        '200':
                          content:
                            application/json:
                              schema: {$ref: '#/components/schemas/Node'}
                components:
                  schemas:
                    Id: {type: string}
                    Node:
                      properties:
                        next: {$ref: '#/components/schemas/Node'}
                        id: {$ref: '#/components/schemas/Id'}
                """,
            )
        assertEquals(
            setOf("#/components/schemas/Id", "#/components/schemas/Node"),
            document.reachableSchemas(document.operation(HttpMethod.DELETE, "/items/{id}")).keys,
        )
        assertEquals(delete, document.operations.single().key)
    }
}
