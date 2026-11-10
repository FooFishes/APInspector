package work.foofish.apinspector.environment.data

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

/** 环境与其 Cookie 罐。 */
@Dao
interface EnvironmentDao {
    @Query("SELECT * FROM environment WHERE spec_id = :specId ORDER BY position")
    fun observeAll(specId: String): Flow<List<EnvironmentEntity>>

    @Query("SELECT * FROM environment WHERE spec_id = :specId ORDER BY position")
    suspend fun all(specId: String): List<EnvironmentEntity>

    @Query("SELECT * FROM environment WHERE id = :id")
    fun observe(id: String): Flow<EnvironmentEntity?>

    @Query("SELECT * FROM environment WHERE id = :id")
    suspend fun find(id: String): EnvironmentEntity?

    @Query("SELECT current_environment_id FROM spec WHERE id = :specId")
    fun observeCurrentId(specId: String): Flow<String?>

    @Query("UPDATE spec SET current_environment_id = :environmentId WHERE id = :specId")
    suspend fun setCurrent(
        specId: String,
        environmentId: String?,
    )

    @Query("SELECT COALESCE(MAX(position), -1) FROM environment WHERE spec_id = :specId")
    suspend fun lastPosition(specId: String): Int

    @Insert
    suspend fun insert(environments: List<EnvironmentEntity>)

    @Update
    suspend fun update(environment: EnvironmentEntity)

    @Query("DELETE FROM environment WHERE id = :id")
    suspend fun delete(id: String)

    /** 删除环境；它是当前环境时改为列表中的第一个。 */
    @Transaction
    suspend fun deleteAndReselect(
        specId: String,
        id: String,
        currentId: String?,
    ) {
        delete(id)
        if (currentId == id) setCurrent(specId, all(specId).firstOrNull()?.id)
    }

    /** 插入环境，并在当前环境为空时选中第一个。 */
    @Transaction
    suspend fun insertAndSelect(
        specId: String,
        environments: List<EnvironmentEntity>,
        currentId: String?,
    ) {
        insert(environments)
        if (currentId == null) setCurrent(specId, environments.firstOrNull()?.id)
    }

    @Query("SELECT * FROM cookie WHERE environment_id = :environmentId ORDER BY domain, path, name")
    fun observeCookies(environmentId: String): Flow<List<CookieEntity>>

    @Query("SELECT * FROM cookie WHERE environment_id = :environmentId")
    suspend fun cookies(environmentId: String): List<CookieEntity>

    @Upsert
    suspend fun upsertCookies(cookies: List<CookieEntity>)

    @Query("DELETE FROM cookie WHERE environment_id = :environmentId AND name = :name AND domain = :domain AND path = :path")
    suspend fun deleteCookie(
        environmentId: String,
        name: String,
        domain: String,
        path: String,
    )

    @Query("DELETE FROM cookie WHERE environment_id = :environmentId AND expires_at <= :now")
    suspend fun deleteExpiredCookies(
        environmentId: String,
        now: Long,
    )

    @Query("DELETE FROM cookie WHERE environment_id = :environmentId")
    suspend fun clearCookies(environmentId: String)

    /** 保存响应带来的 Cookie：已过期的（服务端要求删除）移除，其余新增或覆盖。 */
    @Transaction
    suspend fun saveCookies(
        environmentId: String,
        cookies: List<CookieEntity>,
        now: Long,
    ) {
        upsertCookies(cookies)
        deleteExpiredCookies(environmentId, now)
    }
}
