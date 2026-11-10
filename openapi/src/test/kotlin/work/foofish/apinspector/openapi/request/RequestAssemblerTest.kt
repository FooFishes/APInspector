package work.foofish.apinspector.openapi.request

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import work.foofish.apinspector.openapi.ApiKeyLocation
import work.foofish.apinspector.openapi.HttpMethod
import work.foofish.apinspector.openapi.ParameterLocation
import work.foofish.apinspector.openapi.operation
import work.foofish.apinspector.openapi.parseSuccess
import work.foofish.apinspector.openapi.spec

class RequestAssemblerTest {
    private val document =
        parseSuccess(
            spec(
                """
                security:
                  - key: []
                  - token: []
                paths:
                  /users/{id}:
                    get:
                      parameters:
                        - { name: id, in: path, required: true, schema: { type: string } }
                        - { name: verbose, in: query, schema: { type: boolean } }
                        - { name: X-Trace, in: header, schema: { type: string } }
                        - { name: session, in: cookie, schema: { type: string } }
                      responses:
                        '200': { description: OK, content: { application/json: {}, text/plain: {} } }
                        '404': { description: Missing, content: { application/json: {} } }
                    put:
                      security: [{}, { basic: [] }]
                      parameters:
                        - { name: id, in: path, required: true, schema: { type: string } }
                        - { name: Authorization, in: header, required: true, schema: { type: string } }
                      requestBody:
                        content:
                          application/x-www-form-urlencoded: {}
                          application/octet-stream: {}
                components:
                  securitySchemes:
                    key: { type: apiKey, name: api_key, in: query }
                    token: { type: http, scheme: bearer }
                    basic: { type: http, scheme: basic }
                """,
            ),
        ).document

    private val get = document.operation(HttpMethod.GET, "/users/{id}")
    private val put = document.operation(HttpMethod.PUT, "/users/{id}")
    private val assembler = RequestAssembler(document, userAgent = "APInspector/test")

    private fun environment(
        credentials: Map<String, Credential> = emptyMap(),
        variables: Map<String, String> = mapOf("host" to "api.example.com"),
    ) = RequestEnvironment("https://{{host}}/v1/", variables, credentials)

    private fun RequestInput.with(
        name: String,
        value: String,
        enabled: Boolean = true,
    ) = copy(parameters = parameters.map { if (it.name == name) it.copy(value = ParameterValue.Text(value), enabled = enabled) else it })

    private fun ready(assembly: Assembly): Assembly.Ready = assertInstanceOf(Assembly.Ready::class.java, assembly)

    @Test
    fun `builds the url from base url, path and enabled parameters only`() {
        val input =
            get
                .defaultInput(document)
                .with("id", "a b")
                .with("verbose", "true", enabled = false)
                .copy(extras = listOf(ExtraParameter(ExtraLocation.QUERY, "page", "{{host}}", enabled = true)))

        val request = ready(assembler.assemble(get, input, environment())).request

        assertEquals("https://api.example.com/v1/users/a%20b?page=api.example.com", request.url)
        assertEquals(HttpMethod.GET, request.method)
        assertNull(request.body)
    }

    @Test
    fun `adds Accept and User-Agent, manual headers override them`() {
        val input =
            get.defaultInput(document).with("id", "1").with("X-Trace", "t").copy(
                extras = listOf(ExtraParameter(ExtraLocation.HEADER, "user-agent", "curl", enabled = true)),
            )

        val headers = ready(assembler.assemble(get, input, environment())).request.headers

        assertEquals(listOf(Field("Accept", "application/json, text/plain"), Field("X-Trace", "t"), Field("user-agent", "curl")), headers)
    }

