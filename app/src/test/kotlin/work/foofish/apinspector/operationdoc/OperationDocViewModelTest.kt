package work.foofish.apinspector.operationdoc

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import work.foofish.apinspector.navigation.OperationDocKey
import work.foofish.apinspector.openapi.HttpMethod
import work.foofish.apinspector.openapi.OperationKey
import work.foofish.apinspector.spec.SpecId
import work.foofish.apinspector.testing.FakeSpecRepository
import work.foofish.apinspector.testing.MainDispatcherExtension
import work.foofish.apinspector.testing.loaded
import work.foofish.apinspector.testing.summary

class OperationDocViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val repository = FakeSpecRepository()
    private val pets = OperationKey(HttpMethod.GET, "/pets")

    private fun viewModel(operation: OperationKey = pets) = OperationDocViewModel(OperationDocKey(SpecId("a"), operation), repository)

    init {
        repository.put(
            summary("a"),
            loaded(
                """
                paths:
                  /pets:
                    get:
                      summary: List pets
                      responses:
                        '200':
                          description: OK
                          content:
                            application/json:
                              schema: { type: object, properties: { name: { type: string } } }
                """,
            ),
        )
    }

    @Test
    fun `loads the operation and toggles nodes`() =
        runTest {
            val viewModel = viewModel()
            viewModel.uiState.test {
                val loaded = assertInstanceOf(OperationDocUiState.Loaded::class.java, expectMostRecentItem())
                assertEquals("List pets", loaded.overview.summary)

                viewModel.toggle("responses/200")
                val collapsed = expectMostRecentItem() as OperationDocUiState.Loaded
                assertFalse(
                    collapsed.items
                        .filterIsInstance<DocItem.ResponseRow>()
                        .single()
                        .expanded,
                )
            }
        }

    @Test
    fun `an operation that no longer exists is missing`() =
        runTest {
            viewModel(OperationKey(HttpMethod.DELETE, "/pets")).uiState.test {
                assertEquals(OperationDocUiState.Missing, expectMostRecentItem())
            }
        }

    @Test
    fun `deleting the spec makes the operation missing`() =
        runTest {
            viewModel().uiState.test {
                assertInstanceOf(OperationDocUiState.Loaded::class.java, expectMostRecentItem())
                repository.delete(SpecId("a"))
                assertEquals(OperationDocUiState.Missing, expectMostRecentItem())
            }
        }
}
