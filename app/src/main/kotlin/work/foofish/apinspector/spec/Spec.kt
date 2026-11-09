package work.foofish.apinspector.spec

import kotlinx.serialization.Serializable
import java.time.Instant

/** API 文档的标识。 */
@JvmInline
@Serializable
value class SpecId(
    val value: String,
)

/** API 文档的来源；只有 [Url] 来源可以刷新（PRD 2）。 */
sealed interface SpecOrigin {
    /** 从本机文件导入；[displayName] 是导入时的文件名。 */
    data class File(
        val displayName: String?,
    ) : SpecOrigin

    data class Url(
        val url: String,
    ) : SpecOrigin

    data object Pasted : SpecOrigin
}

/** 首页列表中的一份 API 文档。 */
data class SpecSummary(
    val id: SpecId,
    val title: String,
    val version: String?,
    val origin: SpecOrigin,
    val operationCount: Int,
    val pinned: Boolean,
    /** 最近一次导入或刷新的时间。 */
    val updatedAt: Instant,
)
