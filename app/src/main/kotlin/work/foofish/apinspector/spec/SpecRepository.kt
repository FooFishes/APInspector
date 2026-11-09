package work.foofish.apinspector.spec

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import work.foofish.apinspector.openapi.OpenApiDocument
import work.foofish.apinspector.openapi.OpenApiParseError
import work.foofish.apinspector.openapi.OperationDiff
import work.foofish.apinspector.openapi.OperationKey
import work.foofish.apinspector.openapi.ParseWarning

/** API 文档的导入、存储与刷新。 */
interface SpecRepository {
    /** 全部 API 文档：置顶的在前（最近置顶的最前），其余按更新时间从新到旧。 */
    val specs: Flow<List<SpecSummary>>

    /** 单个 API 文档；被删除后发出 `null`。 */
    fun spec(id: SpecId): Flow<SpecSummary?>

    suspend fun import(source: ImportSource): ImportOutcome

    /** 读取并解析已保存的文档内容。 */
    suspend fun document(id: SpecId): DocumentLoad

    /** 拉取 URL 来源的最新内容并与当前内容比较，尚不保存。 */
    suspend fun prepareRefresh(id: SpecId): RefreshPreparation

    /** 保存 [preview] 中的新内容，并删除已消失接口的数据。 */
    suspend fun applyRefresh(preview: RefreshPreview)

    suspend fun setPinned(
        id: SpecId,
        pinned: Boolean,
    )

    suspend fun delete(id: SpecId)
}

/** 接口收藏（PRD 3）。 */
interface FavoriteOperationRepository {
    fun favorites(specId: SpecId): Flow<Set<OperationKey>>

    suspend fun setFavorite(
        specId: SpecId,
        key: OperationKey,
        favorite: Boolean,
    )
}

/** 导入 API 文档的三种方式（PRD 2）。 */
sealed interface ImportSource {
    data class File(
        val uri: Uri,
    ) : ImportSource

    data class Url(
        val url: String,
    ) : ImportSource

    data class Text(
        val text: String,
    ) : ImportSource
}

sealed interface ImportOutcome {
    data class Imported(
        val id: SpecId,
        val title: String,
        val warnings: List<ParseWarning>,
    ) : ImportOutcome

    data class Failed(
        val failure: ImportFailure,
    ) : ImportOutcome
}

/** 获取或解析文档内容失败的原因。 */
sealed interface ImportFailure {
    data object InvalidUrl : ImportFailure

    data class HttpStatus(
        val code: Int,
    ) : ImportFailure

    /** 网络或文件读取失败；[detail] 是底层异常的说明。 */
    data class Unreadable(
        val detail: String,
    ) : ImportFailure

    data class TooLarge(
        val limitBytes: Long,
    ) : ImportFailure

    data class Unparseable(
        val error: OpenApiParseError,
    ) : ImportFailure

    /** 已保存的文档原文丢失（存储被清理或损坏）。 */
    data object StoredContentMissing : ImportFailure
}

/** 已解析的文档内容及其解析警告。 */
data class LoadedDocument(
    val document: OpenApiDocument,
    val warnings: List<ParseWarning>,
)

sealed interface DocumentLoad {
    data class Loaded(
        val content: LoadedDocument,
    ) : DocumentLoad

    /** 文档已被删除。 */
    data object Missing : DocumentLoad

    /** 已保存的内容无法读取或解析（例如存储损坏）。 */
    data class Failed(
        val failure: ImportFailure,
    ) : DocumentLoad
}

sealed interface RefreshPreparation {
    data class Ready(
        val preview: RefreshPreview,
    ) : RefreshPreparation

    data class Failed(
        val failure: ImportFailure,
    ) : RefreshPreparation
}

/**
 * 刷新前的预览：新内容已拉取并解析，但尚未保存。
 *
 * 不是 data class：[source] 可能有数 MB，不应出现在 `toString()` 或相等比较中。
 */
class RefreshPreview(
    val specId: SpecId,
    val diff: OperationDiff,
    /** 新文档的原文，应用刷新时原样保存。 */
    val source: String,
    val content: LoadedDocument,
)
