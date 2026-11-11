package work.foofish.apinspector.custom.data

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomRequestDao {
    /** `IS` 在 [specId] 为 `null` 时匹配临时请求。 */
    @Query("SELECT * FROM custom_request WHERE spec_id IS :specId ORDER BY created_at, id")
    fun observeAll(specId: String?): Flow<List<CustomRequestEntity>>

    @Query("SELECT * FROM custom_request WHERE id = :id")
    fun observe(id: String): Flow<CustomRequestEntity?>

    @Query("SELECT * FROM custom_request WHERE id = :id")
    suspend fun find(id: String): CustomRequestEntity?

    @Insert
    suspend fun insert(request: CustomRequestEntity)

    @Query("UPDATE custom_request SET name = :name, method = :method, url = :url, input = :input, variables = :variables WHERE id = :id")
    suspend fun update(
        id: String,
        name: String,
        method: String,
        url: String,
        input: String,
        variables: String,
    )

    @Query("UPDATE custom_request SET spec_id = :specId, url = :url, variables = :variables WHERE id = :id")
    suspend fun reassign(
        id: String,
        specId: String,
        url: String,
        variables: String,
    )

    @Query("UPDATE history SET spec_id = :specId WHERE custom_id = :id")
    suspend fun reassignHistory(
        id: String,
        specId: String,
    )

    /** 移动到 API 文档：请求记录随之归入该文档。 */
    @Transaction
    suspend fun move(
        id: String,
        specId: String,
        url: String,
        variables: String,
    ) {
        reassign(id, specId, url, variables)
        reassignHistory(id, specId)
    }

    @Query("SELECT body_file FROM history WHERE custom_id = :id AND body_file IS NOT NULL")
    suspend fun bodyFiles(id: String): List<String>

    @Query("DELETE FROM custom_request WHERE id = :id")
    suspend fun delete(id: String)
}
