package work.foofish.apinspector.request.data

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import kotlinx.serialization.Serializable
import work.foofish.apinspector.spec.data.SpecEntity

/**
 * 接口的当前参数（PRD 4.1）；API 文档删除、或刷新后接口消失时一并删除。
 *
 * @property presetId 当前参数加载自哪个预设；预设删除后置空。
 */
@Serializable
@Entity(
    tableName = "working_params",
    primaryKeys = ["spec_id", "method", "path"],
    foreignKeys = [
        ForeignKey(entity = SpecEntity::class, parentColumns = ["id"], childColumns = ["spec_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PresetEntity::class, parentColumns = ["id"], childColumns = ["preset_id"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("preset_id")],
)
data class WorkingParamsEntity(
    @ColumnInfo(name = "spec_id") val specId: String,
    val method: String,
    val path: String,
    /** `RequestInput` 的 JSON。 */
    val input: String,
    @ColumnInfo(name = "preset_id") val presetId: String?,
)

/** 接口的命名预设（PRD 4.1）；同一接口内名称不重复。 */
@Serializable
@Entity(
    tableName = "preset",
    foreignKeys = [
        ForeignKey(entity = SpecEntity::class, parentColumns = ["id"], childColumns = ["spec_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("spec_id", "method", "path", "name", unique = true)],
)
data class PresetEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "spec_id") val specId: String,
    val method: String,
    val path: String,
    val name: String,
    /** `RequestInput` 的 JSON。 */
    val input: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
