package work.foofish.apinspector.openapi

/**
 * 文档中可以用 `$ref` 引用给出的对象（参数、请求体、响应等）。
 *
 * 引用指向文档内部的有效对象时为 [Resolved]；否则为 [Unresolved]，保留原始引用，
 * 以便界面在对应位置标记"未解析的引用"而不影响其余部分（PRD 2）。
 */
public sealed interface Resolvable<out T> {
    public data class Resolved<out T>(
        public val value: T,
    ) : Resolvable<T>

    public data class Unresolved(
        public val ref: String,
        public val reason: UnresolvedReason,
    ) : Resolvable<Nothing>
}

/** 引用无法解析的原因。 */
public enum class UnresolvedReason {
    /** 指向其他文件或 URL 的外部引用；只解析文档内部引用（PRD 2）。 */
    EXTERNAL,

    /** 引用不是合法的 JSON Pointer 片段。 */
    INVALID_POINTER,

    /** 文档中不存在引用的位置。 */
    NOT_FOUND,

    /** 引用的位置存在，但不是所需类型的对象。 */
    INVALID_TARGET,

    /** 引用链首尾相接，永远到达不了实际对象。 */
    CIRCULAR,
}
