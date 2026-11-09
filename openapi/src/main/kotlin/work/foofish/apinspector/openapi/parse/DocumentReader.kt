package work.foofish.apinspector.openapi.parse

import kotlinx.serialization.json.JsonObject
import work.foofish.apinspector.openapi.Info
import work.foofish.apinspector.openapi.OpenApiDocument
import work.foofish.apinspector.openapi.OpenApiParseError
import work.foofish.apinspector.openapi.OpenApiParseResult
import work.foofish.apinspector.openapi.OpenApiVersion
import work.foofish.apinspector.openapi.Server
import work.foofish.apinspector.openapi.ServerVariable
import work.foofish.apinspector.openapi.Tag

/** 把已确认版本的文档根节点读成 [OpenApiDocument]。每个实例只用于一次解析。 */
internal class DocumentReader(
    root: JsonObject,
    private val version: OpenApiVersion,
    private val declaredVersion: String,
) {
    private val context = ParseContext(root, version)
    private val schemas = SchemaReader(context)
    private val content = ContentReader(context, schemas)
    private val security = SecurityReader(context)
    private val operations = OperationReader(context, ParameterReader(context, schemas, content), content, security)

    fun read(): OpenApiParseResult.Success {
        val document = context.document
        val info = info(document)
        val globalSecurity = security.requirements(document).orEmpty()
        val operations = operations.operations(document.obj(Field.PATHS), globalSecurity)
        val securitySchemes = security.schemes(document.obj(Field.COMPONENTS))
        // 必须最后读取：其余部分中出现的引用都要先入队。
        val schemaRegistry = schemas.registry()
        return OpenApiParseResult.Success(
            document =
                OpenApiDocument(
                    version = version,
                    declaredVersion = declaredVersion,
                    info = info,
                    servers = servers(document),
                    tags = tags(document),
                    operations = operations,
                    securitySchemes = securitySchemes,
                    schemas = schemaRegistry,
                ),
            warnings = context.warnings,
        )
    }

    private fun info(document: ObjectNode): Info {
        val info = document.obj(Field.INFO) ?: throw missing(document.pointer.child(Field.INFO))
        val title = info.string(Field.TITLE) ?: throw missing(info.pointer.child(Field.TITLE))
        val version = info.string(Field.VERSION)
        if (version == null) info.warnMissing(Field.VERSION)
        return Info(
            title = title,
            version = version,
            summary = info.string(Field.SUMMARY),
            description = info.string(Field.DESCRIPTION),
        )
    }

    /** 未声明或为空时，按规范默认为一个 URL 为 `/` 的服务器。 */
    private fun servers(document: ObjectNode): List<Server> =
        document
            .objects(Field.SERVERS)
            .mapNotNull(::server)
            .ifEmpty { listOf(Server(url = DEFAULT_SERVER_URL, name = null, description = null, variables = emptyMap())) }

    private fun server(node: ObjectNode): Server? {
        val url = node.string(Field.URL) ?: return null.also { node.warnMissing(Field.URL) }
        val variables =
            node.children(Field.VARIABLES).mapNotNull { (name, variable) -> serverVariable(variable)?.let { name to it } }.toMap()
        return Server(url, node.string(Field.NAME), node.string(Field.DESCRIPTION), variables)
    }

    private fun serverVariable(node: ObjectNode): ServerVariable? {
        val default = node.string(Field.DEFAULT) ?: return null.also { node.warnMissing(Field.DEFAULT) }
        return ServerVariable(default, node.strings(Field.ENUM), node.string(Field.DESCRIPTION))
    }

    private fun tags(document: ObjectNode): List<Tag> =
        document.objects(Field.TAGS).mapNotNull { node ->
            val name = node.string(Field.NAME) ?: return@mapNotNull null.also { node.warnMissing(Field.NAME) }
            Tag(
                name = name,
                summary = node.string(Field.SUMMARY),
                description = node.string(Field.DESCRIPTION),
                parent = node.string(Field.PARENT),
                kind = node.string(Field.KIND),
            )
        }

    private fun missing(pointer: JsonPointer): ParseFailure = ParseFailure(OpenApiParseError.MissingRequiredField(pointer.toFragment()))

    private companion object {
        const val DEFAULT_SERVER_URL = "/"
    }
}
