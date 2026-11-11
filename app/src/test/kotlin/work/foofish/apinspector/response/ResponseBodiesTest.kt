package work.foofish.apinspector.response

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import work.foofish.apinspector.history.HistoryEntry
import work.foofish.apinspector.history.HistoryEntryId
import work.foofish.apinspector.history.HistoryRepository
import work.foofish.apinspector.history.HistorySummary
import work.foofish.apinspector.history.NewHistoryEntry
import work.foofish.apinspector.history.StoredBody
import work.foofish.apinspector.openapi.OperationKey
import work.foofish.apinspector.request.RequestRef
import work.foofish.apinspector.spec.SpecId
import java.io.File
import java.nio.file.Path

class ResponseBodiesTest {
    @TempDir
    lateinit var directory: Path

    /** 只提供 Body 文件的仓库。 */
    private inner class Files : HistoryRepository {
        override fun summaries(
            specId: SpecId?,
            ref: RequestRef?,
        ): Flow<List<HistorySummary>> = emptyFlow()

        override fun entry(id: HistoryEntryId): Flow<HistoryEntry?> = emptyFlow()

        override fun latest(ref: RequestRef): Flow<HistoryEntry?> = emptyFlow()

        override suspend fun record(entry: NewHistoryEntry): HistoryEntryId = error("不需要")

        override suspend fun clear(specId: SpecId?) = Unit

        override suspend fun bodyFile(body: StoredBody): File? = directory.resolve(body.fileName).toFile().takeIf(File::isFile)
    }

    private fun stored(
        name: String,
        bytes: ByteArray,
        total: Long = bytes.size.toLong(),
    ): StoredBody {
        directory.resolve(name).toFile().writeBytes(bytes)
        return StoredBody(name, total, bytes.size.toLong())
    }

    private suspend fun load(
        body: StoredBody?,
        contentType: String?,
    ): BodyView = ResponseBodyLoader(Files(), Dispatchers.Unconfined).load(body, contentType)

    @Test
    fun `json is parsed unless truncated`() =
        runTest {
            val json =
                assertInstanceOf(
                    BodyView.Json::class.java,
                    load(stored("a", """{"a":1}""".toByteArray()), "application/json; charset=utf-8"),
                )
            assertEquals(Json.parseToJsonElement("""{"a":1}"""), json.element)
            assertNotNull(json.file)

            val truncated =
                assertInstanceOf(
                    BodyView.Json::class.java,
                    load(stored("b", """{"a":""".toByteArray(), total = 100), "application/problem+json"),
                )
            assertNull(truncated.element)
            assertNull(truncated.file)
        }

    @Test
    fun `text is decoded with its charset, binary content is detected`() =
        runTest {
            val text =
                assertInstanceOf(
                    BodyView.Text::class.java,
                    load(stored("t", "你好".toByteArray(Charsets.UTF_16LE)), "text/plain; charset=UTF-16LE"),
                )
            assertEquals("你好", text.text)

            assertInstanceOf(BodyView.Binary::class.java, load(stored("bin", byteArrayOf(0, 1, 2, -1)), null))
            assertInstanceOf(BodyView.Text::class.java, load(stored("u", "plain".toByteArray()), null))
        }

    @Test
    fun `images preview only when complete, missing files are cleared`() =
        runTest {
            assertInstanceOf(BodyView.Image::class.java, load(stored("i", byteArrayOf(1)), "image/png"))
            assertEquals(BodyView.Binary(null), load(stored("j", byteArrayOf(1), total = 10), "image/png"))
            assertEquals(BodyView.Cleared, load(StoredBody("gone", 1, 1), "text/plain"))
            assertEquals(BodyView.Empty, load(null, "text/plain"))
        }

    @Test
    fun `json tree filters by query and collapses paths`() {
        val root = Json.parseToJsonElement("""{"user": {"name": "Ann", "tags": ["a", "b"]}, "id": 7}""")

        val all = jsonViewRows(root, collapsed = emptySet(), query = "")
        assertEquals(listOf("$", "$.user", "$.user.name", "$.user.tags", "$.user.tags[0]", "$.user.tags[1]", "$.id"), all.map { it.path })

        val collapsed = jsonViewRows(root, collapsed = setOf("$.user"), query = "")
        assertEquals(listOf("$", "$.user", "$.id"), collapsed.map { it.path })

        val search = jsonViewRows(root, collapsed = setOf("$.user"), query = "ann")
        assertEquals(listOf("$", "$.user", "$.user.name"), search.map { it.path })
        assertEquals(JsonViewKind.STRING, search.last().kind)
    }
}
