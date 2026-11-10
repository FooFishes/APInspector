package work.foofish.apinspector.request.ui

import work.foofish.apinspector.openapi.request.JsonNode
import work.foofish.apinspector.request.body.JsonPath

/** 可视化 JSON 编辑的操作（PRD 4.3）。 */
internal class JsonEditorActions(
    val onToggle: (JsonPath) -> Unit,
    val onSet: (JsonPath, JsonNode) -> Unit,
    val onRemove: (JsonPath) -> Unit,
    val onAddMember: (JsonPath, String) -> Unit,
    /** 请求输入字段名后添加（schema 外的字段）。 */
    val onRequestMember: (JsonPath) -> Unit,
    val onAddItem: (JsonPath) -> Unit,
    val onSelectVariant: (JsonPath, Int) -> Unit,
)
