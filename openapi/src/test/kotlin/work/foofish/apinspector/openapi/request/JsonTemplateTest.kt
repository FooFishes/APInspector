package work.foofish.apinspector.openapi.request

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class JsonTemplateTest {
    private fun parse(text: String): JsonNode = assertInstanceOf(JsonTemplateParse.Success::class.java, JsonTemplate.parse(text)).node

    private fun failure(text: String): JsonTemplateParse.Failure =
        assertInstanceOf(JsonTemplateParse.Failure::class.java, JsonTemplate.parse(text))

    @Test
    fun `variables are valid values, inside strings they stay text`() {
        val node = parse("""{"id": {{id}}, "name": "{{name}}", "tags": [{{a}}, 1]}""")

        assertEquals(
            JsonNode.ObjectNode(
                listOf(
                    JsonMember("id", JsonNode.VariableNode("id")),
                    JsonMember("name", JsonNode.StringNode("{{name}}")),
                    JsonMember("tags", JsonNode.ArrayNode(listOf(JsonNode.VariableNode("a"), JsonNode.NumberNode("1")))),
                ),
            ),
            node,
        )
    }

    @Test
    fun `formats with two-space indent, keeping order, numbers and variables`() {
        val formatted = JsonTemplate.format(parse("""{"b":1.50,"a":[],"c":{},"d":[{{x}},null,true,"q\"\n"]}"""))

        assertEquals(
            """
            {
              "b": 1.50,
              "a": [],
              "c": {},
              "d": [
                {{x}},
                null,
                true,
                "q\"\n"
              ]
            }
            """.trimIndent(),
            formatted,
        )
    }

    @Test
    fun `formatting round-trips`() {
        val text = """{"k": ["é", -1e5, {"n": null}]}"""
        val once = JsonTemplate.format(parse(text))
        assertEquals(once, JsonTemplate.format(parse(once)))
    }

    @Test
    fun `converts from JsonElement`() {
        val element = Json.parseToJsonElement("""{"s":"x","n":2,"b":false,"z":null}""")
        assertEquals(
            """
            {
              "s": "x",
              "n": 2,
              "b": false,
              "z": null
            }
            """.trimIndent(),
            JsonTemplate.format(JsonTemplate.fromElement(element)),
        )
    }

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        textBlock = """
        '   '              | EMPTY                | 1 | 4
        '{"a": }'          | UNEXPECTED_CHARACTER | 1 | 7
        '{"a": 1'          | UNEXPECTED_END       | 1 | 8
        '"abc'             | UNTERMINATED_STRING  | 1 | 1
        '"\x"'             | INVALID_ESCAPE       | 1 | 2
        '01'               | INVALID_NUMBER       | 1 | 1
        '1 2'              | TRAILING_CONTENT     | 1 | 3
        '{{not valid}}'    | UNEXPECTED_CHARACTER | 1 | 1
        '[1,'              | UNEXPECTED_END       | 1 | 4""",
    )
    fun `reports the first error with its position`(
        text: String,
        error: JsonSyntaxError,
        line: Int,
        column: Int,
    ) {
        assertEquals(JsonTemplateParse.Failure(error, line, column), failure(text))
    }

    @Test
    fun `counts lines and columns`() {
        assertEquals(JsonTemplateParse.Failure(JsonSyntaxError.UNEXPECTED_CHARACTER, 3, 8), failure("{\n  \"a\": 1,\n  \"b\": x\n}"))
    }

    @Test
    fun `rejects raw control characters in strings`() {
        assertEquals(JsonSyntaxError.CONTROL_CHARACTER, failure("\"a\nb\"").error)
    }
}
