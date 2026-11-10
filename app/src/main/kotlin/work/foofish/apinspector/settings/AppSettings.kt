package work.foofish.apinspector.settings

import kotlinx.serialization.Serializable
import java.time.Duration

/** 全局设置（PRD 6）。 */
@Serializable
data class AppSettings(
    val timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS,
    /** 关闭后可以看到原始的 3xx 响应。 */
    val followRedirects: Boolean = true,
    /** 全局代理；`null` 为直连。 */
    val proxy: ProxyConfig? = null,
    val bodyLimit: BodyLimit = BodyLimit.MB_10,
) {
    val timeout: Duration get() = Duration.ofSeconds(timeoutSeconds)

    companion object {
        const val DEFAULT_TIMEOUT_SECONDS = 30L

        /** 可设置的超时（秒）。 */
        val TIMEOUT_RANGE_SECONDS = 1L..3_600L
    }
}

/** 响应 Body 的保存上限（PRD 7.1）。 */
enum class BodyLimit(
    val megabytes: Int,
) {
    MB_1(1),
    MB_5(5),
    MB_10(10),
    MB_50(50),
    MB_100(100),
    ;

    val bytes: Long get() = megabytes.toLong() * BYTES_PER_MEGABYTE

    private companion object {
        const val BYTES_PER_MEGABYTE = 1024L * 1024L
    }
}

/** HTTP 或 SOCKS5 代理，可带用户名与密码（密码明文保存）。 */
@Serializable
data class ProxyConfig(
    val type: ProxyType,
    val host: String,
    val port: Int,
    val username: String = "",
    val password: String = "",
) {
    val hasCredentials: Boolean get() = username.isNotEmpty()
}

enum class ProxyType {
    HTTP,
    SOCKS5,
}
