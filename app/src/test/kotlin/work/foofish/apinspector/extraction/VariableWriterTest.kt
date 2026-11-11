package work.foofish.apinspector.extraction

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import work.foofish.apinspector.custom.CustomRequestDraft
import work.foofish.apinspector.environment.EnvironmentId
import work.foofish.apinspector.openapi.HttpMethod
import work.foofish.apinspector.openapi.OperationKey
import work.foofish.apinspector.openapi.request.EnvironmentVariable
import work.foofish.apinspector.openapi.request.RequestInput
import work.foofish.apinspector.request.RequestRef
import work.foofish.apinspector.spec.SpecId
import work.foofish.apinspector.testing.FakeCustomRequestRepository
import work.foofish.apinspector.testing.FakeEnvironmentRepository
import work.foofish.apinspector.testing.environment

class VariableWriterTest {
    private val specId = SpecId("spec")
    private val environmentId = EnvironmentId("env")
    private val environments =
        FakeEnvironmentRepository().apply {
            put(
                environment(specId, environmentId, "生产", "https://api.example.com")
                    .copy(variables = listOf(EnvironmentVariable("token", "old"), EnvironmentVariable("host", "h"))),
            )
        }
    private val customs = FakeCustomRequestRepository()
    private val writer = VariableWriter(environments, customs)
    private val operation = RequestRef.Operation(specId, OperationKey(HttpMethod.GET, "/pets"))
    private val draft = CustomRequestDraft("请求", HttpMethod.GET, "https://example.com", RequestInput(emptyList(), emptyList(), body = null))

    @Test
    fun `updates an existing environment variable in place and appends new ones`() =
        runTest {
            assertTrue(writer.write(operation, environmentId, mapOf("token" to "new", "id" to "7")))

            assertEquals(
                listOf("token" to "new", "host" to "h", "id" to "7"),
                environments.find(environmentId)!!.variables.map { it.name to it.value },
            )
        }

    @Test
    fun `falls back to the current environment when the recorded one is gone`() =
        runTest {
            assertTrue(writer.write(operation, EnvironmentId("deleted"), mapOf("token" to "new")))
            assertTrue(writer.write(operation, environmentId = null, values = mapOf("id" to "7")))

            assertEquals(mapOf("token" to "new", "host" to "h", "id" to "7"), environments.find(environmentId)!!.variableValues)
        }

    @Test
    fun `reports failure when the api document has no environment`() =
        runTest {
            val other = RequestRef.Operation(SpecId("empty"), OperationKey(HttpMethod.GET, "/pets"))

            assertFalse(writer.write(other, environmentId = null, values = mapOf("token" to "new")))
        }

    @Test
    fun `scratch request keeps values in its own variables`() =
        runTest {
            val id = customs.create(specId = null, draft)

            assertTrue(writer.write(RequestRef.Custom(id, specId = null), environmentId = null, values = mapOf("token" to "t")))

            assertEquals(listOf(EnvironmentVariable("token", "t")), customs.find(id)!!.variables)
        }

    @Test
    fun `custom request in an api document writes to the environment`() =
        runTest {
            val id = customs.create(specId, draft)

            assertTrue(writer.write(RequestRef.Custom(id, specId), environmentId, mapOf("token" to "t")))

            assertTrue(customs.find(id)!!.variables.isEmpty())
            assertEquals("t", environments.find(environmentId)!!.variableValues["token"])
        }
}
