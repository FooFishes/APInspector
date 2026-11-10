package work.foofish.apinspector.openapi.request

/**
 * `application/x-www-form-urlencoded` 的"原文"形式（PRD 4.3）：表单字段 ⇄ `a=1&b=2`。
 *
 * 原文中的 `{{变量}}` 保持未编码，以便阅读与编辑；发送时先替换变量再整体编码（[encode]）。
 */
public object FormText {
    /** 编辑用的原文：字段编码后以 `&` 连接，变量引用原样保留。 */
    public fun format(fields: List<Field>): String =
        fields.joinToString(PAIR_SEPARATOR) { field ->
            Variables.mapLiterals(field.name, PercentEncoding::encodeForm) + NAME_SEPARATOR +
                Variables.mapLiterals(field.value, PercentEncoding::encodeForm)
        }

    /** 解析原文；空片段被忽略，没有 `=` 的片段视为值为空。 */
    public fun parse(text: String): List<Field> =
        text
            .trim()
            .split(PAIR_SEPARATOR)
            .filter { it.isNotEmpty() }
            .map { pair ->
                Field(
                    name = PercentEncoding.decodeForm(pair.substringBefore(NAME_SEPARATOR)),
                    value = PercentEncoding.decodeForm(pair.substringAfter(NAME_SEPARATOR, missingDelimiterValue = "")),
                )
            }

    /** 发送用的 Body：变量必须已经替换。 */
    internal fun encode(fields: List<Field>): String =
        fields.joinToString(PAIR_SEPARATOR) { PercentEncoding.encodeForm(it.name) + NAME_SEPARATOR + PercentEncoding.encodeForm(it.value) }

    private const val PAIR_SEPARATOR = "&"
    private const val NAME_SEPARATOR = "="
}
