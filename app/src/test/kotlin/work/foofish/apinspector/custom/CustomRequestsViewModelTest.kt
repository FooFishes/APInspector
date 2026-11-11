package work.foofish.apinspector.custom

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import work.foofish.apinspector.environment.EnvironmentId
import work.foofish.apinspector.openapi.HttpMethod
import work.foofish.apinspector.openapi.request.CurlError
import work.foofish.apinspector.openapi.request.EnvironmentVariable
import work.foofish.apinspector.openapi.request.ExtraLocation
import work.foofish.apinspector.spec.SpecId
import work.foofish.apinspector.testing.FakeCustomRequestRepository
import work.foofish.apinspector.testing.FakeEnvironmentRepository
import work.foofish.apinspector.testing.FakeSpecRepository
import work.foofish.apinspector.testing.MainDispatcherExtension
import work.foofish.apinspector.testing.environment

class CustomRequestsViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val specId = SpecId("spec")
    private val customs = FakeCustomRequestRepository()
    private val environments =
        FakeEnvironmentRepository().apply {
            put(
                environment(specId, EnvironmentId("env"), "生产", "https://{{host}}/v1/")
                    .copy(variables = listOf(EnvironmentVariable("host", "api.example.com"))),
            )
        }

    private fun viewModel(owner: SpecId?) = CustomRequestsViewModel(CustomRequestsOwner(owner), customs, environments, FakeSpecRepository())

    @Test
    fun `curl import keeps the full url for a scratch request and turns headers into extras`() =
        runTest {
            val result = viewModel(null).importCurl("curl -X POST 'https://api.example.com/v1/pets?limit=1' -H 'X-Trace: 1' --insecure")

            val imported = assertInstanceOf(CurlImportResult.Imported::class.java, result)
            assertEquals(listOf("--insecure"), imported.ignored)
            val request = customs.find(imported.id)!!
            assertNull(request.specId)
            assertEquals(HttpMethod.POST, request.method)
            assertEquals("https://api.example.com/v1/pets?limit=1", request.url)
            assertEquals("/v1/pets", request.name)
            assertEquals(listOf(ExtraLocation.HEADER to "X-Trace"), request.input.extras.map { it.location to it.name })
        }

    @Test
    fun `curl import into an api document strips the current base url`() =
        runTest {
            val result = viewModel(specId).importCurl("curl https://api.example.com/v1/pets?limit=1")

            val imported = assertInstanceOf(CurlImportResult.Imported::class.java, result)
            assertEquals("/pets?limit=1", customs.find(imported.id)!!.url)
        }

    @Test
    fun `curl import reports why a command cannot be parsed`() =
        runTest {
            assertEquals(CurlImportResult.Failed(CurlError.NOT_CURL), viewModel(null).importCurl("wget https://example.com"))
        }

    @Test
    fun `moving a scratch request makes its url relative and drops its variables`() =
        runTest {
            val scratch = viewModel(null)
            val id = scratch.create("新请求")
            customs.update(
                customs.find(id)!!.copy(url = "https://api.example.com/v1/pets", variables = listOf(EnvironmentVariable("token", "t"))),
            )

            scratch.uiState.test {
                scratch.move(id, specId)
                assertTrue(expectMostRecentItem().requests.orEmpty().isEmpty())
            }
            val moved = customs.find(id)!!
            assertEquals(specId, moved.specId)
            assertEquals("/pets", moved.url)
            assertTrue(moved.variables.isEmpty())
        }

    @Test
    fun `relative url only strips a whole base url`() {
        val environment = environment(specId, EnvironmentId("env"), "生产", "https://api.example.com")

        assertEquals("/", relativeUrl("https://api.example.com", environment))
        assertEquals("/?a=1", relativeUrl("https://api.example.com?a=1", environment))
        assertEquals("https://api.example.com.cn/pets", relativeUrl("https://api.example.com.cn/pets", environment))
        assertEquals("https://other.example.com/pets", relativeUrl("https://other.example.com/pets", environment))
        assertEquals("https://api.example.com/pets", relativeUrl("https://api.example.com/pets", environment = null))
    }
}
