package work.foofish.apinspector.network

import okhttp3.Call
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import work.foofish.apinspector.history.Timings
import work.foofish.apinspector.openapi.request.Field
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.time.Clock
import java.time.Duration

/**
 * 一次调用的 Cookie 处理（PRD 6）：
 *
 * - 发出时（每一跳，作为网络拦截器）：罐中适用的 Cookie 与参数 / 鉴权给出的 Cookie 合并，同名以后者为准；
 *   后者只发往原始主机，避免重定向到其他主机时泄露凭证。
 * - 收到时（作为 [CookieJar]）：记下 `Set-Cookie`，后续跳转立即可见，调用结束后由调用方持久化 [received]。
 *
 * OkHttp 只在 CookieJar 给出 Cookie 时才覆盖请求中的 `Cookie` 头，因此这里的加载始终为空，改由拦截器设置；
 * 重放时不加载罐（[loadStored] 为 `false`），请求中原有的 `Cookie` 头得以原样发出。
 */
internal class CallCookies(
    stored: List<Cookie>,
    private val explicit: List<Field>,
    private val origin: HttpUrl,
    private val loadStored: Boolean,
    private val clock: Clock,
) : CookieJar,
    Interceptor {
    private val lock = Any()
    private val jar = stored.toMutableList()
    private val receivedCookies = mutableListOf<Cookie>()

    val received: List<Cookie> get() = synchronized(lock) { receivedCookies.toList() }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = emptyList()

    override fun saveFromResponse(
        url: HttpUrl,
        cookies: List<Cookie>,
    ) {
        synchronized(lock) {
            receivedCookies += cookies
            cookies.forEach { cookie -> jar.removeAll { it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path } }
            jar += cookies
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        val explicitHere = if (url.host == origin.host) explicit else emptyList()
        val stored =
            if (loadStored) {
                val now = clock.millis()
                synchronized(lock) { jar.filter { it.expiresAt > now && it.matches(url) } }
            } else {
                emptyList()
            }
        if (explicitHere.isEmpty() && stored.isEmpty()) return chain.proceed(request)
        val explicitNames = explicitHere.mapTo(HashSet()) { it.name }
        val pairs = stored.filterNot { it.name in explicitNames }.map { it.name to it.value } + explicitHere.map { it.name to it.value }
        val header = pairs.joinToString("; ") { (name, value) -> "$name=$value" }
        return chain.proceed(request.newBuilder().header(COOKIE, header).build())
    }

    private companion object {
        const val COOKIE = "Cookie"
    }
}

/** 记录第一跳实际发出的请求头（含 Cookie 等由传输层补充的部分），作为实际请求保存（PRD 7.1）。 */
internal class RequestCapture : Interceptor {
    @Volatile
    var headers: Headers? = null
        private set

    override fun intercept(chain: Interceptor.Chain): Response {
        if (headers == null) headers = chain.request().headers
        return chain.proceed(chain.request())
    }
}

/**
 * 记录第一次连接各阶段的耗时（PRD 7.2）：DNS、TCP 连接、TLS 握手，以及请求发出到收到响应头的等待。
 * 复用连接时没有 DNS / 连接 / TLS 阶段。
 */
internal class TimingListener : EventListener() {
    private var dnsStart: Long? = null
    private var dns: Long? = null
    private var connectStart: Long? = null
    private var connect: Long? = null
    private var tlsStart: Long? = null
    private var tls: Long? = null
    private var requestSent: Long? = null
    private var firstByte: Long? = null

    val timings: Timings
        @Synchronized get() = Timings(dns.duration(), connect.duration(), tls.duration(), firstByte.duration())

    @Synchronized
    override fun dnsStart(
        call: Call,
        domainName: String,
    ) {
        if (dns == null) dnsStart = System.nanoTime()
    }

    @Synchronized
    override fun dnsEnd(
        call: Call,
        domainName: String,
        inetAddressList: List<InetAddress>,
    ) {
        if (dns == null) dns = dnsStart?.let { System.nanoTime() - it }
    }

    @Synchronized
    override fun connectStart(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
    ) {
        if (connect == null) connectStart = System.nanoTime()
    }

    @Synchronized
    override fun secureConnectStart(call: Call) {
        if (tls == null) {
            val now = System.nanoTime()
            tlsStart = now
            // TCP 连接到 TLS 握手开始为止；connectEnd 在握手之后才到，不再覆盖。
            if (connect == null) connect = connectStart?.let { now - it }
        }
    }

    @Synchronized
    override fun secureConnectEnd(
        call: Call,
        handshake: Handshake?,
    ) {
        if (tls == null) tls = tlsStart?.let { System.nanoTime() - it }
    }

    @Synchronized
    override fun connectEnd(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?,
    ) {
        if (connect == null) connect = connectStart?.let { System.nanoTime() - it }
    }

    @Synchronized
    override fun requestHeadersEnd(
        call: Call,
        request: Request,
    ) {
        if (firstByte == null) requestSent = System.nanoTime()
    }

    @Synchronized
    override fun requestBodyEnd(
        call: Call,
        byteCount: Long,
    ) {
        if (firstByte == null) requestSent = System.nanoTime()
    }

    @Synchronized
    override fun responseHeadersStart(call: Call) {
        if (firstByte == null) firstByte = requestSent?.let { System.nanoTime() - it }
    }

    private fun Long?.duration(): Duration? = this?.let(Duration::ofNanos)
}
