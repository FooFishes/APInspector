package work.foofish.apinspector.openapi.request

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VariablesTest {
    @Test
    fun `finds references in order, ignoring invalid names`() {
        assertEquals(
            setOf("host", "user.id", "a-b_c"),
            Variables.references("{{host}}/{{user.id}}?x={{a-b_c}}&y={{host}}&z={{ spaced }}&w={{}}"),
        )
    }

    @Test
    fun `substitutes defined values and reports undefined or empty ones`() {
        val missing = LinkedHashSet<String>()
        val result = Variables.substitute("{{a}}-{{b}}-{{c}}-{{not valid}}", mapOf("a" to "1", "b" to ""), missing)

        assertEquals("1-{{b}}-{{c}}-{{not valid}}", result)
        assertEquals(listOf("b", "c"), missing.toList())
    }

    @Test
    fun `values are inserted verbatim without re-substitution`() {
        val missing = HashSet<String>()
        assertEquals("{{b}}", Variables.substitute("{{a}}", mapOf("a" to "{{b}}", "b" to "x"), missing))
        assertTrue(missing.isEmpty())
    }

    @Test
    fun `transforms only the literal parts`() {
        assertEquals("A{{x}}B", Variables.mapLiterals("a{{x}}b") { it.uppercase() })
    }

    @Test
    fun `validates names`() {
        assertTrue(Variables.isValidName("base.url-2_x"))
        assertFalse(Variables.isValidName("has space"))
        assertFalse(Variables.isValidName(""))
    }
}
