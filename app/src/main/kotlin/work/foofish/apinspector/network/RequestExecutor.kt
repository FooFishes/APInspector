package work.foofish.apinspector.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.coroutines.executeAsync
import okio.Buffer
import okio.buffer
import okio.sink
import work.foofish.apinspector.core.coroutines.ApplicationScope
import work.foofish.apinspector.core.coroutines.IoDispatcher
import work.foofish.apinspector.environment.CookieStore
import work.foofish.apinspector.environment.Environment
import work.foofish.apinspector.history.HistoryEntryId
import work.foofish.apinspector.history.HistoryRepository
import work.foofish.apinspector.history.NetworkError
import work.foofish.apinspector.history.NetworkErrorKind
import work.foofish.apinspector.history.NewHistoryEntry
import work.foofish.apinspector.history.Outcome
import work.foofish.apinspector.history.ReceivedResponse
import work.foofish.apinspector.history.RequestSource
import work.foofish.apinspector.history.StoredBody
import work.foofish.apinspector.history.data.ResponseBodyStore
import work.foofish.apinspector.openapi.HttpMethod
import work.foofish.apinspector.openapi.OperationKey
import work.foofish.apinspector.openapi.request.Field
import work.foofish.apinspector.openapi.request.HttpBody
import work.foofish.apinspector.openapi.request.HttpPart
import work.foofish.apinspector.openapi.request.HttpRequest
import work.foofish.apinspector.openapi.request.RequestInput
import work.foofish.apinspector.openapi.request.isTransportHeader
import work.foofish.apinspector.settings.SettingsRepository
import work.foofish.apinspector.spec.SpecId
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException

/** 一个接口的发送槽位：同一接口同时只有一个请求在途（PRD 6）。 */
data class SendTarget(
    val specId: SpecId,
    val operation: OperationKey,
)

/**
 * 待发送的请求。
 *
 * @property cookies 参数与鉴权给出的 Cookie，与环境的 Cookie 罐合并。
 * @property loadCookieJar 是否携带罐中的 Cookie；重放时为 `false`，原样发送记录中的 `Cookie` 头（PRD 7.1）。
 */
data class SendJob(
    val target: SendTarget,
    val environment: Environment?,
    val request: HttpRequest,
    val cookies: List<Field>,
    val loadCookieJar: Boolean,
    val input: RequestInput?,
    val source: RequestSource,
    val presetName: String?,
)

/** 发送请求并跟踪在途请求（PRD 6）。 */
interface RequestSender {
    /** 在途请求及其开始时间。 */
    val inFlight: StateFlow<Map<SendTarget, Instant>>

    /** 开始发送；同一接口已有请求在途时不发送并返回 `false`。 */
    fun send(job: SendJob): Boolean

    fun cancel(target: SendTarget)
}

/**
 * 应用级的请求执行器（PRD 6）：请求在应用作用域中执行，离开页面不中断；完成、失败或取消后写入请求记录（PRD 7.1）。
 */
