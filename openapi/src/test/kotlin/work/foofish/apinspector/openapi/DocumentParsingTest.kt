package work.foofish.apinspector.openapi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class DocumentParsingTest {
    @Test
    fun `rejects Swagger 2_0 documents explicitly`() {
        assertEquals(OpenApiParseError.Swagger2Unsupported("2.0"), parseFailure("swagger: '2.0'\ninfo: {title: T, version: '1'}"))
    }

    @Test
    fun `rejects Swagger 2_0 even when the version is an unquoted YAML number`() {
        assertEquals(OpenApiParseError.Swagger2Unsupported("2.0"), parseFailure("swagger: 2.0"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["3.3.0", "2.0", "4.0"])
    fun `rejects versions other than 3_0, 3_1 and 3_2`(version: String) {
        assertEquals(OpenApiParseError.UnsupportedVersion(version), parseFailure(spec("", version = version)))
    }

    @ParameterizedTest
    @ValueSource(strings = ["3.0", "3.0.0", "3.0.4"])
    fun `accepts 3_0 versions`(version: String) {
        assertEquals(OpenApiVersion.V3_0, parseSuccess(spec("", version = version)).document.version)
    }

    @ParameterizedTest
    @ValueSource(strings = ["3.1", "3.1.0", "3.1.1"])
    fun `accepts 3_1 versions`(version: String) {
        assertEquals(OpenApiVersion.V3_1, parseSuccess(spec("", version = version)).document.version)
    }

    @ParameterizedTest
    @ValueSource(strings = ["3.2", "3.2.0"])
    fun `accepts 3_2 versions`(version: String) {
        assertEquals(OpenApiVersion.V3_2, parseSuccess(spec("", version = version)).document.version)
    }

    @Test
    fun `rejects documents that are neither OpenAPI nor Swagger`() {
        assertEquals(OpenApiParseError.NotOpenApi, parseFailure("name: something else"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "- a\n- b", "just text", "[1, 2]"])
    fun `rejects documents whose root is not an object`(source: String) {
        assertEquals(OpenApiParseError.RootNotObject, parseFailure(source))
    }

    @Test
    fun `reports syntax errors with their position`() {
        val error = assertInstanceOf(OpenApiParseError.Syntax::class.java, parseFailure("openapi: 3.1.0\ninfo: [unclosed"))
        assertEquals(SourceFormat.YAML, error.format)
        assertEquals(2, error.position?.line)
    }

    @Test
    fun `parses JSON documents`() {
        val document = parseSuccess("""{"openapi": "3.1.0", "info": {"title": "JSON", "version": "2"}}""").document
        assertEquals(Info(title = "JSON", version = "2", summary = null, description = null), document.info)
    }

    @Test
    fun `requires info`() {
        assertEquals(OpenApiParseError.MissingRequiredField("#/info"), parseFailure("openapi: 3.1.0"))
    }

    @Test
    fun `requires info title`() {
        assertEquals(OpenApiParseError.MissingRequiredField("#/info/title"), parseFailure("openapi: 3.1.0\ninfo:\n  version: '1'"))
    }

    @Test
    fun `tolerates a missing info version with a warning`() {
        val result = parseSuccess("openapi: 3.1.0\ninfo:\n  title: T")
        assertEquals(null, result.document.info.version)
        assertEquals(listOf(ParseWarning.MissingField("#/info", "version")), result.warnings)
    }

    @Test
    fun `defaults to a single root server when none is declared`() {
        assertEquals(
            listOf(Server(url = "/", name = null, description = null, variables = emptyMap())),
            parseSuccess(spec("")).document.servers,
        )
    }

    @Test
    fun `skips servers without url and variables without default`() {
        val result =
            parseSuccess(
                spec(
                    """
                    servers:
                      - description: no url
                      - url: https://{env}.example
                        variables:
                          env:
                            enum: [a, b]
                    """,
                ),
            )
        assertEquals(
            listOf(Server(url = "https://{env}.example", name = null, description = null, variables = emptyMap())),
            result.document.servers,
        )
        assertEquals(
            listOf(ParseWarning.MissingField("#/servers/0", "url"), ParseWarning.MissingField("#/servers/1/variables/env", "default")),
            result.warnings,
        )
    }

    @Test
    fun `ignores fields of the wrong type with a warning`() {
        val result = parseSuccess(spec("tags: not-a-list"))
        assertEquals(emptyList<Tag>(), result.document.tags)
        assertEquals(listOf(ParseWarning.UnexpectedType("#/tags", JsonType.ARRAY)), result.warnings)
    }
}
