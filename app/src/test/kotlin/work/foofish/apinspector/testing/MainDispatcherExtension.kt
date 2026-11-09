package work.foofish.apinspector.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

/** 用测试调度器替换 `Dispatchers.Main`，使 `viewModelScope` 可在 JVM 上运行。 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherExtension(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : BeforeEachCallback,
    AfterEachCallback {
    override fun beforeEach(context: ExtensionContext) = Dispatchers.setMain(dispatcher)

    override fun afterEach(context: ExtensionContext) = Dispatchers.resetMain()
}
