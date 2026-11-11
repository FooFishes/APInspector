package work.foofish.apinspector.backup.data

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import work.foofish.apinspector.backup.Backup
import work.foofish.apinspector.custom.data.CustomRequestEntity
import work.foofish.apinspector.environment.data.EnvironmentEntity
import work.foofish.apinspector.extraction.data.ExtractionRuleEntity
import work.foofish.apinspector.history.data.HistoryEntity
import work.foofish.apinspector.request.data.PresetEntity
import work.foofish.apinspector.request.data.WorkingParamsEntity
import work.foofish.apinspector.spec.data.FavoriteOperationEntity
import work.foofish.apinspector.spec.data.SpecEntity

/** 备份用的整表读写（PRD 8）。 */
@Dao
interface BackupDao {
    @Query("SELECT * FROM spec")
    suspend fun specs(): List<SpecEntity>

    @Query("SELECT * FROM favorite_operation")
    suspend fun favorites(): List<FavoriteOperationEntity>

    @Query("SELECT * FROM environment")
    suspend fun environments(): List<EnvironmentEntity>

    @Query("SELECT * FROM preset")
    suspend fun presets(): List<PresetEntity>

    @Query("SELECT * FROM working_params")
    suspend fun working(): List<WorkingParamsEntity>

    @Query("SELECT * FROM custom_request")
    suspend fun customRequests(): List<CustomRequestEntity>

    @Query("SELECT * FROM extraction_rule ORDER BY id")
    suspend fun rules(): List<ExtractionRuleEntity>

    @Query("SELECT * FROM history ORDER BY id")
    suspend fun history(): List<HistoryEntity>

    @Insert
    suspend fun insertSpecs(rows: List<SpecEntity>)

    @Insert
    suspend fun insertFavorites(rows: List<FavoriteOperationEntity>)

    @Insert
    suspend fun insertEnvironments(rows: List<EnvironmentEntity>)

    @Insert
    suspend fun insertPresets(rows: List<PresetEntity>)

    @Insert
    suspend fun insertWorking(rows: List<WorkingParamsEntity>)

    @Insert
    suspend fun insertCustomRequests(rows: List<CustomRequestEntity>)

    @Insert
    suspend fun insertRules(rows: List<ExtractionRuleEntity>)

    @Insert
    suspend fun insertHistory(rows: List<HistoryEntity>)

    @Query("DELETE FROM history")
    suspend fun deleteHistory()

    @Query("DELETE FROM extraction_rule")
    suspend fun deleteRules()

    @Query("DELETE FROM custom_request")
    suspend fun deleteCustomRequests()

    /** 环境、Cookie、收藏、当前参数与预设随 API 文档级联删除。 */
    @Query("DELETE FROM spec")
    suspend fun deleteSpecs()

    /** 在同一个事务里读出全部表，各表之间保持一致。 */
    @Transaction
    suspend fun snapshot(
        schema: Int,
        createdAt: Long,
        includeHistory: Boolean,
    ): Backup =
        Backup(
            schema = schema,
            createdAt = createdAt,
            specs = emptyList(),
            favorites = favorites(),
            environments = environments(),
            presets = presets(),
            working = working(),
            customRequests = customRequests(),
            rules = rules(),
            proxy = null,
            history = if (includeHistory) history() else null,
        )

    /** "覆盖全部"：清空现有数据，再按外键依赖的顺序写入备份；中途失败则整体回滚。 */
    @Transaction
    suspend fun replaceAll(backup: Backup) {
        deleteHistory()
        deleteRules()
        deleteCustomRequests()
        deleteSpecs()
        insertSpecs(backup.specs.map { it.spec })
        insertEnvironments(backup.environments)
        insertPresets(backup.presets)
        insertWorking(backup.working)
        insertFavorites(backup.favorites)
        insertCustomRequests(backup.customRequests)
        insertRules(backup.rules)
        insertHistory(backup.history.orEmpty())
    }
}
