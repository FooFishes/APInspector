package work.foofish.apinspector.request.body

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import work.foofish.apinspector.openapi.HttpMethod
import work.foofish.apinspector.openapi.OperationKey
import work.foofish.apinspector.openapi.request.JsonMember
import work.foofish.apinspector.openapi.request.JsonNode
import work.foofish.apinspector.openapi.request.JsonTemplate
import work.foofish.apinspector.openapi.request.JsonTemplateParse
import work.foofish.apinspector.openapi.request.bodyContents
import work.foofish.apinspector.testing.document
import work.foofish.apinspector.testing.operation

class JsonTreeTest {
    private val document =
        document(
            """
            paths:
              /pets:
                post:
                  requestBody:
                    content:
                      application/json:
                        schema: { ${'$'}ref: '#/components/schemas/Pet' }
            components:
              schemas:
                Pet:
                  type: object
                  required: [name]
                  properties:
                    id: { type: integer, readOnly: true }
                    name: { type: string, description: 名字 }
                    kind: { type: string, enum: [cat, dog] }
                    owner:
                      oneOf:
                        - ${'$'}ref: '#/components/schemas/Person'
                        - ${'$'}ref: '#/components/schemas/Company'
                Person:
                  type: object
                  properties: { first: { type: string } }
                Company:
                  type: object
                  properties: { brand: { type: string } }
            """,
        )
    private val schema =
        document
            .operation(OperationKey(HttpMethod.POST, "/pets"))
            .bodyContents
            .getValue("application/json")
            ?.schema

    private fun parse(text: String): JsonNode = assertInstanceOf(JsonTemplateParse.Success::class.java, JsonTemplate.parse(text)).node

    private val name = JsonPath.ROOT.child(JsonStep.Key("name"))
    private val owner = JsonPath.ROOT.child(JsonStep.Key("owner"))

    @Test
    fun `rows follow the schema, listing missing writable fields and an add row`() {
        val rows = JsonTreeBuilder(document, collapsed = emptySet()).build(parse("""{"name": {{petName}}, "extra": 1}"""), schema)

        val root = assertInstanceOf(JsonRow.Container::class.java, rows[0])
        assertEquals(2, root.size)
        val nameRow = assertInstanceOf(JsonRow.Value::class.java, rows[1])
        assertEquals(JsonValueType.VARIABLE, nameRow.type)
        assertEquals("petName", nameRow.text)
        assertEquals(true, nameRow.schema?.required)
        assertEquals("名字", nameRow.schema?.description)
        val extra = assertInstanceOf(JsonRow.Value::class.java, rows[2])
        assertEquals(null, extra.schema)
        assertEquals(listOf("kind", "owner"), rows.filterIsInstance<JsonRow.Missing>().map { it.name })
        assertEquals(
            listOf("cat", "dog"),
            rows
                .filterIsInstance<JsonRow.Missing>()
                .first()
                .schema.choices,
        )
        assertTrue(rows.last() is JsonRow.Add)
    }

    @Test
    fun `collapsed containers show only themselves`() {
        val rows = JsonTreeBuilder(document, collapsed = setOf(JsonPath.ROOT)).build(parse("""{"name": "x"}"""), schema)
        assertEquals(1, rows.size)
        assertEquals(false, assertInstanceOf(JsonRow.Container::class.java, rows.single()).expanded)
    }

    @Test
    fun `oneOf picks the variant matching existing fields`() {
        val rows = JsonTreeBuilder(document, collapsed = emptySet()).build(parse("""{"owner": {"brand": "acme"}}"""), schema)
        val ownerRow = rows.filterIsInstance<JsonRow.Container>().single { it.path == owner }
        assertEquals(listOf("Person", "Company"), ownerRow.variants)
        assertEquals(1, ownerRow.variant)
        assertTrue(rows.filterIsInstance<JsonRow.Missing>().none { it.parent == owner })
    }

    @Test
    fun `schema at a path follows properties and variants`() {
        val root = parse("""{"owner": {"first": "a"}}""")
        val ownerSchema = schemaAt(root, schema, owner, document)
        assertEquals(JsonNode.ObjectNode(listOf(JsonMember("brand", JsonNode.StringNode("")))), variantSample(ownerSchema, 1, document))
    }

    @Test
    fun `edits replace, remove and append nodes`() {
        val root = parse("""{"name": "a", "tags": [1]}""")
        val tags = JsonPath.ROOT.child(JsonStep.Key("tags"))

        val edited =
            root
                .replace(name, JsonNode.StringNode("b"))
                .append(tags, name = null, value = JsonNode.NumberNode("2"))
                .remove(tags.child(JsonStep.Index(0)))
                .append(JsonPath.ROOT, "age", JsonNode.NumberNode("3"))

        assertEquals("""{"name":"b","tags":[2],"age":3}""", JsonTemplate.format(edited).filterNot(Char::isWhitespace))
    }

    @Test
    fun `empty nodes per type`() {
        assertEquals(JsonNode.VariableNode("token"), emptyNode(JsonValueType.VARIABLE, "token"))
        assertEquals(JsonNode.ArrayNode(emptyList()), emptyNode(JsonValueType.ARRAY, "token"))
    }
}
