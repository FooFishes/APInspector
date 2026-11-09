package work.foofish.apinspector.operations

import app.cash.turbine.test
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import work.foofish.apinspector.navigation.SpecKey
import work.foofish.apinspector.openapi.HttpMethod
import work.foofish.apinspector.openapi.OperationDiff
import work.foofish.apinspector.openapi.OperationKey
import work.foofish.apinspector.spec.DocumentLoad
import work.foofish.apinspector.spec.ImportFailure
import work.foofish.apinspector.spec.RefreshPreparation
import work.foofish.apinspector.spec.RefreshPreview
import work.foofish.apinspector.spec.SpecId
import work.foofish.apinspector.spec.SpecOrigin
import work.foofish.apinspector.testing.FakeSpecRepository
import work.foofish.apinspector.testing.MainDispatcherExtension
import work.foofish.apinspector.testing.loaded
import work.foofish.apinspector.testing.summary

class OperationListViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val id = SpecId("a")
    private val repository = FakeSpecRepository()
    private val current =
        loaded(
            """
            paths:
              /pets:
                get:
                  tags: [pets]
              /legacy:
                get: {}
            """,
        )
    private val pets = OperationKey(HttpMethod.GET, "/pets")
    private val legacy = OperationKey(HttpMethod.GET, "/legacy")

    private fun viewModel() = OperationListViewModel(SpecKey(id), repository, repository)

    private fun preview(diff: OperationDiff) =
        RefreshPreview(
            specId = id,
            diff = diff,
            source = "",
            content =
                loaded(
                    """
                    paths:
                      /pets:
                        get:
                          tags: [pets]
                    """,
                ),
        )

    @Test
    fun `loads the document into sections`() =
        runTest {
            repository.put(summary("a"), current)
            viewModel().uiState.test {
                val content = assertInstanceOf(OperationListContent.Loaded::class.java, expectMostRecentItem().content)
                assertTrue(content.hasOperations)
                assertEquals(listOf(SectionId.Tagged("pets"), SectionId.Untagged), content.sections.map { it.id })
            }
        }

    @Test
    fun `reports missing and failed documents`() =
        runTest {
            viewModel().uiState.test {
                assertEquals(OperationListContent.Missing, expectMostRecentItem().content)
            }
            repository.put(summary("a"))
            repository.putLoad(id, DocumentLoad.Failed(ImportFailure.StoredContentMissing))
            viewModel().uiState.test {
                assertEquals(OperationListContent.Failed(ImportFailure.StoredContentMissing), expectMostRecentItem().content)
            }
        }

    @Test
    fun `becomes missing when the spec is deleted`() =
        runTest {
            repository.put(summary("a"), current)
            viewModel().uiState.test {
                assertInstanceOf(OperationListContent.Loaded::class.java, expectMostRecentItem().content)
                repository.delete(id)
                assertEquals(OperationListContent.Missing, expectMostRecentItem().content)
            }
        }

    @Test
    fun `pinning does not reload the document`() =
        runTest {
            repository.put(summary("a"), current)
            viewModel().uiState.test {
                expectMostRecentItem()
                repository.setPinned(id, pinned = true)
                assertTrue(expectMostRecentItem().spec?.pinned == true)
                assertEquals(1, repository.documentLoads)
            }
        }

    @Test
    fun `favorites and collapsing update sections`() =
        runTest {
            repository.put(summary("a"), current)
            val viewModel = viewModel()
            viewModel.uiState.test {
                viewModel.setFavorite(legacy, favorite = true)
                val withFavorite = expectMostRecentItem().content as OperationListContent.Loaded
                assertEquals(SectionId.Favorites, withFavorite.sections.first().id)

                viewModel.toggleSection(SectionId.Untagged)
                val collapsed = expectMostRecentItem().content as OperationListContent.Loaded
                assertTrue(collapsed.sections.single { it.id == SectionId.Untagged }.collapsed)
            }
        }

    @Test
    fun `only URL specs can refresh`() =
        runTest {
            repository.put(summary("a", origin = SpecOrigin.Pasted), current)
            viewModel().uiState.test {
                assertFalse(expectMostRecentItem().canRefresh)
            }
        }

    @Test
    fun `refresh without removals applies immediately and reloads`() =
        runTest {
            repository.put(summary("a"), current)
            val viewModel = viewModel()
            viewModel.uiState.test {
                assertTrue(expectMostRecentItem().canRefresh)
                viewModel.refresh()
                assertEquals(RefreshStatus.Fetching, expectMostRecentItem().refresh)

                val diff = OperationDiff(added = emptyList(), removed = emptyList(), modified = listOf(pets))
                repository.refreshResult.complete(RefreshPreparation.Ready(preview(diff)))
                val done = expectMostRecentItem()
                assertEquals(RefreshStatus.Done(diff, warningCount = 0), done.refresh)
                assertEquals(1, repository.appliedRefreshes.size)
                assertEquals(1, (done.content as OperationListContent.Loaded).sections.size)

                viewModel.onRefreshResultShown()
                assertEquals(RefreshStatus.Idle, expectMostRecentItem().refresh)
            }
        }

    @Test
    fun `refresh with removals waits for confirmation`() =
        runTest {
            repository.put(summary("a"), current)
            val viewModel = viewModel()
            viewModel.uiState.test {
                viewModel.refresh()
                val diff = OperationDiff(added = emptyList(), removed = listOf(legacy), modified = emptyList())
                repository.refreshResult.complete(RefreshPreparation.Ready(preview(diff)))
                assertEquals(RefreshStatus.ConfirmingRemoval(persistentListOf(legacy)), expectMostRecentItem().refresh)
                assertTrue(repository.appliedRefreshes.isEmpty())

                viewModel.confirmRefresh()
                assertEquals(RefreshStatus.Done(diff, warningCount = 0), expectMostRecentItem().refresh)
                assertEquals(1, repository.appliedRefreshes.size)
            }
        }

    @Test
    fun `cancelling a refresh keeps the current document`() =
        runTest {
            repository.put(summary("a"), current)
            val viewModel = viewModel()
            viewModel.uiState.test {
                viewModel.refresh()
                val diff = OperationDiff(added = emptyList(), removed = listOf(legacy), modified = emptyList())
                repository.refreshResult.complete(RefreshPreparation.Ready(preview(diff)))
                viewModel.cancelRefresh()
                assertEquals(RefreshStatus.Idle, expectMostRecentItem().refresh)

                viewModel.confirmRefresh()
                assertTrue(repository.appliedRefreshes.isEmpty())
            }
        }

    @Test
    fun `refresh failure is reported`() =
        runTest {
            repository.put(summary("a"), current)
            val viewModel = viewModel()
            viewModel.uiState.test {
                viewModel.refresh()
                repository.refreshResult.complete(RefreshPreparation.Failed(ImportFailure.HttpStatus(500)))
                assertEquals(RefreshStatus.Failed(ImportFailure.HttpStatus(500)), expectMostRecentItem().refresh)
            }
        }
}
