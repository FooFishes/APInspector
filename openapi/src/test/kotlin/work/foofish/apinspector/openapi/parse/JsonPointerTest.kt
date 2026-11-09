package work.foofish.apinspector.openapi.parse

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class JsonPointerTest {
    @Test
    fun `parses a component reference`() {
        assertEquals(listOf("components", "schemas", "User"), JsonPointer.fromFragment("#/components/schemas/User")?.tokens)
    }

    @Test
    fun `unescapes slash and tilde`() {
        assertEquals(listOf("paths", "/users/{id}", "a~b"), JsonPointer.fromFragment("#/paths/~1users~1{id}/a~0b")?.tokens)
    }

    @Test
    fun `decodes percent-encoded UTF-8`() {
        assertEquals(listOf("Pet Food", "中文"), JsonPointer.fromFragment("#/Pet%20Food/%E4%B8%AD%E6%96%87")?.tokens)
    }

    @Test
    fun `treats a bare hash as the document root`() {
        assertEquals(JsonPointer.ROOT, JsonPointer.fromFragment("#"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["components/schemas/User", "#components", "#/a~2b", "#/a~", "#/a%zz", "#/a%4", "#/a%E4%B8"])
    fun `rejects malformed fragments`(fragment: String) {
        assertNull(JsonPointer.fromFragment(fragment))
    }

    @Test
    fun `renders the canonical fragment`() {
        assertEquals("#/paths/~1users~1{id}/a~0b", JsonPointer(listOf("paths", "/users/{id}", "a~b")).toFragment())
    }

    @Test
    fun `evaluates object members and array indices`() {
        val root =
            buildJsonObject {
                put("list", buildJsonArray { add(JsonPrimitive("first")) })
                put("map", buildJsonObject { put("key", "value") })
            }
        assertEquals(JsonPrimitive("first"), JsonPointer(listOf("list", "0")).evaluate(root))
        assertEquals(JsonPrimitive("value"), JsonPointer(listOf("map", "key")).evaluate(root))
        assertNull(JsonPointer(listOf("list", "1")).evaluate(root))
        assertNull(JsonPointer(listOf("list", "00")).evaluate(root))
        assertNull(JsonPointer(listOf("map", "key", "deeper")).evaluate(root))
    }
}
