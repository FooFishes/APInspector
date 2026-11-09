package work.foofish.apinspector.openapi.parse

import work.foofish.apinspector.openapi.HttpMethod
import work.foofish.apinspector.openapi.Operation
import work.foofish.apinspector.openapi.OperationKey
import work.foofish.apinspector.openapi.Parameter
import work.foofish.apinspector.openapi.ParameterLocation
import work.foofish.apinspector.openapi.Resolvable
import work.foofish.apinspector.openapi.SecurityRequirement
import java.util.Locale

/** 读取 Paths Object 中的所有接口，保持文档中路径与方法的顺序。 */
internal class OperationReader(
    private val context: ParseContext,
    private val parameters: ParameterReader,
    private val content: ContentReader,
    private val security: SecurityReader,
) {
    fun operations(
        paths: ObjectNode?,
        globalSecurity: List<SecurityRequirement>,
    ): List<Operation> {
        if (paths == null) return emptyList()
        return paths.children(allowsExtensions = true).flatMap { (path, item) ->
            if (!path.startsWith(PATH_PREFIX)) {
                paths.warnInvalidValue(path, path)
                return@flatMap emptyList()
            }
            val pathItem = (context.resolve(item) { it } as? Resolvable.Resolved)?.value ?: return@flatMap emptyList()
            pathItemOperations(path, pathItem, globalSecurity)
        }
    }

    private fun pathItemOperations(
        path: String,
        pathItem: ObjectNode,
        globalSecurity: List<SecurityRequirement>,
    ): List<Operation> {
        val sharedParameters = parameters.parameters(pathItem)
        // 按文档顺序：固定的方法字段，以及 3.2 additionalOperations 中的自定义方法（展开在它出现的位置）。
        // Path Item 中的 summary、parameters 等其他字段不是接口。
        return pathItem.json.keys
            .flatMap { field ->
                when (field) {
                    Field.ADDITIONAL_OPERATIONS -> additionalOperations(pathItem)
                    else -> listOfNotNull(HttpMethod.fromPathItemField(field)?.let { method -> pathItem.obj(field)?.let { method to it } })
                }
            }.map { (method, node) -> operation(OperationKey(method, path), node, sharedParameters, globalSecurity) }
    }

    /** 键是发请求时原样使用的方法名；不能与固定字段的方法重复（如 `POST` 应写在 `post` 中）。 */
    private fun additionalOperations(pathItem: ObjectNode): List<Pair<HttpMethod, ObjectNode>> {
        val operations = pathItem.obj(Field.ADDITIONAL_OPERATIONS) ?: return emptyList()
        return operations.children().mapNotNull { (name, node) ->
            val method = HttpMethod.of(name)?.takeUnless { it.name.uppercase(Locale.ROOT) in FIXED_FIELD_METHOD_NAMES }
            if (method == null) operations.warnInvalidValue(name, name)
            method?.let { it to node }
        }
    }

    private fun operation(
        key: OperationKey,
        node: ObjectNode,
        sharedParameters: List<Resolvable<Parameter>>,
        globalSecurity: List<SecurityRequirement>,
    ): Operation =
        Operation(
            key = key,
            operationId = node.string(Field.OPERATION_ID),
            summary = node.string(Field.SUMMARY),
            description = node.string(Field.DESCRIPTION),
            tags = node.strings(Field.TAGS),
            deprecated = node.boolean(Field.DEPRECATED) == true,
            parameters = merge(sharedParameters, parameters.parameters(node)).filterNot(::isIgnoredHeader),
            requestBody = node.obj(Field.REQUEST_BODY)?.let(content::requestBody),
            responses = resolveAll(node.children(Field.RESPONSES, allowsExtensions = true), content::response),
            security = security.requirements(node) ?: globalSecurity,
        )

    /** Path Item 级参数在前；与 Operation 级参数同名同位置时被覆盖。 */
    private fun merge(
        shared: List<Resolvable<Parameter>>,
        own: List<Resolvable<Parameter>>,
    ): List<Resolvable<Parameter>> {
        val overridden = own.mapNotNullTo(HashSet()) { it.identity() }
        return shared.filterNot { it.identity() in overridden } + own
    }

    private fun Resolvable<Parameter>.identity(): Pair<String, ParameterLocation>? =
        (this as? Resolvable.Resolved)?.value?.let { it.name to it.location }

    private fun isIgnoredHeader(parameter: Resolvable<Parameter>): Boolean {
        val value = (parameter as? Resolvable.Resolved)?.value ?: return false
        return value.location == ParameterLocation.HEADER && value.name.lowercase(Locale.ROOT) in IGNORED_HEADER_PARAMETERS
    }

    private companion object {
        const val PATH_PREFIX = "/"
        val FIXED_FIELD_METHOD_NAMES: Set<String> = HttpMethod.PATH_ITEM_METHODS.mapTo(HashSet(), HttpMethod::name)
    }
}
