package work.foofish.apinspector.spec.data

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * API 文档的元数据。文档原文可能有数 MB，超出 CursorWindow 的单行上限，因此单独存为文件（见 [SpecContentStore]）。
 */
@Entity(tableName = "spec")
data class SpecEntity(
    @PrimaryKey val id: String,
    val title: String,
    val version: String?,
    @ColumnInfo(name = "origin_type") val originType: SpecOriginType,
    /** URL 来源的地址，或文件来源的文件名。 */
    @ColumnInfo(name = "origin_value") val originValue: String?,
    @ColumnInfo(name = "operation_count") val operationCount: Int,
    /** 置顶时间（epoch 毫秒）；未置顶为 `null`。 */
    @ColumnInfo(name = "pinned_at") val pinnedAt: Long?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    /** 当前环境（PRD 5）；不设外键，指向的环境不存在时退回第一个环境。 */
    @ColumnInfo(name = "current_environment_id") val currentEnvironmentId: String? = null,
)

enum class SpecOriginType {
    FILE,
    URL,
    PASTED,
}

/** 收藏的接口；API 文档删除时一并删除。 */
@Entity(
    tableName = "favorite_operation",
    primaryKeys = ["spec_id", "method", "path"],
    foreignKeys = [
        ForeignKey(entity = SpecEntity::class, parentColumns = ["id"], childColumns = ["spec_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("spec_id")],
)
data class FavoriteOperationEntity(
    @ColumnInfo(name = "spec_id") val specId: String,
    val method: String,
    val path: String,
)
