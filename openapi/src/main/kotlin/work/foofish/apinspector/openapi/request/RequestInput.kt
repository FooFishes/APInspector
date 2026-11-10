package work.foofish.apinspector.openapi.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import work.foofish.apinspector.openapi.ParameterLocation

/**
 * 一个接口的参数值：当前参数与预设都是它（PRD 4.1）。值中可以引用 `{{变量}}`。
 *
 * @property parameters 文档声明的参数，以"位置 + 名称"标识，顺序与文档一致。
 * @property extras 额外参数，按用户添加的顺序。
 * @property body 选中的 Body；`null` 表示不发送 Body。
 */
@Serializable
public data class RequestInput(
    val parameters: List<ParameterInput>,
    val extras: List<ExtraParameter>,
    val body: BodyInput?,
) {
    public fun parameter(
        location: ParameterLocation,
        name: String,
    ): ParameterInput? = parameters.firstOrNull { it.location == location && it.name == name }
}

/**
 * 一个声明的参数的值。
 *
 * @property enabled 是否勾选。必填参数总是发送，与此无关；按规范忽略的 Header 例外（见 [isIgnoredHeader]）。
 */
@Serializable
public data class ParameterInput(
    val location: ParameterLocation,
    val name: String,
    val value: ParameterValue,
    val enabled: Boolean,
)

/** 参数值的形态取决于参数的 schema 与序列化方式（见 [ParameterShape]）。 */
@Serializable
public sealed interface ParameterValue {
    @Serializable
    @SerialName("text")
    public data class Text(
        val text: String,
    ) : ParameterValue

    /** 数组参数：每个元素一项。 */
    @Serializable
    @SerialName("items")
    public data class Items(
        val items: List<String>,
    ) : ParameterValue

    /** 对象参数：每个属性一项。 */
    @Serializable
    @SerialName("fields")
    public data class Fields(
        val fields: List<Field>,
    ) : ParameterValue
}

/** 一个键值对。 */
@Serializable
public data class Field(
    val name: String,
    val value: String,
)

/** 额外参数：文档未声明的 Header 或 Query 参数。 */
@Serializable
public data class ExtraParameter(
    val location: ExtraLocation,
    val name: String,
    val value: String,
    val enabled: Boolean,
)

public enum class ExtraLocation {
    QUERY,
    HEADER,
}

/** 选中的 Body：编辑器的种类由 content type 决定（见 [BodyKind]）。 */
@Serializable
public sealed interface BodyInput {
    /** 文档中的 content type 键，可能带参数或通配符。 */
    public val contentType: String

    @Serializable
    @SerialName("json")
    public data class Json(
        override val contentType: String,
        val text: String,
    ) : BodyInput

    /** `application/x-www-form-urlencoded`：未编码的字段。 */
    @Serializable
    @SerialName("form")
    public data class Form(
        override val contentType: String,
        val fields: List<Field>,
    ) : BodyInput

    @Serializable
    @SerialName("multipart")
    public data class Multipart(
        override val contentType: String,
        val parts: List<MultipartPart>,
    ) : BodyInput

    /** 以一个文件作为 Body；[file] 为 `null` 表示尚未选择。 */
    @Serializable
    @SerialName("binary")
    public data class Binary(
        override val contentType: String,
        val file: FileRef?,
    ) : BodyInput

    @Serializable
    @SerialName("text")
    public data class Text(
        override val contentType: String,
        val text: String,
    ) : BodyInput
}

@Serializable
public data class MultipartPart(
    val name: String,
    val value: PartValue,
)

@Serializable
public sealed interface PartValue {
    @Serializable
    @SerialName("text")
    public data class Text(
        val text: String,
    ) : PartValue

    /** [file] 为 `null` 表示尚未选择。 */
    @Serializable
    @SerialName("file")
    public data class File(
        val file: FileRef?,
    ) : PartValue
}

/**
 * 对本机文件的引用：只保存引用，不复制内容（PRD 4.3）。
 *
 * @property uri 系统文件选择器返回的 content URI，已申请持久读取授权。
 * @property size 选择时的大小（字节）；未知时为 `null`。
 */
@Serializable
public data class FileRef(
    val uri: String,
    val name: String,
    val size: Long?,
    val mediaType: String?,
)