@Singleton
class RequestExecutor
    @Inject
    constructor(
        @ApplicationScope private val scope: CoroutineScope,
        @IoDispatcher private val io: CoroutineDispatcher,
        private val clients: HttpClients,
        private val files: FileAccess,
        private val settings: SettingsRepository,
        private val cookies: CookieStore,
        private val history: HistoryRepository,
        private val bodies: ResponseBodyStore,
        private val clock: Clock,
    ) : RequestSender {
        private val jobs = HashMap<SendTarget, Job>()
        private val running = MutableStateFlow<Map<SendTarget, Instant>>(emptyMap())

        override val inFlight: StateFlow<Map<SendTarget, Instant>> = running.asStateFlow()

        override fun send(job: SendJob): Boolean {
            synchronized(jobs) {
                if (job.target in jobs) return false
                val started = clock.instant()
                val launched =
                    scope.launch(start = CoroutineStart.LAZY) {
                        try {
                            execute(job, started)
                        } finally {
                            synchronized(jobs) { jobs.remove(job.target) }
                            running.update { it - job.target }
                        }
                    }
                jobs[job.target] = launched
                running.update { it + (job.target to started) }
                launched.start()
            }
            return true
        }

        override fun cancel(target: SendTarget) {
            synchronized(jobs) { jobs[target] }?.cancel()
        }

        private suspend fun execute(
            job: SendJob,
            started: Instant,
        ): HistoryEntryId {
            val startNanos = System.nanoTime()
            var sent = job.request
            val outcome =
                try {
                    val (request, outcome) = call(job)
                    sent = request
                    outcome
                } catch (exception: CancellationException) {
                    withContext(NonCancellable) { history.record(job.toEntry(sent, Outcome.Cancelled, started, startNanos)) }
                    throw exception
                } catch (exception: IOException) {
                    Outcome.Failed(exception.toNetworkError())
                } catch (exception: IllegalArgumentException) {
                    // 非法的 Header 名、方法与 Body 的组合等：OkHttp 在构造请求时拒绝。
                    Outcome.Failed(NetworkError(NetworkErrorKind.OTHER, exception.message))
                }
            return history.record(job.toEntry(sent, outcome, started, startNanos))
        }

        /** 返回实际发出的请求（含传输层补充的 Cookie 等 Header）与结果。 */
        private suspend fun call(job: SendJob): Pair<HttpRequest, Outcome> {
            val url =
                job.request.url.toHttpUrlOrNull()
                    ?: return job.request to Outcome.Failed(NetworkError(NetworkErrorKind.OTHER, job.request.url))
            val current = settings.current()
            val environment = job.environment
            val stored = if (environment != null && job.loadCookieJar) cookies.load(environment.id) else emptyList()
            val callCookies = CallCookies(stored, job.cookies, url, job.loadCookieJar, clock)
            val timing = TimingListener()
            val capture = RequestCapture()
            val client =
                clients
                    .configure(current, environment?.network)
                    .cookieJar(callCookies)
                    .addNetworkInterceptor(callCookies)
                    .addNetworkInterceptor(capture)
                    .eventListener(timing)
                    .build()
            val outcome =
                try {
                    client.newCall(job.request.toOkHttp()).executeAsync().use { response ->
                        Outcome.Received(response.toReceived(current.bodyLimit.bytes, timing))
                    }
                } finally {
                    if (environment != null) withContext(NonCancellable) { cookies.save(environment.id, callCookies.received) }
                }
            val headers = capture.headers?.map { (name, value) -> Field(name, value) }?.filterNot { isTransportHeader(it.name) }
            return job.request.copy(headers = headers ?: job.request.headers) to outcome
        }

        private suspend fun Response.toReceived(
            limit: Long,
            timing: TimingListener,
        ): ReceivedResponse =
            ReceivedResponse(
                statusCode = code,
                statusMessage = message,
                protocol = protocol.toString(),
                finalUrl = request.url.toString(),
                headers = headers.map { (name, value) -> Field(name, value) },
                contentType = header(CONTENT_TYPE),
                body = storeBody(this, limit),
                timings = timing.timings,
            )

        /** 写入至多 [limit] 字节，其余继续读完只计大小（PRD 7.1）。没有内容时返回 `null`。 */
        private suspend fun storeBody(
            response: Response,
            limit: Long,
        ): StoredBody? {
            val file = bodies.newFile()
            var completed = false
            try {
                val (total, stored) = withContext(io) { copyLimited(response, file, limit) }
                completed = total > 0
                return if (completed) StoredBody(file.name, total, stored) else null
            } finally {
                if (!completed) withContext(NonCancellable) { file.delete() }
            }
        }

        private suspend fun copyLimited(
            response: Response,
            file: File,
            limit: Long,
        ): Pair<Long, Long> {
            val source = response.body.source()
            var total = 0L
            var stored = 0L
            file.sink().buffer().use { sink ->
                val buffer = Buffer()
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = source.read(buffer, SEGMENT_BYTES)
                    if (read == -1L) break
                    total += read
                    val keep = minOf(read, limit - stored)
                    if (keep > 0) {
                        sink.write(buffer, keep)
                        stored += keep
                    }
                    buffer.clear()
                }
            }
            return total to stored
        }

        private fun HttpRequest.toOkHttp(): Request {
            val headers = Headers.Builder().apply { this@toOkHttp.headers.forEach { addUnsafeNonAscii(it.name, it.value) } }.build()
            return Request
                .Builder()
                .url(url)
                .headers(headers)
                .method(method.name, body?.toRequestBody() ?: emptyBodyFor(method))
                .build()
        }

        private fun HttpBody.toRequestBody(): RequestBody =
            when (this) {
                is HttpBody.Text -> {
                    // 不用 String.toRequestBody：它会在缺少 charset 时改写 Content-Type。
                    text.encodeToByteArray().toRequestBody(contentType.toMediaTypeOrNull())
                }

                is HttpBody.File -> {
                    files.requestBody(file, contentType.toMediaTypeOrNull())
                }

                is HttpBody.Multipart -> {
                    MultipartBody
                        .Builder()
                        .setType(contentType.toMediaTypeOrNull() ?: MultipartBody.FORM)
                        .apply {
                            parts.forEach { part ->
                                when (part) {
                                    is HttpPart.Text -> {
                                        addFormDataPart(part.name, part.value)
                                    }

                                    is HttpPart.File -> {
                                        addFormDataPart(
                                            part.name,
                                            part.file.name,
                                            files.requestBody(part.file, part.file.mediaType?.toMediaTypeOrNull()),
                                        )
                                    }
                                }
                            }
                        }.build()
                }
            }

        /** POST、PUT 等方法必须带 Body：没有 Body 时发送空 Body。 */
        private fun emptyBodyFor(method: HttpMethod): RequestBody? =
            if (method in
                METHODS_REQUIRING_BODY
            ) {
                ByteArray(0).toRequestBody(null)
            } else {
                null
            }

        private fun SendJob.toEntry(
            sent: HttpRequest,
            outcome: Outcome,
            started: Instant,
            startNanos: Long,
        ) = NewHistoryEntry(
            specId = target.specId,
            operation = target.operation,
            environmentId = environment?.id,
            environmentName = environment?.name,
            source = source,
            presetName = presetName,
            sentAt = started,
            duration = Duration.ofNanos(System.nanoTime() - startNanos),
            request = sent,
            input = input,
            outcome = outcome,
        )

        private companion object {
            const val CONTENT_TYPE = "Content-Type"
            const val SEGMENT_BYTES = 64L * 1024L
            val METHODS_REQUIRING_BODY: Set<HttpMethod> =
                setOfNotNull(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.of("PROPPATCH"), HttpMethod.of("REPORT"))
        }
    }

private fun IOException.toNetworkError(): NetworkError {
    val kind =
        when (this) {
            is FileUnavailableException -> NetworkErrorKind.FILE_UNAVAILABLE
            is SocketTimeoutException -> NetworkErrorKind.TIMEOUT
            is UnknownHostException -> NetworkErrorKind.UNKNOWN_HOST
            is SSLException -> NetworkErrorKind.TLS
            is ConnectException, is NoRouteToHostException -> NetworkErrorKind.CONNECTION_FAILED
            is InterruptedIOException -> NetworkErrorKind.TIMEOUT
            else -> NetworkErrorKind.OTHER
        }
    return NetworkError(kind, message ?: javaClass.simpleName)
}