    @Test
    fun `injects the first security requirement whose credentials are all filled`() {
        val input = get.defaultInput(document).with("id", "1")

        val withToken =
            ready(
                assembler.assemble(
                    get,
                    input,
                    environment(
                        mapOf(
                            "token" to Credential.Bearer("{{host}}"),
                            "key" to Credential.ApiKey("", "api_key", ApiKeyLocation.QUERY),
                        ),
                    ),
                ),
            )
        assertEquals(Field("Authorization", "Bearer api.example.com"), withToken.request.headers[2])

        val withKey =
            ready(
                assembler.assemble(
                    get,
                    input,
                    environment(
                        mapOf(
                            "token" to Credential.Bearer("t"),
                            "key" to Credential.ApiKey("k", "renamed", ApiKeyLocation.COOKIE),
                        ),
                    ),
                ),
            )
        assertEquals(listOf(Field("renamed", "k")), withKey.cookies)
        assertEquals(2, withKey.request.headers.size)
    }

    @Test
    fun `cookie parameters win over auth cookies of the same name`() {
        val input = get.defaultInput(document).with("id", "1").with("session", "mine")
        val ready =
            ready(assembler.assemble(get, input, environment(mapOf("key" to Credential.ApiKey("k", "session", ApiKeyLocation.COOKIE)))))
        assertEquals(listOf(Field("session", "mine")), ready.cookies)
    }

    @Test
    fun `ignored Authorization header is sent only when checked and then overrides auth`() {
        val credentials = mapOf("basic" to Credential.Basic("u", "p"))
        val unchecked = put.defaultInput(document).with("id", "1").with("Authorization", "Custom x", enabled = false)
        val basic = ready(assembler.assemble(put, unchecked, environment(credentials))).request.headers
        assertEquals(Field("Authorization", "Basic dTpw"), basic.single { it.name == "Authorization" })

        val checked = unchecked.with("Authorization", "Custom x", enabled = true)
        val custom = ready(assembler.assemble(put, checked, environment(credentials))).request.headers
        assertEquals(listOf(Field("Authorization", "Custom x")), custom.filter { it.name == "Authorization" })
    }

    @Test
    fun `form body is encoded after substituting variables`() {
        val input =
            put
                .defaultInput(
                    document,
                ).with("id", "1")
                .copy(body = BodyInput.Form("application/x-www-form-urlencoded", listOf(Field("q", "{{host}} &"))))

        val body = ready(assembler.assemble(put, input, environment())).request.body

        assertEquals(HttpBody.Text("application/x-www-form-urlencoded", "q=api.example.com+%26"), body)
    }

    @Test
    fun `manual Content-Type replaces the body type`() {
        val input =
            put.defaultInput(document).with("id", "1").copy(
                body = BodyInput.Binary("application/octet-stream", FileRef("content://f", "f.bin", size = 3, mediaType = null)),
                extras = listOf(ExtraParameter(ExtraLocation.HEADER, "content-type", "image/png", enabled = true)),
            )

        val request = ready(assembler.assemble(put, input, environment())).request

        assertEquals("image/png", (request.body as HttpBody.File).contentType)
        assertEquals(emptyList<Field>(), request.headers.filter { it.name.equals("content-type", ignoreCase = true) })
    }

    @Test
    fun `reports missing variables, incomplete base url and missing files together`() {
        val input = put.defaultInput(document).with("id", "{{id}}").copy(body = BodyInput.Binary("application/octet-stream", file = null))

        val blocked =
            assertInstanceOf(
                Assembly.Blocked::class.java,
                assembler.assemble(put, input, RequestEnvironment("/{{prefix}}", emptyMap(), emptyMap())),
            )

        assertEquals(
            listOf(
                AssemblyProblem.MissingVariables(listOf("prefix", "id")),
                AssemblyProblem.IncompleteBaseUrl("/{{prefix}}"),
                AssemblyProblem.FileNotSelected(part = null),
            ),
            blocked.problems,
        )
    }

    @Test
    fun `missing parameter inputs fall back to document defaults`() {
        val input = RequestInput(parameters = emptyList(), extras = emptyList(), body = null)
        val request = ready(assembler.assemble(get, input, environment())).request
        assertEquals("https://api.example.com/v1/users/", request.url)
        assertEquals(ParameterLocation.PATH, get.editableParameters.first().location)
    }
}
