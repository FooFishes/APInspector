package work.foofish.apinspector.operationdoc

import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import work.foofish.apinspector.openapi.HttpMethod
import work.foofish.apinspector.openapi.OperationKey
import work.foofish.apinspector.openapi.ParameterLocation
import work.foofish.apinspector.openapi.UnresolvedReason
import work.foofish.apinspector.testing.document
import work.foofish.apinspector.testing.operation

class OperationDocBuilderTest {
    private val document =
        document(
            """
            paths:
              /pets/{id}:
                post:
                  summary: Update pet
                  description: Update pet
                  security:
                    - apiKey: []
                      oauth: [write]
                    - {}
                  parameters:
                    - name: verbose
                      in: query
                      schema: { type: boolean, default: false }
                    - name: id
                      in: path
                      required: true
                      schema: { type: integer, format: int64, minimum: 1 }
                    - ${'$'}ref: '#/components/parameters/Missing'
                  requestBody:
                    required: true
                    content:
                      application/json:
                        schema: { ${'$'}ref: '#/components/schemas/Pet' }
                        example: { name: Rex }
                      text/plain:
                        schema: { type: string }
                  responses:
                    '200':
                      description: OK
                      headers:
                        X-Rate-Limit:
                          schema: { type: integer }
                      content:
                        application/json:
                          schema: { ${'$'}ref: '#/components/schemas/Pet' }
                    '404':
                      description: Not found
                      content:
                        application/json:
                          schema: { type: object, properties: { message: { type: string } } }
            components:
              schemas:
                Pet:
                  type: object
                  required: [name]
                  properties:
                    name: { type: [string, 'null'], enum: [Rex, Max] }
                    owner: { ${'$'}ref: '#/components/schemas/Person' }
                    tags:
                      type: array
                      items: { ${'$'}ref: '#/components/schemas/Tag' }
                Person:
                  type: object
                  properties:
                    friend: { ${'$'}ref: '#/components/schemas/Person' }
                Tag:
                  type: object
                  properties:
                    label: { type: string }
            """,
        )
    private val operation = document.operation(OperationKey(HttpMethod.POST, "/pets/{id}"))

    private fun build(state: DocViewState = DocViewState()) = buildOperationDoc(operation, document, state)

    private fun List<DocItem>.schemaRow(id: String) = filterIsInstance<DocItem.SchemaRow>().single { it.id == id }

    private fun List<DocItem>.hasId(id: String) = any { it.id == id }

    @Test
    fun `parameters are grouped by location in a fixed order`() {
        val items = build()
        val groups = items.filterIsInstance<DocItem.ParameterGroupTitle>().map { it.location }
        assertEquals(listOf(ParameterLocation.PATH, ParameterLocation.QUERY), groups)

        val id = items.filterIsInstance<DocItem.ParameterRow>().first()
        assertEquals("id", id.name)
        assertTrue(id.required)
        assertEquals(TypeLabel("integer(int64)", TypeLabel.Kind.PLAIN), id.type)
        assertEquals(listOf(SchemaFact.Minimum("1", exclusive = false)), id.facts)

        val unresolved = items.filterIsInstance<DocItem.UnresolvedRow>().first()
        assertEquals(UnresolvedReason.NOT_FOUND, unresolved.reason)
    }

    @Test
    fun `media type root schema is expanded by default and references stay collapsed`() {
        val items = build()
        val root = items.schemaRow(ROOT)
        assertEquals(TypeLabel("Pet", TypeLabel.Kind.REFERENCE), root.type)
        assertTrue(root.expanded)

        val name = items.schemaRow("$ROOT/p:name")
        assertTrue(name.required)
        assertEquals("string | null", name.type.text)
        assertEquals(listOf(SchemaFact.Enum(persistentListOf("Rex", "Max"))), name.facts)

        val owner = items.schemaRow("$ROOT/p:owner")
        assertTrue(owner.expandable)
        assertFalse(owner.expanded)
        assertFalse(items.hasId("$ROOT/p:owner/p:friend"))
    }

    @Test
    fun `toggling collapses the root and expands a reference one level at a time`() {
        val collapsed = build(DocViewState(toggled = setOf(ROOT)))
        assertFalse(collapsed.schemaRow(ROOT).expanded)
        assertFalse(collapsed.hasId("$ROOT/p:name"))

        // Person 引用自身：每次只展开一层，不会无限递归。
        val expanded = build(DocViewState(toggled = setOf("$ROOT/p:owner", "$ROOT/p:owner/p:friend")))
        assertTrue(expanded.hasId("$ROOT/p:owner/p:friend/p:friend"))
        assertFalse(expanded.schemaRow("$ROOT/p:owner/p:friend/p:friend").expanded)
    }

    @Test
    fun `arrays expand directly into their item properties`() {
        val items = build(DocViewState(toggled = setOf("$ROOT/p:tags")))
        assertEquals("array<Tag>", items.schemaRow("$ROOT/p:tags").type.text)
        assertEquals(1, items.schemaRow("$ROOT/p:tags/items/p:label").depth - items.schemaRow("$ROOT/p:tags").depth)
    }

    @Test
    fun `selecting another content type shows its schema`() {
        val default = build().filterIsInstance<DocItem.MediaTypes>().first()
        assertEquals(listOf("application/json", "text/plain"), default.contentTypes)
        assertEquals("application/json", default.selected)
        assertTrue(build().any { it is DocItem.ExampleBlock })

        val plain = build(DocViewState(selectedContentTypes = mapOf("requestBody" to "text/plain")))
        assertEquals("string", plain.schemaRow("requestBody/text/plain/schema").type.text)
        assertFalse(plain.hasId(ROOT))
    }

    @Test
    fun `only the first response is expanded by default`() {
        val items = build()
        val responses = items.filterIsInstance<DocItem.ResponseRow>()
        assertEquals(listOf("200" to true, "404" to false), responses.map { it.status to it.expanded })
        assertTrue(items.any { it is DocItem.HeaderRow && it.name == "X-Rate-Limit" })
        assertFalse(items.hasId("responses/404/application/json/schema"))

        val toggled = build(DocViewState(toggled = setOf("responses/404")))
        assertTrue(toggled.hasId("responses/404/application/json/schema/p:message"))
    }

    @Test
    fun `overview drops a description that repeats the summary and lists security alternatives`() {
        val overview = operation.overview()
        assertNull(overview.description)
        assertEquals(listOf(listOf("apiKey", "oauth"), emptyList()), overview.security)
    }

    private companion object {
        const val ROOT = "requestBody/application/json/schema"
    }
}
