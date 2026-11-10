package work.foofish.apinspector.openapi.request

/**
 * 变量引用 `{{name}}`（PRD 5，ADR-0002）。
 *
 * 变量名由字母、数字、`_`、`-`、`.` 组成；其他 `{{…}}`（例如 JSON 中本来就有的字面量）不是变量，按原文保留。
 */
public object Variables {
    private val REFERENCE = Regex("""\{\{([A-Za-z0-9_.\-]+)\}\}""")

    /** [name] 是否可以作为变量名。 */
    public fun isValidName(name: String): Boolean = REFERENCE.matches("{{$name}}")

    /** [text] 中引用的变量名，按首次出现的顺序。 */
    public fun references(text: String): Set<String> = REFERENCE.findAll(text).mapTo(LinkedHashSet()) { it.groupValues[1] }

    /** 以 `{{name}}` 引用 [name]。 */
    public fun reference(name: String): String = "{{$name}}"

    /** 把 [text] 中的变量替换为 [values] 中的值；值未定义或为空的引用保留原文。 */
    public fun resolve(
        text: String,
        values: Map<String, String>,
    ): String = substitute(text, values, missing = HashSet())

    /**
     * 把 [text] 中的变量替换为 [values] 中的值；值未定义或为空的引用保留原文，并把变量名记入 [missing]。
     */
    internal fun substitute(
        text: String,
        values: Map<String, String>,
        missing: MutableSet<String>,
    ): String =
        REFERENCE.replace(text) { match ->
            val name = match.groupValues[1]
            values[name]?.takeIf { it.isNotEmpty() } ?: match.value.also { missing += name }
        }

    /** [text] 恰好是一个变量引用时返回变量名。 */
    internal fun wholeReference(text: String): String? = REFERENCE.matchEntire(text)?.groupValues?.get(1)

    /** 在 [text] 中逐段处理：变量引用原样保留，其余部分交给 [transform]。 */
    internal fun mapLiterals(
        text: String,
        transform: (String) -> String,
    ): String =
        buildString {
            var start = 0
            REFERENCE.findAll(text).forEach { match ->
                append(transform(text.substring(start, match.range.first)))
                append(match.value)
                start = match.range.last + 1
            }
            append(transform(text.substring(start)))
        }
}
