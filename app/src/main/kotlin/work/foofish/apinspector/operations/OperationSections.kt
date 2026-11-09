package work.foofish.apinspector.operations

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import work.foofish.apinspector.openapi.OpenApiDocument
import work.foofish.apinspector.openapi.Operation
import work.foofish.apinspector.openapi.OperationKey
import work.foofish.apinspector.openapi.Tag

/** 接口列表中的分组（PRD 3）。 */
sealed interface SectionId {
    /** 收藏的接口，置于最上方。 */
    data object Favorites : SectionId

    data class Tagged(
        val name: String,
    ) : SectionId

    /** 没有 tag 的接口。 */
    data object Untagged : SectionId
}

data class OperationSection(
    val id: SectionId,
    /** [SectionId.Tagged] 在文档中声明的 tag 信息；未声明的 tag 为 `null`。 */
    val tag: Tag?,
    val operations: ImmutableList<OperationRow>,
    val collapsed: Boolean,
)

data class OperationRow(
    val key: OperationKey,
    val summary: String?,
    val deprecated: Boolean,
    val favorite: Boolean,
)

/**
 * 把文档中的接口分组：收藏、各 tag（先按文档声明的顺序，再按首次出现的顺序）、未分组。
 *
 * 一个接口有多个 tag 时出现在每个分组中；收藏的接口同时出现在原分组中。
 * 搜索时只保留匹配的接口与非空分组，并忽略折叠状态，以免匹配项被藏起来。
 */
fun buildSections(
    document: OpenApiDocument,
    favorites: Set<OperationKey>,
    query: String,
    collapsed: Set<SectionId>,
): ImmutableList<OperationSection> {
    val keyword = query.trim()
    val searching = keyword.isNotEmpty()
    val operations = if (searching) document.operations.filter { it.matches(keyword) } else document.operations
    val declaredTags = document.tags.associateBy(Tag::name)
    val tagOrder = LinkedHashSet<String>()
    document.tags.mapTo(tagOrder, Tag::name)
    operations.flatMapTo(tagOrder, Operation::tags)

    fun section(
        id: SectionId,
        members: List<Operation>,
    ): OperationSection? =
        members.takeIf { it.isNotEmpty() }?.let {
            OperationSection(
                id = id,
                tag = (id as? SectionId.Tagged)?.let { tagged -> declaredTags[tagged.name] },
                operations = members.map { operation -> operation.toRow(operation.key in favorites) }.toImmutableList(),
                collapsed = !searching && id in collapsed,
            )
        }

    return buildList {
        section(SectionId.Favorites, operations.filter { it.key in favorites })?.let(::add)
        tagOrder.forEach { name -> section(SectionId.Tagged(name), operations.filter { name in it.tags })?.let(::add) }
        section(SectionId.Untagged, operations.filter { it.tags.isEmpty() })?.let(::add)
    }.toImmutableList()
}

/** 按路径、summary、operationId 匹配，忽略大小写。 */
private fun Operation.matches(keyword: String): Boolean =
    listOfNotNull(key.path, summary, operationId).any { it.contains(keyword, ignoreCase = true) }

private fun Operation.toRow(favorite: Boolean): OperationRow = OperationRow(key, summary, deprecated, favorite)
