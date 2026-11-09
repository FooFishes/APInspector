package work.foofish.apinspector.openapi

import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class SchemaParsingTest {
    /** 把 [schema] 作为 `GET /probe` 的 200 响应体 Schema 解析，[components] 作为 `components.schemas`。 */
    private fun probe(
        schema: String,
        components: String = "{}",
        version: String = "3.1.0",
    ): Pair<Schema, OpenApiParseResult.Success> {
        val result =
            parseSuccess(
                spec(
                    """
                    |paths:
                    |  /probe:
                    |    get:
                    |      responses:
                    |        '200':
                    |          description: ok
                    |          content:
                    |            application/json:
                    |              schema:
                    |${schema.trimIndent().prependIndent(" ".repeat(16))}
                    |components:
                    |  schemas:
                    |${components.trimIndent().prependIndent(" ".repeat(4))}
                    """.trimMargin(),
                    version = version,
                ),
            )
        val response =
            result.document
                .operation(HttpMethod.GET, "/probe")
                .responses
                .getValue("200")
                .resolved()
        return checkNotNull(
            response.content
                .getValue("application/json")
                .resolved()
                .schema,
        ) to result
    }

    private fun probeDefinition(
        schema: String,
        version: String = "3.1.0",
    ): Schema.Definition = assertInstanceOf(Schema.Definition::class.java, probe(schema, version = version).first)

    @Test
    fun `keeps references and registers their targets transitively`() {
        val (schema, result) =
            probe(
                schema = $$"$ref: '#/components/schemas/User'",
                components =
                    $$"""
                    User:
                      type: object
                      properties:
                        address:
                          $ref: '#/components/schemas/Address'
                    Address:
                      type: string
                    Unused:
                      type: string
                    """,
            )
        assertEquals(Schema.Reference("#/components/schemas/User", "User", description = null), schema)
        assertEquals(setOf("#/components/schemas/User", "#/components/schemas/Address"), result.document.schemas.keys)
        assertEquals(
            setOf(SchemaType.STRING),
            (result.document.schemas.getValue("#/components/schemas/Address") as Schema.Definition).types,
        )
    }

    @Test
    fun `parses self-referencing schemas without expanding them`() {
        val (schema, result) =
            probe(
                schema = $$"$ref: '#/components/schemas/Node'",
                components =
                    $$"""
                    Node:
                      type: object
                      properties:
                        children:
                          type: array
                          items:
                            $ref: '#/components/schemas/Node'
                    """,
            )
        val node = assertInstanceOf(Schema.Definition::class.java, result.document.dereference(schema))
        val children = node.properties.getValue("children") as Schema.Definition
        assertEquals(schema, children.items)
        assertEquals(emptyList<ParseWarning>(), result.warnings)
    }

    @Test
    fun `dereferencing a pure reference cycle yields a circular marker`() {
        val (schema, result) =
            probe(
                schema = $$"$ref: '#/components/schemas/A'",
                components =
                    $$"""
                    A:
                      $ref: '#/components/schemas/B'
                    B:
                      $ref: '#/components/schemas/A'
                    """,
            )
        assertEquals(Schema.Unresolved("#/components/schemas/A", UnresolvedReason.CIRCULAR), result.document.dereference(schema))
    }

    @Test
    fun `marks external and missing schema references as unresolved`() {
        val (schema, result) =
            probe(
                schema =
                    $$"""
                    oneOf:
                      - $ref: 'models.json#/Pet'
                      - $ref: '#/components/schemas/Missing'
                    """,
            )
        assertEquals(
            listOf(
                Schema.Unresolved("models.json#/Pet", UnresolvedReason.EXTERNAL),
                Schema.Unresolved("#/components/schemas/Missing", UnresolvedReason.NOT_FOUND),
            ),
            (schema as Schema.Definition).oneOf,
        )
        assertEquals(2, result.warnings.filterIsInstance<ParseWarning.UnresolvedReference>().size)
    }

    @Test
    fun `canonicalizes percent-encoded references`() {
        val (schema, result) = probe(schema = $$"$ref: '#/components/schemas/Pet%20Food'", components = "Pet Food: {type: string}")
        assertEquals(Schema.Reference("#/components/schemas/Pet Food", "Pet Food", description = null), schema)
        assertEquals(setOf("#/components/schemas/Pet Food"), result.document.schemas.keys)
    }

    @Test
    fun `keeps a reference description only in 3_1`() {
        val ref = $$"{$ref: '#/components/schemas/S', description: overridden}"
        assertEquals("overridden", (probe(ref, "S: {type: string}", version = "3.1.0").first as Schema.Reference).description)
        assertEquals(null, (probe(ref, "S: {type: string}", version = "3.0.3").first as Schema.Reference).description)
    }

    @Test
    fun `normalizes 3_0 nullable only when a type is declared`() {
        assertEquals(setOf(SchemaType.STRING, SchemaType.NULL), probeDefinition("{type: string, nullable: true}", version = "3.0.3").types)
        assertEquals(emptySet<SchemaType>(), probeDefinition("{nullable: true}", version = "3.0.3").types)
    }

    @Test
    fun `reads 3_1 type arrays and warns about unknown types`() {
        val (schema, result) = probe("{type: [string, 'null', str]}")
        assertEquals(setOf(SchemaType.STRING, SchemaType.NULL), (schema as Schema.Definition).types)
        assertEquals(
            listOf(ParseWarning.InvalidValue("#/paths/~1probe/get/responses/200/content/application~1json/schema/type/2", "\"str\"")),
            result.warnings,
        )
    }

    @Test
    fun `normalizes both styles of exclusive bounds`() {
        val legacy = probeDefinition("{type: number, minimum: 0, exclusiveMinimum: true, maximum: 10}", version = "3.0.3")
        assertEquals(NumericBound(BigDecimal.ZERO, exclusive = true), legacy.minimum)
        assertEquals(NumericBound(BigDecimal.TEN, exclusive = false), legacy.maximum)

        val modern = probeDefinition("{type: number, exclusiveMinimum: 0.10, maximum: 10, exclusiveMaximum: 5}")
        assertEquals(NumericBound(BigDecimal("0.10"), exclusive = true), modern.minimum)
        assertEquals(NumericBound(BigDecimal(5), exclusive = true), modern.maximum)
    }

    @Test
    fun `maps boolean schemas to definitions`() {
        val schema = probeDefinition("{type: object, additionalProperties: false, items: true}")
        assertEquals(Schema.Definition(not = Schema.Definition()), schema.additionalProperties)
        assertEquals(Schema.Definition(), schema.items)
    }

    @Test
    fun `folds const into enum values and merges examples`() {
        assertEquals(listOf(JsonPrimitive("fixed")), probeDefinition("{const: fixed}").enumValues)
        assertEquals(listOf(JsonPrimitive(1), JsonPrimitive(2)), probeDefinition("{enum: [1, 2], const: 3}").enumValues)
        assertEquals(
            listOf(JsonPrimitive("a"), JsonPrimitive("b"), JsonPrimitive("c")),
            probeDefinition("{example: a, examples: [b, c]}").examples,
        )
    }

    @Test
    fun `reads constraints and flags`() {
        val schema =
            probeDefinition(
                """
                type: string
                title: Code
                format: uuid
                minLength: 1
                maxLength: 36
                pattern: '^[a-f0-9-]+$'
                default: abc
                readOnly: true
                deprecated: true
                """,
            )
        assertEquals(
            Schema.Definition(
                title = "Code",
                types = setOf(SchemaType.STRING),
                format = "uuid",
                minLength = 1,
                maxLength = 36,
                pattern = "^[a-f0-9-]+$",
                defaultValue = JsonPrimitive("abc"),
                readOnly = true,
                deprecated = true,
            ),
            schema,
        )
    }

    @Test
    fun `keeps property order and required names`() {
        val schema = probeDefinition("{type: object, required: [b], properties: {b: {}, a: {}, c: {}}}")
        assertEquals(listOf("b", "a", "c"), schema.properties.keys.toList())
        assertEquals(setOf("b"), schema.required)
    }
}
