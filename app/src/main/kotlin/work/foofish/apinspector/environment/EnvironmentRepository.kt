package work.foofish.apinspector.environment

import kotlinx.coroutines.flow.Flow
import okhttp3.Cookie
import work.foofish.apinspector.openapi.OpenApiDocument
import work.foofish.apinspector.spec.SpecId

/** 环境的管理与当前环境的选择（PRD 5）。 */
interface EnvironmentRepository {
    /** 按列表顺序。 */
    fun environments(specId: SpecId): Flow<List<Environment>>

    /** 当前环境；没有任何环境时为 `null`。 */
    fun current(specId: SpecId): Flow<Environment?>

    fun environment(id: EnvironmentId): Flow<Environment?>

    suspend fun find(id: EnvironmentId): Environment?

    suspend fun select(
        specId: SpecId,
        id: EnvironmentId,
    )

    /**
     * API 文档还没有任何环境时（例如在环境功能加入前导入的文档），按 `servers` 生成。
     * 环境至少保留一个（PRD 5），因此此后不会再生成。
     */
    suspend fun ensureEnvironments(
        specId: SpecId,
        document: OpenApiDocument,
    )

    /** 新建一个空白环境，排在最后。 */
    suspend fun create(
        specId: SpecId,
        name: String,
    ): EnvironmentId

    /** 复制环境（不含 Cookie），排在最后。 */
    suspend fun duplicate(
        id: EnvironmentId,
        name: String,
    ): EnvironmentId?

    /** 即时保存（PRD 5）。 */
    suspend fun update(environment: Environment)

    /** 删除环境；是当前环境时改选第一个。调用方保证不删除最后一个环境。 */
    suspend fun delete(id: EnvironmentId)
}

/** 每个环境的 Cookie 罐（PRD 6）：发送请求时读写，环境页查看与删除。 */
interface CookieStore {
    /** 未过期的 Cookie，随罐的变化更新。 */
    fun cookies(id: EnvironmentId): Flow<List<Cookie>>

    suspend fun deleteCookie(
        id: EnvironmentId,
        cookie: Cookie,
    )

    suspend fun clearCookies(id: EnvironmentId)

    /** 罐中全部未过期的 Cookie；是否适用于某个 URL 由调用方按 RFC 6265 判断（[Cookie.matches]）。 */
    suspend fun load(id: EnvironmentId): List<Cookie>

    suspend fun save(
        id: EnvironmentId,
        cookies: List<Cookie>,
    )
}
