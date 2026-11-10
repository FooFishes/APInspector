package work.foofish.apinspector.openapi.request

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import work.foofish.apinspector.openapi.HttpMethod
import work.foofish.apinspector.openapi.ParameterLocation
import work.foofish.apinspector.openapi.operation
import work.foofish.apinspector.openapi.parseSuccess
import work.foofish.apinspector.openapi.spec

class InputAlignmentTest {
    private val document =
        parseSuccess(
            spec(
                """
                paths:
                  /items/{id}:
                    post:
                      parameters:
                        - { name: id, in: path, required: true, schema: { type: string } }
                        - { name: tags, in: query, schema: { type: array, items: { type: string } } }
                        - { name: limit, in: query, schema: { type: integer, default: 10 } }
                      requestBody:
                        content:
                          text/plain: { example: hello }
                """,
            ),
        ).document

    private val operation = document.operation(HttpMethod.POST, "/items/{id}")

    @Test
    fun `keeps matching values, adds new parameters and turns removed header and query into extras`() {
        val old =
            RequestInput(
                parameters =
                    listOf(
                        ParameterInput(ParameterLocation.PATH, "id", ParameterValue.Text("42"), enabled = true),
                        ParameterInput(ParameterLocation.QUERY, "tags", ParameterValue.Text("x"), enabled = true),
                        ParameterInput(ParameterLocation.HEADER, "X-Old", ParameterValue.Text("h"), enabled = false),
                        ParameterInput(ParameterLocation.PATH, "version", ParameterValue.Text("v1"), enabled = true),
                    ),
                extras = emptyList(),
                body = BodyInput.Json("application/json", "{}"),
            )

        val aligned = old.alignedTo(operation, document)

        assertEquals(
            listOf(
                ParameterInput(ParameterLocation.PATH, "id", ParameterValue.Text("42"), enabled = true),
                ParameterInput(ParameterLocation.QUERY, "tags", ParameterValue.Items(listOf("x")), enabled = true),
                ParameterInput(ParameterLocation.QUERY, "limit", ParameterValue.Text("10"), enabled = false),
            ),
            aligned.parameters,
        )
        assertEquals(listOf(ExtraParameter(ExtraLocation.HEADER, "X-Old", "h", enabled = false)), aligned.extras)
        assertEquals(BodyInput.Text("text/plain", "hello"), aligned.body)
    }

    @Test
    fun `keeps the body when its content type still exists, and no body stays no body`() {
        val body = BodyInput.Text("text/plain", "edited")
        val input = RequestInput(emptyList(), emptyList(), body)
        assertEquals(body, input.alignedTo(operation, document).body)
        assertNull(input.copy(body = null).alignedTo(operation, document).body)
    }
}
