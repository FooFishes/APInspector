package work.foofish.apinspector.openapi.request

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import work.foofish.apinspector.openapi.HttpMethod

class CurlParserTest {
    private fun import(command: String): CurlImport = (Curl.parse(command) as CurlParse.Success).import

    private fun error(command: String): CurlError = (Curl.parse(command) as CurlParse.Failure).error

    @Test
    fun `splits words with quotes, escapes and continuations`() {
        assertEquals(
            listOf("a b", "c\"d", "it's", "x\ny", "", "e f", "gh"),
            ShellWords.split("'a b' \"c\\\"d\" 'it'\\''s' \$'x\\ny' '' e\\ f g\\\nh"),
        )
        assertNull(ShellWords.split("curl 'abc"))
        assertNull(ShellWords.split("curl \"abc"))
    }

    @Test
    fun `plain url is a GET`() {
        val result = import("curl https://example.com/a?b=1")

        assertEquals(HttpMethod.GET, result.method)
        assertEquals("https://example.com/a?b=1", result.url)
        assertEquals(emptyList<Field>(), result.headers)
        assertNull(result.body)
    }

    @Test
    fun `json body with headers and explicit method`() {
        val result =
            import(
                """
                curl -X PUT 'https://example.com/pets/1' \
                  -H 'Content-Type: application/json; charset=utf-8' \
                  -H 'X-Trace: abc' \
                  --data-raw '{"name":"it'\''s"}' --compressed
                """.trimIndent(),
            )

        assertEquals(HttpMethod.PUT, result.method)
        assertEquals(listOf(Field("X-Trace", "abc")), result.headers)
        assertEquals(BodyInput.Json("application/json; charset=utf-8", """{"name":"it's"}"""), result.body)
        assertEquals(emptyList<String>(), result.ignored)
    }

    @Test
    fun `data without content type is a form and implies POST`() {
        val result = import("curl example.com -d a=1 -d 'b=x+y%21' --data-urlencode 'c=p q'")

        assertEquals(HttpMethod.POST, result.method)
        assertEquals("example.com", result.url)
        assertEquals(
            BodyInput.Form("application/x-www-form-urlencoded", listOf(Field("a", "1"), Field("b", "x y!"), Field("c", "p q"))),
            result.body,
        )
    }

    @Test
    fun `get flag moves data into the query`() {
        val result = import("curl -G https://example.com/search?x=1 -d q=cat --data-urlencode 'tag=a b'")

        assertEquals(HttpMethod.GET, result.method)
        assertEquals("https://example.com/search?x=1&q=cat&tag=a+b", result.url)
        assertNull(result.body)
    }

    @Test
    fun `multipart fields and files`() {
        val result = import("curl https://example.com/upload -F name=doggie -F 'file=@/tmp/a.png;type=image/png'")

        assertEquals(HttpMethod.POST, result.method)
        assertEquals(
            BodyInput.Multipart(
                "multipart/form-data",
                listOf(MultipartPart("name", PartValue.Text("doggie")), MultipartPart("file", PartValue.File(file = null))),
            ),
            result.body,
        )
    }

    @Test
    fun `user, agent, referer and cookie become headers`() {
        val result = import("curl -u alice:secret -A UA/1 -e https://ref.example -b 'a=1; b=2' https://example.com")

        assertEquals(
            listOf(
                Field("Authorization", "Basic YWxpY2U6c2VjcmV0"),
                Field("User-Agent", "UA/1"),
                Field("Referer", "https://ref.example"),
                Field("Cookie", "a=1; b=2"),
            ),
            result.headers,
        )
    }

    @Test
    fun `json option sets content type and accept`() {
        val result = import("""curl --json '{"a":1}' https://example.com""")

        assertEquals(BodyInput.Json("application/json", """{"a":1}"""), result.body)
        assertEquals(listOf(Field("Accept", "application/json")), result.headers)
    }

    @Test
    fun `combined and attached short options`() {
        val result = import("curl -sSLk -XDELETE -H'X-A: 1' https://example.com/1")

        assertEquals(HttpMethod.DELETE, result.method)
        assertEquals(listOf(Field("X-A", "1")), result.headers)
        assertEquals(listOf("-L", "-k"), result.ignored)
    }

    @Test
    fun `unknown options are reported and valued ones swallow their value`() {
        val result = import("curl --insecure -o out.json --max-time 5 -x http://proxy:8080 --url https://example.com https://other.example")

        assertEquals("https://example.com", result.url)
        assertEquals(listOf("--insecure", "-o", "--max-time", "-x", "https://other.example"), result.ignored)
    }

    @Test
    fun `other content types are text and content type stays without a body`() {
        assertEquals(
            BodyInput.Text("text/xml", "<a/>"),
            import("curl https://example.com -H 'content-type: text/xml' -d '<a/>'").body,
        )
        assertEquals(
            listOf(Field("Content-Type", "text/plain")),
            import("curl https://example.com -H 'Content-Type: text/plain'").headers,
        )
    }

    @Test
    fun `head flag and prompt prefix`() {
        assertEquals(HttpMethod.HEAD, import("$ /usr/bin/curl -I https://example.com").method)
    }

    @Test
    fun `reports failures`() {
        assertEquals(CurlError.NOT_CURL, error("wget https://example.com"))
        assertEquals(CurlError.NOT_CURL, error("   "))
        assertEquals(CurlError.NO_URL, error("curl -X POST -H 'a: b'"))
        assertEquals(CurlError.UNTERMINATED_QUOTE, error("curl 'https://example.com"))
        assertEquals(CurlError.UNKNOWN_METHOD, error("curl -X 'GE T' https://example.com"))
    }
}
