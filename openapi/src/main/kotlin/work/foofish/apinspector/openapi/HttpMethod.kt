package work.foofish.apinspector.openapi

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * HTTP 方法。方法名区分大小写（RFC 9110 §9.1），按发出请求时的原样保存。
 *
 * 除标准方法外，OpenAPI 3.2 允许通过 `additionalOperations` 声明任意方法（如 `LINK`），因此这里不是枚举。
 */
@JvmInline
@Serializable(with = HttpMethodSerializer::class)
public value class HttpMethod private constructor(
    public val name: String,
) {
    override fun toString(): String = name

    public companion object {
        public val GET: HttpMethod = HttpMethod("GET")
        public val PUT: HttpMethod = HttpMethod("PUT")
        public val POST: HttpMethod = HttpMethod("POST")
        public val DELETE: HttpMethod = HttpMethod("DELETE")
        public val OPTIONS: HttpMethod = HttpMethod("OPTIONS")
        public val HEAD: HttpMethod = HttpMethod("HEAD")
        public val PATCH: HttpMethod = HttpMethod("PATCH")
        public val TRACE: HttpMethod = HttpMethod("TRACE")

        /** OpenAPI 3.2 新增的 QUERY 方法（带请求体的安全查询）。 */
        public val QUERY: HttpMethod = HttpMethod("QUERY")

        /** 可以用 Path Item Object 固定字段声明的方法，按规范中的字段顺序。 */
        public val PATH_ITEM_METHODS: List<HttpMethod> = listOf(GET, PUT, POST, DELETE, OPTIONS, HEAD, PATCH, TRACE, QUERY)

        private val byPathItemField: Map<String, HttpMethod> = PATH_ITEM_METHODS.associateBy { it.name.lowercase() }

        /** RFC 9110 §5.6.2 的 token。 */
        private val TOKEN = Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+")

        /**
         * 按 Path Item Object 的固定字段名查找 HTTP 方法。
         *
         * 字段名区分大小写；`parameters`、`summary` 等非方法字段返回 `null`。
         */
        public fun fromPathItemField(field: String): HttpMethod? = byPathItemField[field]

        /** 按方法名创建，原样保留大小写；不是合法的 HTTP token 时返回 `null`。 */
        public fun of(name: String): HttpMethod? = name.takeIf(TOKEN::matches)?.let(::HttpMethod)
    }
}

/** 以方法名序列化；反序列化时校验是否为合法的 HTTP token。 */
internal object HttpMethodSerializer : KSerializer<HttpMethod> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("work.foofish.apinspector.openapi.HttpMethod", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: HttpMethod,
    ) {
        encoder.encodeString(value.name)
    }

    override fun deserialize(decoder: Decoder): HttpMethod {
        val name = decoder.decodeString()
        return HttpMethod.of(name) ?: throw SerializationException("非法的 HTTP 方法：$name")
    }
}
