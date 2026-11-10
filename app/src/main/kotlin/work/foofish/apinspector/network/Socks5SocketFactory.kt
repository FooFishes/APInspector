package work.foofish.apinspector.network

import okhttp3.Dns
import java.io.DataInputStream
import java.io.IOException
import java.io.OutputStream
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import javax.net.SocketFactory

/**
 * 通过 SOCKS5 代理建立连接的 SocketFactory（RFC 1928），支持用户名密码认证（RFC 1929）。
 *
 * 不使用 JDK 内置的 SOCKS 支持：它的认证依赖进程全局的 `Authenticator`，无法让不同环境使用不同的代理账号。
 * 配合 [RemoteDns]，域名交给代理解析（等同 `socks5h`）。
 */
internal class Socks5SocketFactory(
    private val proxyHost: String,
    private val proxyPort: Int,
    private val username: String,
    private val password: String,
) : SocketFactory() {
    override fun createSocket(): Socket = Socks5Socket()

    override fun createSocket(
        host: String,
        port: Int,
    ): Socket = Socks5Socket().apply { connect(InetSocketAddress.createUnresolved(host, port)) }

    override fun createSocket(
        host: String,
        port: Int,
        localHost: InetAddress,
        localPort: Int,
    ): Socket = createSocket(host, port)

    override fun createSocket(
        host: InetAddress,
        port: Int,
    ): Socket = Socks5Socket().apply { connect(InetSocketAddress(host, port)) }

    override fun createSocket(
        address: InetAddress,
        port: Int,
        localAddress: InetAddress,
        localPort: Int,
    ): Socket = createSocket(address, port)

    private inner class Socks5Socket : Socket() {
        override fun connect(
            endpoint: SocketAddress,
            timeout: Int,
        ) {
            val target = endpoint as? InetSocketAddress ?: throw IOException("不支持的地址类型：$endpoint")
            super.connect(InetSocketAddress(proxyHost, proxyPort), timeout)
            val previousTimeout = soTimeout
            soTimeout = timeout
            try {
                handshake(target)
            } finally {
                soTimeout = previousTimeout
            }
        }

        private fun handshake(target: InetSocketAddress) {
            val output = getOutputStream()
            val input = DataInputStream(getInputStream())
            val authenticate = username.isNotEmpty()
            output.write(if (authenticate) byteArrayOf(VERSION, 2, NO_AUTH, USER_PASS) else byteArrayOf(VERSION, 1, NO_AUTH))
            output.flush()
            ensure(input.readByte() == VERSION) { INVALID_RESPONSE }
            when (input.readByte()) {
                NO_AUTH -> {}

                USER_PASS -> {
                    authenticate(output, input)
                }

                else -> {
                    ensure(false) { "SOCKS5 代理不接受所提供的认证方式" }
                }
            }
            output.write(byteArrayOf(VERSION, CONNECT, RESERVED) + encodeAddress(target) + encodePort(target.port))
            output.flush()
            ensure(input.readByte() == VERSION) { INVALID_RESPONSE }
            val reply = input.readByte().toInt()
            ensure(reply == SUCCEEDED) { "SOCKS5 代理拒绝连接（错误码 $reply）" }
            input.readByte()
            skipBoundAddress(input)
        }

        private fun authenticate(
            output: OutputStream,
            input: DataInputStream,
        ) {
            val user = username.encodeToByteArray()
            val pass = password.encodeToByteArray()
            ensure(user.size <= MAX_FIELD_LENGTH && pass.size <= MAX_FIELD_LENGTH) { "SOCKS5 用户名或密码过长" }
            output.write(byteArrayOf(AUTH_VERSION, user.size.toByte()) + user + byteArrayOf(pass.size.toByte()) + pass)
            output.flush()
            input.readByte()
            ensure(input.readByte().toInt() == SUCCEEDED) { "SOCKS5 代理认证失败" }
        }

        private fun encodeAddress(target: InetSocketAddress): ByteArray {
            val address = target.address
            return when {
                address == null || RemoteDns.isPlaceholder(address) -> {
                    val host = (address?.hostName ?: target.hostString).encodeToByteArray()
                    byteArrayOf(DOMAIN, host.size.toByte()) + host
                }

                address is Inet4Address -> {
                    byteArrayOf(IPV4) + address.address
                }

                address is Inet6Address -> {
                    byteArrayOf(IPV6) + address.address
                }

                else -> {
                    throw IOException("不支持的地址类型：$address")
                }
            }
        }

        private fun encodePort(port: Int): ByteArray = byteArrayOf((port shr BYTE_BITS).toByte(), port.toByte())

        private fun skipBoundAddress(input: DataInputStream) {
            val length =
                when (input.readByte()) {
                    IPV4 -> IPV4_LENGTH
                    IPV6 -> IPV6_LENGTH
                    DOMAIN -> input.readUnsignedByte()
                    else -> throw IOException("SOCKS5 代理响应无效")
                }
            input.readFully(ByteArray(length + PORT_LENGTH))
        }
    }

    private companion object {
        const val VERSION: Byte = 5
        const val AUTH_VERSION: Byte = 1
        const val NO_AUTH: Byte = 0
        const val USER_PASS: Byte = 2
        const val CONNECT: Byte = 1
        const val RESERVED: Byte = 0
        const val IPV4: Byte = 1
        const val DOMAIN: Byte = 3
        const val IPV6: Byte = 4
        const val SUCCEEDED = 0
        const val IPV4_LENGTH = 4
        const val IPV6_LENGTH = 16
        const val PORT_LENGTH = 2
        const val BYTE_BITS = 8
        const val MAX_FIELD_LENGTH = 255
    }
}

/**
 * 不在本机解析域名，而是返回带有域名的占位地址，由 [Socks5SocketFactory] 交给代理解析。IP 字面量照常解析（不访问网络）。
 */
internal object RemoteDns : Dns {
    private val PLACEHOLDER = byteArrayOf(0, 0, 0, 0)
    private val IP_LITERAL = Regex("""^[0-9.]+$|:""")

    override fun lookup(hostname: String): List<InetAddress> =
        if (IP_LITERAL.containsMatchIn(hostname)) Dns.SYSTEM.lookup(hostname) else listOf(InetAddress.getByAddress(hostname, PLACEHOLDER))

    fun isPlaceholder(address: InetAddress): Boolean = address.address.contentEquals(PLACEHOLDER) && address.hostName.isNotEmpty()
}

/** 代理违反协议或拒绝请求时以 [IOException] 中止握手，由 OkHttp 作为连接失败处理。 */
private inline fun ensure(
    condition: Boolean,
    message: () -> String,
) {
    if (!condition) throw IOException(message())
}

private const val INVALID_RESPONSE = "SOCKS5 代理响应无效"
