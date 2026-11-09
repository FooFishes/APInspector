package work.foofish.apinspector.openapi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class HttpMethodTest {
    @ParameterizedTest
    @CsvSource(
        "get, GET",
        "put, PUT",
        "post, POST",
        "delete, DELETE",
        "options, OPTIONS",
        "head, HEAD",
        "patch, PATCH",
        "trace, TRACE",
        "query, QUERY",
    )
    fun `resolves every method field defined by the Path Item Object`(
        field: String,
        expected: String,
    ) {
        assertEquals(expected, HttpMethod.fromPathItemField(field)?.name)
    }

    @ParameterizedTest
    @ValueSource(strings = ["GET", "Post", "parameters", "summary", "servers", "additionalOperations", "\$ref", ""])
    fun `rejects fields that are not lowercase method names`(field: String) {
        assertNull(HttpMethod.fromPathItemField(field))
    }

    @Test
    fun `creates custom methods preserving case`() {
        assertEquals("LINK", HttpMethod.of("LINK")?.name)
        assertEquals("Purge", HttpMethod.of("Purge")?.name)
        assertEquals(HttpMethod.GET, HttpMethod.of("GET"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "BAD METHOD", "GET/1", "MÉTHODE"])
    fun `rejects names that are not HTTP tokens`(name: String) {
        assertNull(HttpMethod.of(name))
    }
}
