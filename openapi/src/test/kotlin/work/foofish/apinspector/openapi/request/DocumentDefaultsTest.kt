package work.foofish.apinspector.openapi.request

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import work.foofish.apinspector.openapi.HttpMethod
import work.foofish.apinspector.openapi.ParameterLocation
import work.foofish.apinspector.openapi.operation
import work.foofish.apinspector.openapi.parseSuccess
import work.foofish.apinspector.openapi.spec

class DocumentDefaultsTest {
    private val document =
        parseSuccess(
            spec(
                """
                paths:
                  /pets/{id}:
                    post:
                      parameters:
                        - { name: id, in: path, required: true, schema: { type: integer }, example: 7 }
                        - name: sort
                          in: query
                          schema: { type: string, default: name, examples: [age] }
                        - name: kind
                          in: query
                          schema: { type: string, enum: [cat] }
                        - name: tags
                          in: query
                          schema: { type: array, items: { type: string, enum: [a, b] } }
                          examples: { first: { value: [a, b] } }
                        - name: filter
                          in: query
                          style: deepObject
                          schema: { type: object }
                          example: { color: red }
                        - { name: Accept, in: header, required: true, schema: { type: string } }
                      requestBody:
                        content:
                          application/json:
                            schema: { ${'$'}ref: '#/components/schemas/Pet' }
                          multipart/form-data:
                            schema:
                              properties:
                                photo: { type: string, format: binary }
                                caption: { type: string }
                components:
                  schemas:
                    Pet:
                      type: object
                      properties:
                        id: { type: integer, readOnly: true }
                        name: { type: string, example: Rex }
                        tags: { type: array, items: { type: string } }
                        parent: { ${'$'}ref: '#/components/schemas/Pet' }
                """,
            ),
        ).document

    private val operation = document.operation(HttpMethod.POST, "/pets/{id}")
    private val input = operation.defaultInput(document)

    private fun value(name: String): ParameterInput = input.parameters.single { it.name == name }

    @Test
    fun `parameter defaults follow the documented precedence`() {
        assertEquals(ParameterValue.Text("7"), value("id").value)
        assertEquals(ParameterValue.Text("name"), value("sort").value)
        assertEquals(ParameterValue.Text("cat"), value("kind").value)
        assertEquals(ParameterValue.Items(listOf("a", "b")), value("tags").value)
        assertEquals(ParameterValue.Fields(listOf(Field("color", "red"))), value("filter").value)
    }

    @Test
    fun `only required parameters start enabled, ignored headers never do`() {
        assertEquals(listOf("id"), input.parameters.filter { it.enabled }.map { it.name })
        assertEquals(ParameterLocation.HEADER, value("Accept").location)
    }

    @Test
    fun `json body is generated from the schema, skipping readOnly and stopping at cycles`() {
        assertEquals(
            BodyInput.Json(
                "application/json",
                """
                {
                  "name": "Rex",
                  "tags": [
                    ""
                  ],
                  "parent": null
                }
                """.trimIndent(),
            ),
            input.body,
        )
    }

    @Test
    fun `multipart fields with binary format are files`() {
        val body = defaultBody("multipart/form-data", operation.bodyContents.getValue("multipart/form-data"), document)
        assertEquals(
            BodyInput.Multipart(
                "multipart/form-data",
                listOf(MultipartPart("photo", PartValue.File(null)), MultipartPart("caption", PartValue.Text(""))),
            ),
            body,
        )
    }

    @Test
    fun `body kinds follow the content type`() {
        assertEquals(BodyKind.JSON, BodyKind.of("application/vnd.api+json; charset=utf-8"))
        assertEquals(BodyKind.FORM, BodyKind.of("Application/X-WWW-Form-Urlencoded"))
        assertEquals(BodyKind.MULTIPART, BodyKind.of("multipart/mixed"))
        assertEquals(BodyKind.BINARY, BodyKind.of("image/png"))
        assertEquals(BodyKind.TEXT, BodyKind.of("text/xml"))
    }

    @Test
    fun `operations without request body have no body`() {
        val get =
            parseSuccess(spec("paths: { /a: { get: { responses: { '200': { description: OK } } } } }"))
                .document
        assertNull(
            get.operations
                .single()
                .defaultInput(get)
                .body,
        )
    }
}
