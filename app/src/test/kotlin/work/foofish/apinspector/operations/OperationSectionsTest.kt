package work.foofish.apinspector.operations

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import work.foofish.apinspector.openapi.HttpMethod
import work.foofish.apinspector.openapi.OperationKey
import work.foofish.apinspector.testing.document

class OperationSectionsTest {
    private val document =
        document(
            """
            tags:
              - name: users
                summary: 用户
              - name: declared-but-unused
            paths:
              /orders:
                get:
                  tags: [orders]
                  summary: List orders
              /users:
                get:
                  tags: [users]
                  operationId: listUsers
                post:
                  tags: [users, admin]
                  deprecated: true
              /health:
                get:
                  summary: Health check
            """,
        )

    private val listUsers = OperationKey(HttpMethod.GET, "/users")
    private val createUser = OperationKey(HttpMethod.POST, "/users")
    private val health = OperationKey(HttpMethod.GET, "/health")

    @Test
    fun `groups declared tags first, then undeclared in order of appearance, then untagged`() {
        val sections = buildSections(document, favorites = emptySet(), query = "", collapsed = emptySet())

        assertEquals(
            listOf(SectionId.Tagged("users"), SectionId.Tagged("orders"), SectionId.Tagged("admin"), SectionId.Untagged),
            sections.map { it.id },
        )
        assertEquals("用户", sections[0].tag?.summary)
        assertNull(sections[1].tag)
        assertEquals(listOf(listUsers, createUser), sections[0].operations.map { it.key })
        assertTrue(sections[0].operations[1].deprecated)
    }

    @Test
    fun `favorites come first and stay in their own sections`() {
        val sections = buildSections(document, favorites = setOf(health), query = "", collapsed = emptySet())

        assertEquals(SectionId.Favorites, sections.first().id)
        assertEquals(listOf(health), sections.first().operations.map { it.key })
        assertTrue(
            sections
                .last()
                .operations
                .single()
                .favorite,
        )
    }

    @Test
    fun `collapsed sections are marked`() {
        val sections = buildSections(document, favorites = emptySet(), query = "", collapsed = setOf(SectionId.Untagged))

        assertTrue(sections.single { it.id == SectionId.Untagged }.collapsed)
        assertFalse(sections.first().collapsed)
    }

    @Test
    fun `search matches operationId, drops empty sections and ignores collapsing`() {
        val sections = buildSections(document, favorites = emptySet(), query = "LISTUSERS", collapsed = setOf(SectionId.Tagged("users")))

        assertEquals(listOf(SectionId.Tagged("users")), sections.map { it.id })
        assertFalse(sections.single().collapsed)
        assertEquals(listOf(listUsers), sections.single().operations.map { it.key })
    }

    @Test
    fun `search matches summary and path, trimming the query`() {
        val bySummary = buildSections(document, favorites = emptySet(), query = " health ", collapsed = emptySet())
        assertEquals(listOf(health), bySummary.flatMap { it.operations }.map { it.key })

        val byPath = buildSections(document, favorites = emptySet(), query = "/ord", collapsed = emptySet())
        assertEquals(listOf(SectionId.Tagged("orders")), byPath.map { it.id })

        assertTrue(buildSections(document, favorites = emptySet(), query = "nope", collapsed = emptySet()).isEmpty())
    }
}
