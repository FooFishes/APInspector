package work.foofish.apinspector.spec.data

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow

/** API 文档及其附属数据（收藏的接口）。 */
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

    /** 刷新：更新元数据，并删除已消失接口的附属数据。 */
    @Transaction
    suspend fun applyRefresh(
        spec: SpecEntity,
        removed: List<FavoriteOperationEntity>,
    ) {
        update(spec)
        removed.forEach { deleteFavorite(it.specId, it.method, it.path) }
    }
}
