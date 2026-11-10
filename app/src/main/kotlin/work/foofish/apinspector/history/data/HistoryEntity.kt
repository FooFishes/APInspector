package work.foofish.apinspector.history.data

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import work.foofish.apinspector.history.NetworkErrorKind
import work.foofish.apinspector.history.OutcomeKind
import work.foofish.apinspector.history.RequestSource
import work.foofish.apinspector.spec.data.SpecEntity

/**
 * 请求记录（PRD 7.1）；API 文档删除时一并删除。响应 Body 以文件存于缓存目录，这里只保存文件名。
 *
 * 不对环境设外键：环境删除后记录仍保留其名称。
 */
@Entity(
    tableName = "history",
    foreignKeys = [
        ForeignKey(entity = SpecEntity::class, parentColumns = ["id"], childColumns = ["spec_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("spec_id", "id"), Index("spec_id", "method", "path", "id")],
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "spec_id") val specId: String,
    val method: String,
    val path: String,
    @ColumnInfo(name = "environment_id") val environmentId: String?,
    @ColumnInfo(name = "environment_name") val environmentName: String?,
    val source: RequestSource,
    @ColumnInfo(name = "preset_name") val presetName: String?,
    @ColumnInfo(name = "sent_at") val sentAt: Long,
    @ColumnInfo(name = "duration_ms") val durationMillis: Long,
    /** `HttpRequest` 的 JSON。 */
    val request: String,
    /** `RequestInput` 的 JSON。 */
    val input: String?,
    val outcome: OutcomeKind,
    @ColumnInfo(name = "status_code") val statusCode: Int?,
    @ColumnInfo(name = "status_message") val statusMessage: String?,
    val protocol: String?,
    @ColumnInfo(name = "final_url") val finalUrl: String?,
    /** `List<Field>` 的 JSON。 */
    @ColumnInfo(name = "response_headers") val responseHeaders: String?,
    @ColumnInfo(name = "content_type") val contentType: String?,
    @ColumnInfo(name = "body_file") val bodyFile: String?,
    @ColumnInfo(name = "body_total") val bodyTotal: Long?,
    @ColumnInfo(name = "body_stored") val bodyStored: Long?,
    @ColumnInfo(name = "dns_ms") val dnsMillis: Long?,
    @ColumnInfo(name = "connect_ms") val connectMillis: Long?,
    @ColumnInfo(name = "tls_ms") val tlsMillis: Long?,
    @ColumnInfo(name = "first_byte_ms") val firstByteMillis: Long?,
    @ColumnInfo(name = "error_kind") val errorKind: NetworkErrorKind?,
    @ColumnInfo(name = "error_detail") val errorDetail: String?,
)

/** 列表用的摘要列。 */
data class HistorySummaryRow(
    val id: Long,
    val method: String,
    val path: String,
    @ColumnInfo(name = "sent_at") val sentAt: Long,
    @ColumnInfo(name = "duration_ms") val durationMillis: Long,
    @ColumnInfo(name = "status_code") val statusCode: Int?,
    val outcome: OutcomeKind,
    @ColumnInfo(name = "body_total") val bodyTotal: Long?,
)
