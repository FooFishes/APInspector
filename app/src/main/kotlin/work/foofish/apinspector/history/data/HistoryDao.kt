package work.foofish.apinspector.history.data

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query(
        """
        SELECT id, method, path, sent_at, duration_ms, status_code, outcome, body_total FROM history
        WHERE spec_id = :specId ORDER BY id DESC
        """,
    )
    fun observeSummaries(specId: String): Flow<List<HistorySummaryRow>>

    @Query(
        """
        SELECT id, method, path, sent_at, duration_ms, status_code, outcome, body_total FROM history
        WHERE spec_id = :specId AND method = :method AND path = :path ORDER BY id DESC
        """,
    )
    fun observeSummaries(
        specId: String,
        method: String,
        path: String,
    ): Flow<List<HistorySummaryRow>>

    @Query("SELECT * FROM history WHERE id = :id")
    fun observe(id: Long): Flow<HistoryEntity?>

    @Query("SELECT * FROM history WHERE spec_id = :specId AND method = :method AND path = :path ORDER BY id DESC LIMIT 1")
    fun observeLatest(
        specId: String,
        method: String,
        path: String,
    ): Flow<HistoryEntity?>

    @Insert
    suspend fun insert(entry: HistoryEntity): Long

    @Query(
        """
        SELECT body_file FROM history WHERE spec_id = :specId AND body_file IS NOT NULL
        AND id NOT IN (SELECT id FROM history WHERE spec_id = :specId ORDER BY id DESC LIMIT :keep)
        """,
    )
    suspend fun overflowBodyFiles(
        specId: String,
        keep: Int,
    ): List<String>

    @Query(
        "DELETE FROM history WHERE spec_id = :specId AND id NOT IN (SELECT id FROM history WHERE spec_id = :specId ORDER BY id DESC LIMIT :keep)",
    )
    suspend fun deleteOverflow(
        specId: String,
        keep: Int,
    )

    /** 插入一条记录并淘汰超出上限的最旧记录，返回新记录的标识与被淘汰记录的 Body 文件。 */
    @Transaction
    suspend fun insertAndTrim(
        entry: HistoryEntity,
        keep: Int,
    ): Pair<Long, List<String>> {
        val id = insert(entry)
        val evicted = overflowBodyFiles(entry.specId, keep)
        deleteOverflow(entry.specId, keep)
        return id to evicted
    }

    @Query("SELECT body_file FROM history WHERE spec_id = :specId AND body_file IS NOT NULL")
    suspend fun bodyFiles(specId: String): List<String>

    @Query("DELETE FROM history WHERE spec_id = :specId")
    suspend fun clear(specId: String)
}
