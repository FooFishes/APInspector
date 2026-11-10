package work.foofish.apinspector.settings

import app.cash.turbine.test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import work.foofish.apinspector.history.ResponseCache
import work.foofish.apinspector.settings.ui.SettingsViewModel
import work.foofish.apinspector.testing.MainDispatcherExtension

class SettingsViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val repository =
        object : SettingsRepository {
            override val settings = MutableStateFlow(AppSettings())

            override suspend fun update(transform: (AppSettings) -> AppSettings) = settings.update(transform)
        }

    private val cache =
        object : ResponseCache {
            var bytes = CACHED_BYTES

            override suspend fun size(): Long = bytes

            override suspend fun clear() {
                bytes = 0
            }
        }

    @Test
    fun `shows defaults and cache size, applies updates`() =
        runTest {
            val viewModel = SettingsViewModel(repository, cache)
            viewModel.uiState.test {
                val initial = expectMostRecentItem()
                assertEquals(AppSettings.DEFAULT_TIMEOUT_SECONDS, initial.settings?.timeoutSeconds)
                assertEquals(BodyLimit.MB_10, initial.settings?.bodyLimit)
                assertEquals(CACHED_BYTES, initial.cacheBytes)

                viewModel.update { it.copy(proxy = ProxyConfig(ProxyType.SOCKS5, host = "127.0.0.1", port = PROXY_PORT)) }
                assertEquals(ProxyType.SOCKS5, expectMostRecentItem().settings?.proxy?.type)

                viewModel.clearCache()
                assertEquals(0L, expectMostRecentItem().cacheBytes)
            }
        }

    private companion object {
        const val CACHED_BYTES = 2_048L
        const val PROXY_PORT = 1080
    }
}
