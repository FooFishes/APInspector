package work.foofish.apinspector.network

import android.annotation.SuppressLint
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import work.foofish.apinspector.environment.EnvironmentNetwork
import work.foofish.apinspector.environment.ProxyChoice
import work.foofish.apinspector.settings.AppSettings
import work.foofish.apinspector.settings.ProxyConfig
import work.foofish.apinspector.settings.ProxyType
import java.net.InetSocketAddress
import java.net.Proxy
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.X509TrustManager

/**
 * 按全局设置与环境的网络设置配置发送请求用的 OkHttpClient（PRD 6）。
 *
 * 每次发送都从同一个基础客户端派生，共享连接池与线程池。
 */
@Singleton
class HttpClients
    @Inject
    constructor(
        base: OkHttpClient,
    ) {
        /** 超时由每次发送单独配置，这里去掉导入文档用的整体调用超时。 */
        private val requests = base.newBuilder().callTimeout(Duration.ZERO).build()

        internal fun configure(
            settings: AppSettings,
            network: EnvironmentNetwork?,
        ): OkHttpClient.Builder {
            val timeout = network?.timeout ?: settings.timeout
            val builder =
                requests
                    .newBuilder()
                    .connectTimeout(timeout)
                    .readTimeout(timeout)
                    .writeTimeout(timeout)
                    .followRedirects(settings.followRedirects)
                    .followSslRedirects(settings.followRedirects)
            val proxy =
                when (val choice = network?.proxy ?: ProxyChoice.FollowGlobal) {
                    ProxyChoice.FollowGlobal -> settings.proxy
                    ProxyChoice.Direct -> null
                    is ProxyChoice.Custom -> choice.config
                }
            builder.applyProxy(proxy)
            if (network?.skipTlsVerification == true) builder.trustEverything()
            return builder
        }

        private fun OkHttpClient.Builder.applyProxy(proxy: ProxyConfig?) {
            when (proxy?.type) {
                // 直连时也不使用系统代理，行为只由应用内设置决定。
                null -> {
                    proxy(Proxy.NO_PROXY)
                }

                ProxyType.HTTP -> {
                    proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(proxy.host, proxy.port)))
                    if (proxy.hasCredentials) proxyAuthenticator(BasicProxyAuthenticator(proxy.username, proxy.password))
                }

                ProxyType.SOCKS5 -> {
                    proxy(Proxy.NO_PROXY)
                    socketFactory(Socks5SocketFactory(proxy.host, proxy.port, proxy.username, proxy.password))
                    dns(RemoteDns)
                }
            }
        }

        /** 跳过证书校验（PRD 6，环境级开关，默认关闭，开启时界面显著标识）。 */
        private fun OkHttpClient.Builder.trustEverything() {
            val context = SSLContext.getInstance(TLS).apply { init(null, arrayOf(TrustAllManager), SecureRandom()) }
            sslSocketFactory(context.socketFactory, TrustAllManager)
            hostnameVerifier(TrustAllHostnames)
        }

        private companion object {
            const val TLS = "TLS"
        }
    }

/** 代理要求认证（407）时附上 Basic 凭证；已经附上仍被拒绝则放弃。 */
private class BasicProxyAuthenticator(
    username: String,
    password: String,
) : Authenticator {
    private val credential = Credentials.basic(username, password)

    override fun authenticate(
        route: Route?,
        response: Response,
    ): Request? =
        if (response.request.header(PROXY_AUTHORIZATION) != null) {
            null
        } else {
            response.request
                .newBuilder()
                .header(PROXY_AUTHORIZATION, credential)
                .build()
        }

    private companion object {
        const val PROXY_AUTHORIZATION = "Proxy-Authorization"
    }
}

// 用户为某个环境显式开启"跳过证书校验"时才使用（PRD 6），用于调试自签名证书的服务。
@SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
private object TrustAllManager : X509TrustManager {
    override fun checkClientTrusted(
        chain: Array<out X509Certificate>,
        authType: String,
    ) = Unit

    override fun checkServerTrusted(
        chain: Array<out X509Certificate>,
        authType: String,
    ) = Unit

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

@SuppressLint("BadHostnameVerifier")
private object TrustAllHostnames : HostnameVerifier {
    override fun verify(
        hostname: String,
        session: SSLSession,
    ): Boolean = true
}
