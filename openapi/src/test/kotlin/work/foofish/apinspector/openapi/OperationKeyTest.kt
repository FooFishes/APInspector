package work.foofish.apinspector.openapi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class OperationKeyTest {
    @Test
    fun `renders as method followed by path`() {
        assertEquals("GET /users/{id}", OperationKey(HttpMethod.GET, "/users/{id}").toString())
    }

    @Test
    fun `same path with different methods are different operations`() {
        assertNotEquals(OperationKey(HttpMethod.GET, "/users"), OperationKey(HttpMethod.POST, "/users"))
    }

    @Test
    fun `rejects a path without leading slash`() {
        assertThrows<IllegalArgumentException> { OperationKey(HttpMethod.GET, "users") }
    }
}
