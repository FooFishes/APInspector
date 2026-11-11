package work.foofish.apinspector.custom.data

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import kotlinx.serialization.Serializable
import work.foofish.apinspector.spec.data.SpecEntity

/**
 * 自定义请求（PRD 4.2）；[specId] 为 `null` 时是临时请求。API 文档删除时一并删除。
 */
@Serializable
@Entity(
    tableName = "custom_request",
    foreignKeys = [
        ForeignKey(entity = SpecEntity::class, parentColumns = ["id"], childColumns = ["spec_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("spec_id", "created_at")],
)
data class CustomRequestEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "spec_id") val specId: String?,
    val name: String,
    val method: String,
    val url: String,
    /** `RequestInput` 的 JSON。 */
    val input: String,
    /** `List<EnvironmentVariable>` 的 JSON。 */
    val variables: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
