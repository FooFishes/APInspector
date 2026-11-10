package work.foofish.apinspector.environment.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Cookie
import work.foofish.apinspector.environment.CookieStore
import work.foofish.apinspector.environment.Environment
import work.foofish.apinspector.environment.EnvironmentId
import work.foofish.apinspector.environment.EnvironmentNetwork
import work.foofish.apinspector.environment.EnvironmentRepository
import work.foofish.apinspector.openapi.OpenApiDocument
import work.foofish.apinspector.openapi.request.environmentTemplates
import work.foofish.apinspector.spec.SpecId
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultEnvironmentRepository
    @Inject
    constructor(
        private val dao: EnvironmentDao,
        private val clock: Clock,
    ) : EnvironmentRepository,
        CookieStore {
        /** 多个页面可能同时发现"还没有环境"，串行化以免重复生成。 */
        private val ensuring = Mutex()

        override fun environments(specId: SpecId): Flow<List<Environment>> =
            dao.observeAll(specId.value).map { environments -> environments.map(EnvironmentEntity::toEnvironment) }

        /** 记录的当前环境不存在时（如刚删除）退回第一个。 */
        override fun current(specId: SpecId): Flow<Environment?> =
            combine(environments(specId), dao.observeCurrentId(specId.value)) { environments, currentId ->
                environments.firstOrNull { it.id.value == currentId } ?: environments.firstOrNull()
            }

        override fun environment(id: EnvironmentId): Flow<Environment?> = dao.observe(id.value).map { it?.toEnvironment() }

        override suspend fun find(id: EnvironmentId): Environment? = dao.find(id.value)?.toEnvironment()

        override suspend fun select(
            specId: SpecId,
            id: EnvironmentId,
        ) {
            dao.setCurrent(specId.value, id.value)
        }

        override suspend fun ensureEnvironments(
            specId: SpecId,
            document: OpenApiDocument,
        ) {
            ensuring.withLock {
                if (dao.all(specId.value).isNotEmpty()) return
                val entities = document.environmentTemplates().mapIndexed { position, template -> template.toEntity(specId, position) }
                dao.insertAndSelect(specId.value, entities, currentId = null)
            }
        }

        override suspend fun create(
            specId: SpecId,
            name: String,
        ): EnvironmentId {
            val environment =
                Environment(
                    id = EnvironmentId(UUID.randomUUID().toString()),
                    specId = specId,
                    name = name,
                    baseUrl = "",
                    variables = emptyList(),
                    credentials = emptyMap(),
                    network = EnvironmentNetwork(),
                )
            insertLast(environment)
            return environment.id
        }

        override suspend fun duplicate(
            id: EnvironmentId,
            name: String,
        ): EnvironmentId? {
            val source = find(id) ?: return null
            val copy = source.copy(id = EnvironmentId(UUID.randomUUID().toString()), name = name)
            insertLast(copy)
            return copy.id
        }

        private suspend fun insertLast(environment: Environment) {
            val specId = environment.specId.value
            val currentId = dao.observeCurrentId(specId).first()
            dao.insertAndSelect(specId, listOf(environment.toEntity(dao.lastPosition(specId) + 1)), currentId)
        }

        override suspend fun update(environment: Environment) {
            val position = dao.find(environment.id.value)?.position ?: return
            dao.update(environment.toEntity(position))
        }

        override suspend fun delete(id: EnvironmentId) {
            val entity = dao.find(id.value) ?: return
            dao.deleteAndReselect(entity.specId, id.value, dao.observeCurrentId(entity.specId).first())
        }

        override fun cookies(id: EnvironmentId): Flow<List<Cookie>> =
            dao.observeCookies(id.value).map { cookies ->
                val now = clock.millis()
                cookies.filter { it.expiresAt > now }.map(CookieEntity::toCookie)
            }

        override suspend fun deleteCookie(
            id: EnvironmentId,
            cookie: Cookie,
        ) {
            dao.deleteCookie(id.value, cookie.name, cookie.domain, cookie.path)
        }

        override suspend fun clearCookies(id: EnvironmentId) {
            dao.clearCookies(id.value)
        }

        override suspend fun load(id: EnvironmentId): List<Cookie> {
            val now = clock.millis()
            return dao.cookies(id.value).filter { it.expiresAt > now }.map(CookieEntity::toCookie)
        }

        override suspend fun save(
            id: EnvironmentId,
            cookies: List<Cookie>,
        ) {
            if (cookies.isEmpty() || dao.find(id.value) == null) return
            dao.saveCookies(id.value, cookies.map { it.toEntity(id) }, clock.millis())
        }
    }
