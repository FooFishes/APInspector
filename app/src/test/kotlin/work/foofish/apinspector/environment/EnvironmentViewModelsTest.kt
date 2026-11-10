package work.foofish.apinspector.environment

import app.cash.turbine.test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import work.foofish.apinspector.environment.ui.EnvironmentViewModel
import work.foofish.apinspector.environment.ui.EnvironmentsViewModel
import work.foofish.apinspector.navigation.EnvironmentKey
import work.foofish.apinspector.navigation.EnvironmentsKey
import work.foofish.apinspector.openapi.request.Credential
import work.foofish.apinspector.spec.SpecId
import work.foofish.apinspector.testing.FakeEnvironmentRepository
import work.foofish.apinspector.testing.FakeSpecRepository
import work.foofish.apinspector.testing.MainDispatcherExtension
import work.foofish.apinspector.testing.environment
import work.foofish.apinspector.testing.loaded
import work.foofish.apinspector.testing.summary

class EnvironmentViewModelsTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val specId = SpecId("a")
    private val repository = FakeEnvironmentRepository()
    private val prod = EnvironmentId("prod")
    private val staging = EnvironmentId("staging")

    init {
        repository.put(environment(specId, prod, "生产", "https://api.example.com"))
        repository.put(environment(specId, staging, "预发", "https://staging.example.com"))
    }

    @Test
    fun `lists, selects and refuses to delete the last environment`() =
        runTest {
            val viewModel = EnvironmentsViewModel(EnvironmentsKey(specId), repository)
            viewModel.uiState.test {
                assertEquals(listOf("生产", "预发"), expectMostRecentItem().environments?.map { it.name })

                viewModel.select(staging)
                assertEquals(staging, expectMostRecentItem().current)

                viewModel.delete(staging)
                assertEquals(listOf(prod), expectMostRecentItem().environments?.map { it.id })

                viewModel.delete(prod)
                expectNoEvents()
                assertEquals(prod, repository.find(prod)?.id)
            }
        }

    @Test
    fun `creates, duplicates and renames`() =
        runTest {
            val viewModel = EnvironmentsViewModel(EnvironmentsKey(specId), repository)
            viewModel.uiState.test {
                expectMostRecentItem()
                var created: EnvironmentId? = null
                viewModel.create("本地") { created = it }
                viewModel.duplicate(prod, "生产 副本")
                viewModel.rename(prod, "线上")

                val names = expectMostRecentItem().environments?.map { it.name }
                assertEquals(listOf("线上", "预发", "本地", "生产 副本"), names)
                assertEquals("本地", created?.let { repository.find(it) }?.name)
            }
        }

    @Test
    fun `edits apply locally and are saved, schemes come from the document`() =
        runTest {
            val specs = FakeSpecRepository()
            specs.put(
                summary("a"),
                loaded(
                    """
                    paths: {}
                    components:
                      securitySchemes:
                        token: { type: http, scheme: bearer }
                    """,
                ),
            )
            val viewModel =
                EnvironmentViewModel(EnvironmentKey(specId, prod), specs, repository, repository, CoroutineScope(main.dispatcher))
            viewModel.uiState.test {
                assertEquals(listOf("token"), expectMostRecentItem().schemes.map { it.name })

                viewModel.update { it.copy(baseUrl = "{{host}}") }
                viewModel.update { it.copy(credentials = mapOf("token" to Credential.Bearer("secret"))) }

                val environment = expectMostRecentItem().environment
                assertTrue(environment?.isBaseUrlIncomplete == true)
                assertEquals(environment, repository.find(prod))
                assertEquals(Credential.Bearer("secret"), repository.find(prod)?.credentials?.get("token"))
            }
        }

    @Test
    fun `reports a deleted environment`() =
        runTest {
            val viewModel =
                EnvironmentViewModel(
                    EnvironmentKey(specId, staging),
                    FakeSpecRepository(),
                    repository,
                    repository,
                    CoroutineScope(main.dispatcher),
                )
            viewModel.uiState.test {
                expectMostRecentItem()
                repository.delete(staging)
                assertTrue(expectMostRecentItem().missing)
            }
        }
}
