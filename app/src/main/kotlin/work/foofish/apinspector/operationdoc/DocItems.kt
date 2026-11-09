package work.foofish.apinspector.operationdoc

import kotlinx.collections.immutable.ImmutableList
import work.foofish.apinspector.openapi.OperationKey
import work.foofish.apinspector.openapi.ParameterLocation
import work.foofish.apinspector.openapi.UnresolvedReason

/** 文档页的展示状态：用户切换过展开状态的节点，以及各处选中的 content type。 */
data class DocViewState(
    val toggled: Set<String> = emptySet(),
    /** [DocItem.MediaTypes.ownerId] → 选中的 content type。 */
    val selectedContentTypes: Map<String, String> = emptyMap(),
)

/** 接口文档页顶部的概览。 */
data class OperationOverview(
    val key: OperationKey,
    val summary: String?,
    val description: String?,
    val operationId: String?,
    val deprecated: Boolean,
    val tags: ImmutableList<String>,
    /**
     * 可选的鉴权方式，满足其一即可；每一项列出需同时满足的方案名，空项表示可匿名访问。
     * 整体为空表示无需鉴权。
     */
    val security: ImmutableList<ImmutableList<String>>,
)

/**
 * 接口文档中按顺序展示的一行。整页被摊平为一个列表，以便惰性渲染大型 Schema。
 *
 * [id] 在同一接口的文档中唯一且稳定，用作列表键与展开状态的键。
 */
sealed interface DocItem {
    val id: String

    data class SectionTitle(
        override val id: String,
        val section: DocSection,
    ) : DocItem

    data class Note(
        override val id: String,
        val note: DocNote,
        val depth: Int,
    ) : DocItem

    data class Description(
        override val id: String,
        val text: String,
        val depth: Int,
    ) : DocItem

    data class ParameterGroupTitle(
        override val id: String,
        val location: ParameterLocation,
    ) : DocItem

    data class ParameterRow(
        override val id: String,
        val name: String,
        val required: Boolean,
        val deprecated: Boolean,
        val type: TypeLabel?,
        val description: String?,
        val facts: ImmutableList<SchemaFact>,
        val expandable: Boolean,
        val expanded: Boolean,
    ) : DocItem

    /** 某个请求体 / 响应的 content type 选择；[ownerId] 标识它属于哪一处。 */
    data class MediaTypes(
        override val id: String,
        val ownerId: String,
        val contentTypes: ImmutableList<String>,
        val selected: String,
        val depth: Int,
    ) : DocItem

    data class SchemaRow(
        override val id: String,
        val depth: Int,
        /** 根节点为 `null`。 */
        val label: NodeLabel?,
        val type: TypeLabel,
        val required: Boolean,
        val flags: SchemaFlags,
        val description: String?,
        val facts: ImmutableList<SchemaFact>,
        val expandable: Boolean,
        val expanded: Boolean,
    ) : DocItem

    data class ExampleBlock(
        override val id: String,
        val depth: Int,
        val title: ExampleTitle,
        val code: String,
    ) : DocItem

    data class ResponseRow(
        override val id: String,
        val status: String,
        val summary: String?,
        val description: String?,
        val expandable: Boolean,
        val expanded: Boolean,
    ) : DocItem

    data class HeaderRow(
        override val id: String,
        val depth: Int,
        val name: String,
        val required: Boolean,
        val type: TypeLabel?,
        val description: String?,
    ) : DocItem

    data class UnresolvedRow(
        override val id: String,
        val depth: Int,
        val ref: String,
        val reason: UnresolvedReason,
    ) : DocItem
}

enum class DocSection { PARAMETERS, REQUEST_BODY, RESPONSES }

enum class DocNote { NO_PARAMETERS, NO_REQUEST_BODY, NO_RESPONSES, BODY_REQUIRED, RESPONSE_HEADERS }

/** Schema 树中一个节点相对父节点的位置。 */
sealed interface NodeLabel {
    data class Property(
        val name: String,
    ) : NodeLabel

    /** `allOf` / `oneOf` / `anyOf` 的第 [index] 项（从 1 开始）。 */
    data class Composition(
        val keyword: String,
        val index: Int,
    ) : NodeLabel

    data object AdditionalProperties : NodeLabel

    data object Not : NodeLabel

    /** OpenAPI 3.2 流式内容中每一项的 Schema。 */
    data object ItemSchema : NodeLabel
}

/** 类型的简写，例如 `integer(int64)`、`array<User>`、`string | null`。 */
data class TypeLabel(
    val text: String,
    val kind: Kind,
) {
    enum class Kind {
        PLAIN,

        /** 指向另一个 Schema，可展开。 */
        REFERENCE,

        /** 未解析的引用；[text] 为原始引用。 */
        UNRESOLVED,
    }
}

data class SchemaFlags(
    val deprecated: Boolean,
    val readOnly: Boolean,
    val writeOnly: Boolean,
)

/** Schema 的约束与附加信息；数值与取值已格式化为显示文本。 */
sealed interface SchemaFact {
    data class Enum(
        val values: ImmutableList<String>,
    ) : SchemaFact

    data class Default(
        val value: String,
    ) : SchemaFact

    data class Minimum(
        val value: String,
        val exclusive: Boolean,
    ) : SchemaFact

    data class Maximum(
        val value: String,
        val exclusive: Boolean,
    ) : SchemaFact

    data class MinLength(
        val value: Long,
    ) : SchemaFact

    data class MaxLength(
        val value: Long,
    ) : SchemaFact

    data class MinItems(
        val value: Long,
    ) : SchemaFact

    data class MaxItems(
        val value: Long,
    ) : SchemaFact

    data object UniqueItems : SchemaFact

    data class Pattern(
        val regex: String,
    ) : SchemaFact

    data class Example(
        val value: String,
    ) : SchemaFact
}

sealed interface ExampleTitle {
    /** Media Type 的 `example` 字段。 */
    data object Single : ExampleTitle

    data class Named(
        val name: String,
        val summary: String?,
    ) : ExampleTitle

    /** OpenAPI 3.2 的 `serializedValue`。 */
    data class Serialized(
        val name: String,
    ) : ExampleTitle

    data class External(
        val name: String,
    ) : ExampleTitle
}
