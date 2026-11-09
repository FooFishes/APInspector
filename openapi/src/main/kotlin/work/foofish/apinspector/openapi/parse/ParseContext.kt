package work.foofish.apinspector.openapi.parse

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import work.foofish.apinspector.openapi.OpenApiVersion
import work.foofish.apinspector.openapi.ParseWarning
import work.foofish.apinspector.openapi.Resolvable
import work.foofish.apinspector.openapi.UnresolvedReason

/** 一次解析的共享状态：文档根节点、版本与收集到的警告。 */
internal class ParseContext(
    private val root: JsonObject,
    val version: OpenApiVersion,
) {
    private val collectedWarnings = mutableListOf<ParseWarning>()

    val warnings: List<ParseWarning> get() = collectedWarnings.toList()

    val document: ObjectNode = ObjectNode(root, JsonPointer.ROOT, this)

    /** OpenAPI 3.2 的 `$self`：文档自身的 URI，以它开头的引用其实指向本文档。 */
    private val selfUri: String? = (root[Field.SELF] as? JsonPrimitive)?.takeIf { it.isString }?.content

    fun warn(warning: ParseWarning) {
        collectedWarnings += warning
    }

    /** 在文档内定位 [ref]；外部引用、非法指针与不存在的位置分别给出原因。 */
    fun locate(ref: String): RefTarget {
        val fragment = selfUri?.takeIf(ref::startsWith)?.let { ref.substring(it.length) } ?: ref
        if (!fragment.startsWith(INTERNAL_REF_PREFIX)) return RefTarget.Missing(UnresolvedReason.EXTERNAL)
        val pointer = JsonPointer.fromFragment(fragment) ?: return RefTarget.Missing(UnresolvedReason.INVALID_POINTER)
        val element = pointer.evaluate(root) ?: return RefTarget.Missing(UnresolvedReason.NOT_FOUND)
        return RefTarget.Found(pointer, element)
    }

    /**
     * 解析可能以 `$ref` 给出的对象：沿引用链找到实际对象后交给 [read]。
     *
     * 引用无法解析时记录警告并返回 [Resolvable.Unresolved]；[read] 返回 `null`（对象本身不合法）时返回 `null`。
     */
    fun <T : Any> resolve(
        node: ObjectNode,
        read: (ObjectNode) -> T?,
    ): Resolvable<T>? {
        val visited = mutableSetOf(node.pointer)
        var current = node
        while (true) {
            val ref = current.reference() ?: return read(current)?.let { Resolvable.Resolved(it) }
            val reason =
                when (val target = locate(ref)) {
                    is RefTarget.Missing -> {
                        target.reason
                    }

                    is RefTarget.Found -> {
                        val element = target.element
                        when {
                            !visited.add(target.pointer) -> {
                                UnresolvedReason.CIRCULAR
                            }

                            element !is JsonObject -> {
                                UnresolvedReason.INVALID_TARGET
                            }

                            else -> {
                                current = ObjectNode(element, target.pointer, this)
                                null
                            }
                        }
                    }
                }
            if (reason != null) {
                warn(ParseWarning.UnresolvedReference(current.pointer.child(Field.REF).toFragment(), ref, reason))
                return Resolvable.Unresolved(ref, reason)
            }
        }
    }

    private companion object {
        const val INTERNAL_REF_PREFIX = "#"
    }
}

internal sealed interface RefTarget {
    data class Found(
        val pointer: JsonPointer,
        val element: JsonElement,
    ) : RefTarget

    data class Missing(
        val reason: UnresolvedReason,
    ) : RefTarget
}
