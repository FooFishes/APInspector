package work.foofish.apinspector.openapi

/**
 * 刷新 API 文档时新旧接口的差异，按"HTTP 方法 + 路径"匹配（ADR-0001）。
 *
 * 接口本身相同、但它引用的 Schema 内容变了，也算作修改。
 */
public data class OperationDiff(
    /** 新文档中新增的接口，按新文档中的顺序。 */
    public val added: List<OperationKey>,
    /** 新文档中已不存在的接口，按旧文档中的顺序；它们的当前参数、预设等数据会被删除。 */
    public val removed: List<OperationKey>,
    /** 两边都存在但内容不同的接口，按新文档中的顺序。 */
    public val modified: List<OperationKey>,
) {
    public val isEmpty: Boolean get() = added.isEmpty() && removed.isEmpty() && modified.isEmpty()

    public companion object {
        public fun between(
            old: OpenApiDocument,
            new: OpenApiDocument,
        ): OperationDiff {
            val oldOperations = old.operations.associateBy(Operation::key)
            val newKeys = new.operations.mapTo(HashSet(), Operation::key)
            return OperationDiff(
                added = new.operations.map(Operation::key).filterNot(oldOperations::containsKey),
                removed = old.operations.map(Operation::key).filterNot(newKeys::contains),
                modified =
                    new.operations
                        .filter { operation ->
                            val previous = oldOperations[operation.key] ?: return@filter false
                            previous != operation || old.reachableSchemas(previous) != new.reachableSchemas(operation)
                        }.map(Operation::key),
            )
        }
    }
}
