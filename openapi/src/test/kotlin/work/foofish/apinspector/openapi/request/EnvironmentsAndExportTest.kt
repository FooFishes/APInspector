package work.foofish.apinspector.openapi.request

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import work.foofish.apinspector.openapi.HttpMethod
import work.foofish.apinspector.openapi.parseSuccess
import work.foofish.apinspector.openapi.spec

class EnvironmentsAndExportTest {
    @Test
    fun `servers become environments with variables, relative urls kept`() {
        val document =
            parseSuccess(
                spec(
                    """
                    servers:
                      - url: https://{region}.example.com:{port}/v1
                        description: Production
                        variables:
                          region: { default: eu, enum: [eu, us] }
                          port: { default: '443' }
                      - url: /api
                        description: Production
                    paths: {}
                    """,
                ),
            ).document

        assertEquals(
            listOf(
                EnvironmentTemplate(
                    "Production",
                    "https://{{region}}.example.com:{{port}}/v1",
                    listOf(EnvironmentVariable("region", "eu", listOf("eu", "us")), EnvironmentVariable("port", "443")),
                ),
                EnvironmentTemplate("Production (2)", "/api", emptyList()),
            ),
            document.environmentTemplates(),
        )
    }

    @Test
    fun `absolute http urls are complete`() {
        assertTrue(isAbsoluteHttpUrl("HTTPS://example.com"))
        assertTrue(isAbsoluteHttpUrl("http://10.0.2.2:8080/api"))
        assertFalse(isAbsoluteHttpUrl("/api"))
        assertFalse(isAbsoluteHttpUrl("ftp://example.com"))
        assertFalse(isAbsoluteHttpUrl("https://"))
    }

    @Test
    fun `form text keeps variables readable and round-trips`() {
        val fields = listOf(Field("q", "a b&{{token}}"), Field("中", ""))
        val text = FormText.format(fields)

        assertEquals("q=a+b%26{{token}}&%E4%B8%AD=", text)
        assertEquals(fields, FormText.parse(text))
        assertEquals(listOf(Field("flag", ""), Field("x", "100%")), FormText.parse("flag&&x=100%"))
    }

    @Test
    fun `exports curl with quoting`() {
        val request =
            HttpRequest(
                method = HttpMethod.POST,
                url = "https://example.com/a?b=1",
                headers = listOf(Field("Accept", "application/json")),
                body = HttpBody.Text("application/json", """{"name":"it's"}"""),
            )

        assertEquals(
            """
            curl \
              -X 'POST' \
              'https://example.com/a?b=1' \
              -H 'Accept: application/json' \
              -H 'Content-Type: application/json' \
              --data-raw '{"name":"it'\''s"}'
            """.trimIndent(),
            Curl.export(request),
        )
    }

    @Test
    fun `exports multipart parts and omits -X for plain GET`() {
        val file = FileRef("content://x", "photo.png", size = 1, mediaType = "image/png")
        val multipart =
            HttpRequest(
                HttpMethod.POST,
                "https://e.com",
                emptyList(),
                HttpBody.Multipart("multipart/form-data", listOf(HttpPart.Text("a", "1"), HttpPart.File("f", file))),
            )
        assertEquals("curl \\\n  -X 'POST' \\\n  'https://e.com' \\\n  -F 'a=1' \\\n  -F 'f=@\"photo.png\"'", Curl.export(multipart))

        assertEquals("curl \\\n  'https://e.com'", Curl.export(HttpRequest(HttpMethod.GET, "https://e.com", emptyList(), body = null)))
    }
}
