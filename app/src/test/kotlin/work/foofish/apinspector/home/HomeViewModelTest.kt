package work.foofish.apinspector.home

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import work.foofish.apinspector.spec.ImportFailure
import work.foofish.apinspector.spec.ImportOutcome
import work.foofish.apinspector.spec.ImportSource
import work.foofish.apinspector.spec.SpecId
import work.foofish.apinspector.testing.FakeSpecRepository
import work.foofish.apinspector.testing.MainDispatcherExtension
import work.foofish.apinspector.testing.summary

class HomeViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val repository = FakeSpecRepository()

    // 字段初始化早于扩展的 beforeEach：延迟到替换 Main 调度器之后再创建，viewModelScope 才会使用测试调度器。
    private val viewModel by lazy { HomeViewModel(repository) }

    @Test
    fun `starts loading, then lists specs`() =
        runTest {
            // 未订阅前是初始状态；订阅后（无限制调度器下）立即得到仓库的内容。
            assertNull(viewModel.uiState.value.specs)
            viewModel.uiState.test {
                assertFalse(expectMostRecentItem().hasSpecs)
                repository.put(summary("a"))
                val state = expectMostRecentItem()
                assertEquals(listOf(SpecId("a")), state.specs?.map { it.id })
                assertTrue(state.hasSpecs)
            }
        }

    @Test
    fun `query filters by title but hasSpecs reflects all specs`() =
        runTest {
            repository.put(summary("a", title = "Petstore"))
            repository.put(summary("b", title = "Billing"))
            viewModel.uiState.test {
                viewModel.onQueryChange("  PET ")
                assertEquals(listOf("Petstore"), expectMostRecentItem().specs?.map { it.title })

                viewModel.onQueryChange("nothing")
                val empty = expectMostRecentItem()
                assertEquals(emptyList<String>(), empty.specs?.map { it.title })
                assertTrue(empty.hasSpecs)
            }
        }

    @Test
    fun `import reports progress and outcome until shown`() =
        runTest {
            viewModel.uiState.test {
                viewModel.import(ImportSource.Text("x"))
                assertEquals(ImportStatus.Importing, expectMostRecentItem().importStatus)

                val outcome = ImportOutcome.Failed(ImportFailure.InvalidUrl)
                repository.importResult.complete(outcome)
                assertEquals(ImportStatus.Finished(outcome), expectMostRecentItem().importStatus)

                viewModel.onImportOutcomeShown()
                assertEquals(ImportStatus.Idle, expectMostRecentItem().importStatus)
            }
        }

    @Test
    fun `ignores a second import while one is running`() =
        runTest {
            viewModel.import(ImportSource.Text("first"))
            viewModel.import(ImportSource.Text("second"))
            assertEquals(listOf(ImportSource.Text("first")), repository.imports)
        }

    @Test
    fun `outcome shown does not cancel a running import`() =
        runTest {
            viewModel.uiState.test {
                viewModel.import(ImportSource.Text("x"))
                viewModel.onImportOutcomeShown()
                assertEquals(ImportStatus.Importing, expectMostRecentItem().importStatus)
            }
        }

    @Test
    fun `pin and delete go through the repository`() =
        runTest {
            repository.put(summary("a"))
            viewModel.uiState.test {
                viewModel.setPinned(SpecId("a"), pinned = true)
                assertTrue(
                    expectMostRecentItem()
                        .specs
                        .orEmpty()
                        .single()
                        .pinned,
                )

                viewModel.delete(SpecId("a"))
                assertFalse(expectMostRecentItem().hasSpecs)
            }
        }
}
