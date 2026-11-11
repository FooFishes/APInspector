package work.foofish.apinspector.spec.data

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow
import work.foofish.apinspector.environment.data.EnvironmentEntity
import work.foofish.apinspector.request.data.PresetEntity
import work.foofish.apinspector.request.data.WorkingParamsEntity

/** API 文档及其附属数据（收藏的接口、环境、当前参数与预设）中需要一起变更的部分。 */
@Dao
interface SpecDao {
    @Query("SELECT * FROM spec ORDER BY pinned_at IS NULL, pinned_at DESC, updated_at DESC")
    fun observeAll(): Flow<List<SpecEntity>>

    @Query("SELECT * FROM spec WHERE id = :id")
    fun observe(id: String): Flow<SpecEntity?>

    @Query("SELECT * FROM spec WHERE id = :id")
    suspend fun find(id: String): SpecEntity?

    @Insert
    suspend fun insert(spec: SpecEntity)

    @Insert
    suspend fun insertEnvironments(environments: List<EnvironmentEntity>)

    /** 导入：保存元数据与按 `servers` 生成的环境，[spec] 已指向第一个环境。 */
    @Transaction
    suspend fun insertImported(
        spec: SpecEntity,
        environments: List<EnvironmentEntity>,
    ) {
        insert(spec)
        insertEnvironments(environments)
    }

    @Update
    suspend fun update(spec: SpecEntity)

    @Query("UPDATE spec SET pinned_at = :pinnedAt WHERE id = :id")
    suspend fun setPinnedAt(
        id: String,
        pinnedAt: Long?,
    )

    /** 收藏等附属数据通过外键级联删除。 */
    @Query("DELETE FROM spec WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM favorite_operation WHERE spec_id = :specId")
    fun observeFavorites(specId: String): Flow<List<FavoriteOperationEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFavorite(favorite: FavoriteOperationEntity)

    @Query("DELETE FROM favorite_operation WHERE spec_id = :specId AND method = :method AND path = :path")
    suspend fun deleteFavorite(
        specId: String,
        method: String,
        path: String,
    )

    @Query("SELECT * FROM working_params WHERE spec_id = :specId")
    suspend fun allWorking(specId: String): List<WorkingParamsEntity>

    @Query("SELECT * FROM preset WHERE spec_id = :specId")
    suspend fun allPresets(specId: String): List<PresetEntity>

    @Upsert
    suspend fun upsertWorking(working: List<WorkingParamsEntity>)

    @Upsert
    suspend fun upsertPresets(presets: List<PresetEntity>)

    @Query("DELETE FROM working_params WHERE spec_id = :specId AND method = :method AND path = :path")
    suspend fun deleteWorking(
        specId: String,
        method: String,
        path: String,
    )

    @Query("DELETE FROM preset WHERE spec_id = :specId AND method = :method AND path = :path")
    suspend fun deletePresets(
        specId: String,
        method: String,
        path: String,
    )

    @Query("DELETE FROM extraction_rule WHERE spec_id = :specId AND method = :method AND path = :path")
    suspend fun deleteRules(
        specId: String,
        method: String,
        path: String,
    )

    /**
     * 刷新：更新元数据，删除已消失接口的收藏、当前参数、预设与提取规则，并写入与新文档对齐后的当前参数与预设（PRD 2、4.1）。
     */
    @Transaction
    suspend fun applyRefresh(
        spec: SpecEntity,
        removed: List<FavoriteOperationEntity>,
        working: List<WorkingParamsEntity>,
        presets: List<PresetEntity>,
    ) {
        update(spec)
        removed.forEach {
            deleteFavorite(it.specId, it.method, it.path)
            deleteWorking(it.specId, it.method, it.path)
            deletePresets(it.specId, it.method, it.path)
            deleteRules(it.specId, it.method, it.path)
        }
        upsertPresets(presets)
        upsertWorking(working)
    }
}
