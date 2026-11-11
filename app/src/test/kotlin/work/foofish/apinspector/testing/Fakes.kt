package work.foofish.apinspector.testing

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import okhttp3.Cookie
import okhttp3.MediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import work.foofish.apinspector.environment.CookieStore
import work.foofish.apinspector.environment.Environment
import work.foofish.apinspector.environment.EnvironmentId
import work.foofish.apinspector.environment.EnvironmentNetwork
import work.foofish.apinspector.environment.EnvironmentRepository
import work.foofish.apinspector.network.FileAccess
import work.foofish.apinspector.network.RequestSender
import work.foofish.apinspector.network.SendJob
import work.foofish.apinspector.openapi.OpenApiDocument
import work.foofish.apinspector.openapi.OperationKey
import work.foofish.apinspector.openapi.request.FileRef
import work.foofish.apinspector.openapi.request.RequestInput
import work.foofish.apinspector.openapi.request.environmentTemplates
import work.foofish.apinspector.request.Preset
import work.foofish.apinspector.request.PresetId
import work.foofish.apinspector.request.RequestInputRepository
import work.foofish.apinspector.request.RequestRef
import work.foofish.apinspector.request.WorkingParams
import work.foofish.apinspector.spec.SpecId
import java.io.File
import java.time.Instant

/** 内存中的环境仓库。 */
class FakeEnvironmentRepository :
    EnvironmentRepository,
    CookieStore {
    private val all = MutableStateFlow(emptyList<Environment>())
    private val currentIds = MutableStateFlow(emptyMap<SpecId, EnvironmentId>())
    private var nextId = 0

    /** 新增到末尾；已有同一标识的环境时原位替换。 */
    fun put(environment: Environment) {
        all.update { list ->
            if (list.any { it.id == environment.id }) list.map { if (it.id == environment.id) environment else it } else list + environment
        }
    }

    override fun environments(specId: SpecId): Flow<List<Environment>> = all.map { list -> list.filter { it.specId == specId } }

    override fun current(specId: SpecId): Flow<Environment?> =
        combine(environments(specId), currentIds) { list, ids -> list.firstOrNull { it.id == ids[specId] } ?: list.firstOrNull() }

    override fun environment(id: EnvironmentId): Flow<Environment?> = all.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun find(id: EnvironmentId): Environment? = all.value.firstOrNull { it.id == id }

    override suspend fun select(
        specId: SpecId,
        id: EnvironmentId,
    ) {
        currentIds.update { it + (specId to id) }
    }

    override suspend fun ensureEnvironments(
        specId: SpecId,
        document: OpenApiDocument,
    ) {
        if (all.value.any { it.specId == specId }) return
        document.environmentTemplates().forEach { template ->
            put(environment(specId, newId(), template.name, template.baseUrl).copy(variables = template.variables))
        }
    }

    override suspend fun create(
        specId: SpecId,
        name: String,
    ): EnvironmentId = newId().also { put(environment(specId, it, name, baseUrl = "")) }

    override suspend fun duplicate(
        id: EnvironmentId,
        name: String,
    ): EnvironmentId? {
        val source = find(id) ?: return null
        return newId().also { put(source.copy(id = it, name = name)) }
    }

    override suspend fun update(environment: Environment) = put(environment)

    override suspend fun delete(id: EnvironmentId) {
        all.update { list -> list.filterNot { it.id == id } }
    }

    override fun cookies(id: EnvironmentId): Flow<List<Cookie>> = MutableStateFlow(emptyList())

    override suspend fun deleteCookie(
        id: EnvironmentId,
        cookie: Cookie,
    ) = Unit

    override suspend fun clearCookies(id: EnvironmentId) = Unit

    override suspend fun load(id: EnvironmentId): List<Cookie> = emptyList()

    override suspend fun save(
        id: EnvironmentId,
        cookies: List<Cookie>,
    ) = Unit

    private fun newId() = EnvironmentId("env-${nextId++}")
}

fun environment(
    specId: SpecId,
    id: EnvironmentId,
    name: String,
    baseUrl: String,
): Environment = Environment(id, specId, name, baseUrl, variables = emptyList(), credentials = emptyMap(), network = EnvironmentNetwork())

/** 内存中的当前参数与预设。 */
class FakeRequestInputRepository : RequestInputRepository {
    private val working = MutableStateFlow(emptyMap<Pair<SpecId, OperationKey>, WorkingParams>())
    private val presetsByOperation = MutableStateFlow(emptyMap<Pair<SpecId, OperationKey>, List<Preset>>())
    private var nextId = 0

    /** 每次保存的当前参数，按顺序。 */
    val saved = mutableListOf<WorkingParams>()

    fun stored(
        specId: SpecId,
        operation: OperationKey,
    ): WorkingParams? = working.value[specId to operation]

    override fun working(
        specId: SpecId,
        operation: OperationKey,
    ): Flow<WorkingParams?> = working.map { it[specId to operation] }

    override suspend fun saveWorking(
        specId: SpecId,
        operation: OperationKey,
        working: WorkingParams,
    ) {
        saved += working
        this.working.update { it + ((specId to operation) to working) }
    }

    override fun presets(
        specId: SpecId,
        operation: OperationKey,
    ): Flow<List<Preset>> = presetsByOperation.map { it[specId to operation].orEmpty() }

    override suspend fun createPreset(
        specId: SpecId,
        operation: OperationKey,
        name: String,
        input: RequestInput,
    ): PresetId {
        val id = PresetId("preset-${nextId++}")
        presetsByOperation.update { it + ((specId to operation) to it[specId to operation].orEmpty() + Preset(id, name, input)) }
        saveWorking(specId, operation, WorkingParams(input, id))
        return id
    }

    override suspend fun updatePreset(
        id: PresetId,
        input: RequestInput,
    ) {
        presetsByOperation.update { all -> all.mapValues { (_, list) -> list.map { if (it.id == id) it.copy(input = input) else it } } }
    }

    override suspend fun renamePreset(
        id: PresetId,
        name: String,
    ) {
        presetsByOperation.update { all -> all.mapValues { (_, list) -> list.map { if (it.id == id) it.copy(name = name) else it } } }
    }

    override suspend fun deletePreset(id: PresetId) {
        presetsByOperation.update { all -> all.mapValues { (_, list) -> list.filterNot { it.id == id } } }
    }
}

/** 记录发送请求的执行器；请求一直在途，直到测试调用 [finish]。 */
class FakeRequestSender : RequestSender {
    private val running = MutableStateFlow(emptyMap<RequestRef, Instant>())
    val sent = mutableListOf<SendJob>()
    val cancelled = mutableListOf<RequestRef>()

    override val inFlight: StateFlow<Map<RequestRef, Instant>> = running

    override fun send(job: SendJob): Boolean {
        if (job.target in running.value) return false
        sent += job
        running.update { it + (job.target to Instant.EPOCH) }
        return true
    }

    override fun cancel(target: RequestRef) {
        cancelled += target
        finish(target)
    }

    fun finish(target: RequestRef) {
        running.update { it - target }
    }
}

/** 文件都可读；[unreadable] 中的 URI 除外。 */
class FakeFileAccess : FileAccess {
    val unreadable = mutableSetOf<String>()

    override suspend fun adopt(uri: Uri): FileRef? = null

    override suspend fun isReadable(file: FileRef): Boolean = file.uri !in unreadable

    override fun requestBody(
        file: FileRef,
        contentType: MediaType?,
    ): RequestBody = ByteArray(0).toRequestBody(contentType)

    override suspend fun save(
        from: File,
        to: Uri,
    ) = Unit
}
