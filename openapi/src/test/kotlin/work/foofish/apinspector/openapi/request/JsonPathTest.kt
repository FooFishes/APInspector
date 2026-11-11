package work.foofish.apinspector.openapi.request

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class JsonPathTest {
    private val json =
        """
        {"data":{"token":"abc","count":3,"ok":true,"none":null,
          "items":[{"id":1,"name":"a"},{"id":2,"name":"b"}],
          "odd key":{"it's":"x"}},
         "id":0}
        """.trimIndent()

    private fun extract(path: String): String? = checkNotNull(JsonPath.parse(path)) { path }.extract(json)

    @Test
    fun `extracts members and renders values`() {
        assertEquals("abc", extract("$.data.token"))
        assertEquals("3", extract("$.data.count"))
        assertEquals("true", extract("$.data.ok"))
        assertEquals("null", extract("$.data.none"))
        assertEquals("""{"id":1,"name":"a"}""", extract("$.data.items[0]"))
        assertNull(extract("$.data.missing"))
        assertNull(extract("$.data.token.deeper"))
    }

    @Test
    fun `indexes arrays from both ends`() {
        assertEquals("b", extract("$.data.items[1].name"))
        assertEquals("b", extract("$.data.items[-1].name"))
        assertNull(extract("$.data.items[2]"))
        assertNull(extract("$.data.items[-3]"))
    }

    @Test
    fun `bracket names, wildcards and recursive descent`() {
        assertEquals("x", extract("$.data['odd key']['it\\'s']"))
        assertEquals("x", extract("$[\"data\"][\"odd key\"][\"it's\"]"))
        assertEquals("1", extract("$.data.items[*].id"))
        assertEquals("abc", extract("$.data.*"))
        assertEquals("a", extract("$..name"))
        assertEquals("0", extract("$..id"))
        assertEquals("x", extract("$..['it\\'s']"))
    }

    @Test
    fun `root may be omitted`() {
        assertEquals("abc", extract("data.token"))
        assertEquals("abc", extract(".data.token"))
        assertEquals("2", JsonPath.parse("[1]")?.extract("[1,2]"))
        assertEquals("[1,2]", JsonPath.parse("$")?.extract("[1, 2]"))
    }

    @Test
    fun `normalizes its text`() {
        assertEquals("$.data['odd key'][0][*]..name", JsonPath.parse(" data[\"odd key\"][0].*..name ").toString())
        assertEquals("$..['a b']", JsonPath.parse("$..['a b']").toString())
        assertEquals(JsonPath.parse("a.b"), JsonPath.parse("$['a'].b"))
    }

    @Test
    fun `rejects malformed paths and invalid json`() {
        listOf("", "$.", "$..", "$[", "$[abc]", "$['a'", "$['a", "$.a[1", "$ x", "$.a..").forEach { assertNull(JsonPath.parse(it), it) }
        assertNull(JsonPath.parse("$.a")?.extract("not json"))
    }
}
