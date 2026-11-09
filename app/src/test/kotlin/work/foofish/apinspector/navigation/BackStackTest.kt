package work.foofish.apinspector.navigation

import androidx.navigation3.runtime.NavKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import work.foofish.apinspector.openapi.HttpMethod
import work.foofish.apinspector.openapi.OperationKey
import work.foofish.apinspector.spec.SpecId

class BackStackTest {
    private val a = SpecId("a")
    private val b = SpecId("b")
    private val getPets = OperationDocKey(a, OperationKey(HttpMethod.GET, "/pets"))
    private val postPets = OperationDocKey(a, OperationKey(HttpMethod.POST, "/pets"))

    @Test
    fun `opening a spec replaces whatever was open`() {
        val stack = mutableListOf<NavKey>(HomeKey, SpecKey(a), getPets)
        stack.openSpec(b)
        assertEquals(listOf(HomeKey, SpecKey(b)), stack)
        assertEquals(SpecKey(b), stack.currentSpec)
        assertNull(stack.currentOperation)
    }

    @Test
    fun `opening an operation replaces the open one instead of stacking`() {
        val stack = mutableListOf<NavKey>(HomeKey, SpecKey(a))
        stack.openOperation(getPets)
        stack.openOperation(postPets)
        assertEquals(listOf(HomeKey, SpecKey(a), postPets), stack)
        assertEquals(postPets, stack.currentOperation)
    }

    @Test
    fun `operation key round-trips the method`() {
        assertEquals(OperationKey(HttpMethod.QUERY, "/search"), OperationDocKey(a, OperationKey(HttpMethod.QUERY, "/search")).operation)
    }
}
