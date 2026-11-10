package work.foofish.apinspector.response

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * 响应 JSON 折叠树中的一行（PRD 7.2）。
 *
 * @property path JSONPath 形式的路径，如 `$.data[0].name`；长按可复制。
 * @property value 叶子的值（字符串不带引号）；容器为 `null`。
 */
data class JsonViewRow(
    val path: String,
    val depth: Int,
    val key: String?,
    val value: String?,
    val kind: JsonViewKind,
    val size: Int,
    val expanded: Boolean,
)

enum class JsonViewKind {
    OBJECT,
    ARRAY,
    STRING,
    NUMBER,
    BOOLEAN,
    NULL,
}

/**
 * 展开为行。[query] 不为空时只保留键或值包含它（不区分大小写）的行及其祖先，并全部展开。
 */
fun jsonViewRows(
    root: JsonElement,
    collapsed: Set<String>,
    query: String,
): ImmutableList<JsonViewRow> = JsonViewBuilder(collapsed, query.trim()).build(root)

private class JsonViewBuilder(
    private val collapsed: Set<String>,
    private val needle: String,
) {
    private val rows = mutableListOf<JsonViewRow>()
    private val filtering = needle.isNotEmpty()

    fun build(root: JsonElement): ImmutableList<JsonViewRow> {
        visit(root, ROOT_PATH, depth = 0, key = null)
        return rows.toImmutableList()
    }

    /** 加入 [element] 的行；过滤时返回它或其后代是否命中。 */
    private fun visit(
        element: JsonElement,
        path: String,
        depth: Int,
        key: String?,
    ): Boolean {
        val keyMatched = filtering && key?.contains(needle, ignoreCase = true) == true
        return when (element) {
            is JsonObject -> {
                container(
                    element.entries.map { (name, value) ->
                        Child(childPath(path, name), name, value)
                    },
                    JsonViewKind.OBJECT,
                    path,
                    depth,
                    key,
                    keyMatched,
                )
            }

            is JsonArray -> {
                container(
                    element.mapIndexed {
                        index,
                        value,
                        ->
                        Child("$path[$index]", "[$index]", value)
                    },
                    JsonViewKind.ARRAY,
                    path,
                    depth,
                    key,
                    keyMatched,
                )
            }

            is JsonPrimitive -> {
                primitive(element, path, depth, key, keyMatched)
            }
        }
    }

    private fun container(
        children: List<Child>,
        kind: JsonViewKind,
        path: String,
        depth: Int,
        key: String?,
        keyMatched: Boolean,
    ): Boolean {
        val expanded = filtering || path !in collapsed
        val position = rows.size
        rows += JsonViewRow(path, depth, key, value = null, kind, children.size, expanded)
        if (!expanded) return keyMatched
        // 先访问全部子节点（不能短路），再决定是否保留自己。
        val childMatched = children.map { visit(it.value, it.path, depth + 1, it.key) }.any { it }
        val keep = !filtering || keyMatched || childMatched
        if (!keep) rows.subList(position, rows.size).clear()
        return keep
    }

    private fun primitive(
        element: JsonPrimitive,
        path: String,
        depth: Int,
        key: String?,
        keyMatched: Boolean,
    ): Boolean {
        val keep = !filtering || keyMatched || element.content.contains(needle, ignoreCase = true)
        if (keep) rows += JsonViewRow(path, depth, key, element.content, element.kind(), size = 0, expanded = false)
        return keep
    }

    private data class Child(
        val path: String,
        val key: String,
        val value: JsonElement,
    )
}

private fun JsonPrimitive.kind(): JsonViewKind =
    when {
        this is JsonNull -> JsonViewKind.NULL
        isString -> JsonViewKind.STRING
        booleanOrNull != null -> JsonViewKind.BOOLEAN
        else -> JsonViewKind.NUMBER
    }

/** 成员路径：简单的键用 `.name`，其余用 `['name']`。 */
private fun childPath(
    parent: String,
    name: String,
): String = if (SIMPLE_KEY.matches(name)) "$parent.$name" else "$parent['${name.replace("'", "\\'")}']"

private const val ROOT_PATH = "$"
private val SIMPLE_KEY = Regex("[A-Za-z_][A-Za-z0-9_]*")
