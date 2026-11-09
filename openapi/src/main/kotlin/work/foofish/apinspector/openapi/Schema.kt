package work.foofish.apinspector.openapi

import kotlinx.serialization.json.JsonElement
import java.math.BigDecimal

/**
 * Schema Object，已归一化 OpenAPI 3.0 与 3.1 的差异。
 *
 * 文档内部的 `$ref` 不会被内联展开，而是保留为 [Reference]，目标登记在 [OpenApiDocument.schemas] 中。
 * 因此循环引用（如树形结构）不会导致无限展开，界面可以按需逐层展开引用。
 */
public sealed interface Schema {
    /**
     * 指向文档内部另一个 Schema 的引用。
     *
     * @property ref 规范化后的 URI 片段，例如 `#/components/schemas/User`；必定是 [OpenApiDocument.schemas] 的键。
     * @property name 引用路径的最后一段，例如 `User`，用于显示。
     * @property description OpenAPI 3.1 允许在 `$ref` 旁覆盖的描述。
     */
    public data class Reference(
        public val ref: String,
        public val name: String,
        public val description: String?,
    ) : Schema

    /** 无法解析的引用，界面上标记为"未解析的引用"。 */
    public data class Unresolved(
        public val ref: String,
        public val reason: UnresolvedReason,
    ) : Schema

    /**
     * 一个具体的 Schema 定义。所有字段缺省即"不约束"，因此 `Definition()` 表示接受任意值。
     *
     * @property types 允许的类型；为空表示不限类型。OpenAPI 3.0 的 `nullable: true` 归一化为包含 [SchemaType.NULL]。
     * @property enumValues `enum` 的取值；只有 `const` 时归一化为单个取值。
     * @property examples 3.0 的 `example` 与 3.1 的 `examples` 合并后的示例值。
     */
    public data class Definition(
        public val title: String? = null,
        public val description: String? = null,
        public val types: Set<SchemaType> = emptySet(),
        public val format: String? = null,
        public val properties: Map<String, Schema> = emptyMap(),
        public val required: Set<String> = emptySet(),
        public val additionalProperties: Schema? = null,
        public val items: Schema? = null,
        public val allOf: List<Schema> = emptyList(),
        public val oneOf: List<Schema> = emptyList(),
        public val anyOf: List<Schema> = emptyList(),
        public val not: Schema? = null,
        public val enumValues: List<JsonElement> = emptyList(),
        public val defaultValue: JsonElement? = null,
        public val examples: List<JsonElement> = emptyList(),
        public val minimum: NumericBound? = null,
        public val maximum: NumericBound? = null,
        public val minLength: Long? = null,
        public val maxLength: Long? = null,
        public val pattern: String? = null,
        public val minItems: Long? = null,
        public val maxItems: Long? = null,
        public val uniqueItems: Boolean = false,
        public val readOnly: Boolean = false,
        public val writeOnly: Boolean = false,
        public val deprecated: Boolean = false,
    ) : Schema
}

public enum class SchemaType {
    STRING,
    NUMBER,
    INTEGER,
    BOOLEAN,
    ARRAY,
    OBJECT,
    NULL,
}

/**
 * 数值上下界，已归一化 3.0（`exclusiveMinimum: true` 修饰 `minimum`）与 3.1（`exclusiveMinimum` 本身是数值）两种写法。
 */
public data class NumericBound(
    public val value: BigDecimal,
    public val exclusive: Boolean,
)
