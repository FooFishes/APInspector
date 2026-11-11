package work.foofish.apinspector.environment.data

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import kotlinx.serialization.Serializable
import work.foofish.apinspector.spec.data.SpecEntity

/** 环境；API 文档删除时一并删除。结构化字段以 JSON 文本保存（见 StorageJson）。 */
@Serializable
@Entity(
    tableName = "environment",
    foreignKeys = [
        ForeignKey(entity = SpecEntity::class, parentColumns = ["id"], childColumns = ["spec_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("spec_id")],
)
data class EnvironmentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "spec_id") val specId: String,
    val name: String,
    @ColumnInfo(name = "base_url") val baseUrl: String,
    /** `List<EnvironmentVariable>`。 */
    val variables: String,
    /** `Map<String, Credential>`。 */
    val credentials: String,
    /** `EnvironmentNetwork`。 */
    val network: String,
    /** 在环境列表中的顺序。 */
    val position: Int,
)

/** Cookie 罐中的一个 Cookie（PRD 6）；环境删除时一并删除。 */
@Serializable
@Entity(
    tableName = "cookie",
    primaryKeys = ["environment_id", "name", "domain", "path"],
    foreignKeys = [
        ForeignKey(
            entity = EnvironmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["environment_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("environment_id")],
)
data class CookieEntity(
    @ColumnInfo(name = "environment_id") val environmentId: String,
    val name: String,
    val value: String,
    val domain: String,
    val path: String,
    /** 过期时间（epoch 毫秒）；会话 Cookie 也持久保存，此时为最大值。 */
    @ColumnInfo(name = "expires_at") val expiresAt: Long,
    val secure: Boolean,
    @ColumnInfo(name = "http_only") val httpOnly: Boolean,
    @ColumnInfo(name = "host_only") val hostOnly: Boolean,
    /** 是否带有 Expires / Max-Age；为 `false` 时是会话 Cookie。 */
    val persistent: Boolean,
)
