package work.foofish.apinspector.request.data

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

/** 当前参数与预设。刷新 API 文档时的批量对齐见 SpecDao.applyRefresh。 */
@Dao
interface RequestInputDao {
    @Query("SELECT * FROM working_params WHERE spec_id = :specId AND method = :method AND path = :path")
    fun observeWorking(
        specId: String,
        method: String,
        path: String,
    ): Flow<WorkingParamsEntity?>

    @Query("SELECT * FROM working_params WHERE spec_id = :specId AND method = :method AND path = :path")
    suspend fun working(
        specId: String,
        method: String,
        path: String,
    ): WorkingParamsEntity?

    @Upsert
    suspend fun upsertWorking(working: WorkingParamsEntity)

    @Query("SELECT * FROM preset WHERE spec_id = :specId AND method = :method AND path = :path ORDER BY created_at")
    fun observePresets(
        specId: String,
        method: String,
        path: String,
    ): Flow<List<PresetEntity>>

    @Query("SELECT * FROM preset WHERE id = :id")
    suspend fun preset(id: String): PresetEntity?

    @Insert
    suspend fun insertPreset(preset: PresetEntity)

    @Query("UPDATE preset SET input = :input WHERE id = :id")
    suspend fun updatePresetInput(
        id: String,
        input: String,
    )

    @Query("UPDATE preset SET name = :name WHERE id = :id")
    suspend fun renamePreset(
        id: String,
        name: String,
    )

    @Query("DELETE FROM preset WHERE id = :id")
    suspend fun deletePreset(id: String)
}
