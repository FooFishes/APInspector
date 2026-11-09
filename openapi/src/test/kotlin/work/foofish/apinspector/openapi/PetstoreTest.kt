package work.foofish.apinspector.openapi

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/** 用一份接近真实的 OpenAPI 3.0 YAML 文档做端到端校验。 */
class PetstoreTest {
    private val result = parseSuccess(fixture("petstore.yaml"))
    private val document = result.document

    @Test
    fun `parses a well-formed document without warnings`() {
        assertEquals(emptyList<ParseWarning>(), result.warnings)
        assertEquals(OpenApiVersion.V3_0, document.version)
        assertEquals("3.0.3", document.declaredVersion)
    }

    @Test
    fun `reads info, keeping an unquoted YAML version as text`() {
        assertEquals(Info(title = "Petstore", version = "1.0", summary = null, description = "用于测试的宠物商店。"), document.info)
    }

    @Test
    fun `reads servers with variables`() {
        assertEquals(
            listOf(
                Server(
                    url = "https://{region}.petstore.example/v1",
                    name = null,
                    description = "生产环境",
                    variables = mapOf("region" to ServerVariable(defaultValue = "eu", enumValues = listOf("eu", "us"), description = null)),
                ),
                Server(url = "http://localhost:8080/v1", name = null, description = null, variables = emptyMap()),
            ),
            document.servers,
        )
    }

    @Test
    fun `reads tags in declaration order`() {
        assertEquals(
            listOf(
                Tag("pets", summary = null, description = "宠物", parent = null, kind = null),
                Tag("store", summary = null, description = null, parent = null, kind = null),
            ),
            document.tags,
        )
    }

    @Test
    fun `lists operations in document order`() {
        assertEquals(
            listOf(
                OperationKey(HttpMethod.GET, "/pets"),
                OperationKey(HttpMethod.POST, "/pets"),
                OperationKey(HttpMethod.GET, "/pets/{petId}"),
                OperationKey(HttpMethod.DELETE, "/pets/{petId}"),
                OperationKey(HttpMethod.GET, "/health"),
            ),
            document.operations.map(Operation::key),
        )
    }

    @Test
    fun `resolves referenced parameters and keeps inline ones`() {
        val parameters = document.operation(HttpMethod.GET, "/pets").parameters.map { it.resolved() }

        assertEquals(
            listOf("limit" to ParameterLocation.QUERY, "status" to ParameterLocation.QUERY),
            parameters.map {
                it.name to
                    it.location
            },
        )
        val limitSchema = parameters.first().schema as Schema.Definition
        assertEquals(NumericBound(BigDecimal.ONE, exclusive = false), limitSchema.minimum)
        assertEquals(JsonPrimitive(20), limitSchema.defaultValue)
    }

    @Test
    fun `applies path item parameters to every operation under the path`() {
        for (method in listOf(HttpMethod.GET, HttpMethod.DELETE)) {
            val petId =
                document
                    .operation(method, "/pets/{petId}")
                    .parameters
                    .single()
                    .resolved()
            assertEquals("petId", petId.name)
            assertEquals(true, petId.required)
        }
    }

    @Test
    fun `inherits, overrides and clears security requirements`() {
        val bearer = listOf(SecurityRequirement(mapOf("bearerAuth" to emptyList())))
        assertEquals(bearer, document.operation(HttpMethod.GET, "/pets").security)
        assertEquals(
            listOf(SecurityRequirement(mapOf("apiKeyAuth" to emptyList()))),
            document.operation(HttpMethod.DELETE, "/pets/{petId}").security,
        )
        assertEquals(emptyList<SecurityRequirement>(), document.operation(HttpMethod.GET, "/health").security)
    }

    @Test
    fun `reads responses keyed by status, including referenced ones`() {
        val responses = document.operation(HttpMethod.GET, "/pets").responses

        assertEquals(listOf("200", "default"), responses.keys.toList())
        assertEquals(
            listOf("X-Total-Count"),
            responses
                .getValue("200")
                .resolved()
                .headers.keys
                .toList(),
        )
        val error = responses.getValue("default").resolved()
        assertEquals("错误", error.description)
        assertEquals(
            Schema.Reference("#/components/schemas/Error", "Error", null),
            error.content
                .getValue("application/json")
                .resolved()
                .schema,
        )
    }

    @Test
    fun `resolves a referenced request body with its example`() {
        val body = checkNotNull(document.operation(HttpMethod.POST, "/pets").requestBody).resolved()

        assertEquals(true, body.required)
        assertEquals(
            buildJsonObject { put("name", "旺财") },
            body.content
                .getValue("application/json")
                .resolved()
                .example,
        )
    }

    @Test
    fun `registers exactly the referenced schemas`() {
        assertEquals(
            setOf("#/components/schemas/Pet", "#/components/schemas/NewPet", "#/components/schemas/Error"),
            document.schemas.keys,
        )
    }

    @Test
    fun `normalizes nullable into the type set`() {
        val newPet = document.schemas.getValue("#/components/schemas/NewPet") as Schema.Definition
        val tag = newPet.properties.getValue("tag") as Schema.Definition
        assertEquals(setOf(SchemaType.STRING, SchemaType.NULL), tag.types)
    }

    @Test
    fun `reads security schemes`() {
        assertEquals(
            mapOf(
                "bearerAuth" to
                    Resolvable.Resolved(
                        SecurityScheme.Http(scheme = "bearer", bearerFormat = "JWT", description = null, deprecated = false),
                    ),
                "apiKeyAuth" to
                    Resolvable.Resolved(
                        SecurityScheme.ApiKey(name = "X-API-Key", location = ApiKeyLocation.HEADER, description = null, deprecated = false),
                    ),
            ),
            document.securitySchemes,
        )
    }
}
