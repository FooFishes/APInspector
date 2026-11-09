package work.foofish.apinspector.openapi.parse

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import work.foofish.apinspector.openapi.OpenApiParseError
import work.foofish.apinspector.openapi.SourceFormat
import work.foofish.apinspector.openapi.TextPosition

class SourceReaderTest {
    private fun failure(source: String): OpenApiParseError = assertThrows<ParseFailure> { SourceReader.read(source) }.error

    @Test
    fun `reads JSON`() {
        assertEquals(buildJsonObject { put("a", 1) }, SourceReader.read("""{"a": 1}"""))
    }

    @Test
    fun `ignores a leading byte order mark`() {
        assertEquals(buildJsonObject { put("a", 1) }, SourceReader.read("{\"a\": 1}"))
    }

    @Test
    fun `types YAML scalars by the 1_2 core schema`() {
        val yaml =
            """
            quoted: "123"
            integer: 123
            hex: 0x1F
            octal: 0o17
            flag: True
            nothing: ~
            empty:
            plain: hello
            """.trimIndent()
        val values = SourceReader.read(yaml).jsonObject
        assertEquals(JsonPrimitive("123"), values["quoted"])
        assertEquals(JsonPrimitive(123), values["integer"])
        assertEquals(JsonPrimitive(31), values["hex"])
        assertEquals(JsonPrimitive(15), values["octal"])
        assertEquals(JsonPrimitive(true), values["flag"])
        assertEquals(JsonNull, values["nothing"])
        assertEquals(JsonNull, values["empty"])
        assertEquals(JsonPrimitive("hello"), values["plain"])
    }

    @Test
    fun `keeps the exact text of YAML decimals`() {
        val decimal =
            SourceReader
                .read("version: 1.10")
                .jsonObject
                .getValue("version")
                .jsonPrimitive
        assertEquals("1.10", decimal.content)
        assertFalse(decimal.isString)
    }

    @Test
    fun `keeps non-finite YAML floats as text`() {
        assertEquals(JsonPrimitive(".inf"), SourceReader.read("x: .inf").jsonObject["x"])
    }

    @Test
    fun `converts numeric YAML keys to strings`() {
        assertEquals(
            listOf("200", "404"),
            SourceReader
                .read("200: ok\n404: missing")
                .jsonObject.keys
                .toList(),
        )
    }

    @Test
    fun `shares the converted value of an anchor between its aliases`() {
        val values = SourceReader.read("a: &shared {k: v}\nb: *shared").jsonObject
        assertSame(values["a"], values["b"])
    }

    @Test
    fun `rejects recursive aliases`() {
        assertInstanceOf(OpenApiParseError.RecursiveAlias::class.java, failure("a: &self [1, *self]"))
    }

    @Test
    fun `rejects duplicate keys with their position`() {
        assertEquals(OpenApiParseError.DuplicateKey("a", TextPosition(line = 2, column = 1)), failure("a: 1\na: 2"))
    }

    @Test
    fun `rejects non-scalar keys as syntax errors`() {
        assertInstanceOf(OpenApiParseError.Syntax::class.java, failure("? [a, b]\n: c"))
    }

    @Test
    fun `reports YAML syntax errors with their position`() {
        val error = assertInstanceOf(OpenApiParseError.Syntax::class.java, failure("a: 1\nb: [2, 3\nc: 4"))
        assertEquals(SourceFormat.YAML, error.format)
        assertEquals(true, error.detail.isNotBlank())
        assertEquals(true, error.position != null)
    }

    @Test
    fun `reports JSON syntax errors`() {
        val error = assertInstanceOf(OpenApiParseError.Syntax::class.java, failure("""{"a": }"""))
        assertEquals(SourceFormat.JSON, error.format)
    }

    @Test
    fun `reads YAML sequences`() {
        assertEquals(JsonArray(listOf(JsonPrimitive(1), JsonPrimitive("two"))), SourceReader.read("- 1\n- two"))
    }
}
