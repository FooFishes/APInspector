package work.foofish.apinspector.extraction

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import work.foofish.apinspector.core.coroutines.IoDispatcher
import work.foofish.apinspector.custom.CustomRequestRepository
import work.foofish.apinspector.environment.EnvironmentId
import work.foofish.apinspector.environment.EnvironmentRepository
import work.foofish.apinspector.history.ReceivedResponse
import work.foofish.apinspector.history.data.ResponseBodyStore
import work.foofish.apinspector.openapi.request.EnvironmentVariable
import work.foofish.apinspector.openapi.request.JsonPath
import work.foofish.apinspector.openapi.request.Variables
import work.foofish.apinspector.request.RequestRef
import java.io.IOException
import javax.inject.Inject

/**
 * 把值写入变量（PRD 5）：临时请求写入它自己的变量（ADR-0002），其余写入环境的变量。
 */
class VariableWriter
    @Inject
    constructor(
        private val environments: EnvironmentRepository,
        private val customs: CustomRequestRepository,
    ) {
        /**
         * @param environmentId 写入的环境；它已不存在（或发送时还是临时请求）则写入所属 API 文档的当前环境。[ref] 是临时请求时不用。
         * @return 是否已写入；请求已不存在，或 API 文档没有任何环境时为 `false`。
         */
        suspend fun write(
            ref: RequestRef,
            environmentId: EnvironmentId?,
            values: Map<String, String>,
        ): Boolean {
            if (values.isEmpty()) return true
            val specId =
                when (ref) {
                    is RequestRef.Operation -> {
                        ref.specId
                    }

                    is RequestRef.Custom -> {
                        // 归属以请求当前的状态为准：它可能已从临时请求移进了某份 API 文档。
                        val request = customs.find(ref.id) ?: return false
                        request.specId ?: run {
                            customs.update(request.copy(variables = request.variables.with(values)))
                            return true
                        }
                    }
                }
            val environment = environmentId?.let { environments.find(it) } ?: environments.current(specId).first() ?: return false
            environments.update(environment.copy(variables = environment.variables.with(values)))
            return true
        }
    }

/** 同名变量改值（保留候选值），没有的追加在最后。 */
private fun List<EnvironmentVariable>.with(values: Map<String, String>): List<EnvironmentVariable> {
    val existing = mapTo(HashSet()) { it.name }
    return map { variable -> values[variable.name]?.let { variable.copy(value = it) } ?: variable } +
        values.filterKeys { it !in existing }.map { (name, value) -> EnvironmentVariable(name, value) }
}

/**
 * 执行提取规则（PRD 5）：仅在 2xx 响应后，按 JSONPath 从 Body 取值写入变量。
 *
 * 写不对的规则、取不到值的规则跳过；Body 被截断时不是完整的 JSON，不提取。
 */
class ExtractionRunner
    @Inject
    constructor(
        private val rules: ExtractionRuleRepository,
        private val writer: VariableWriter,
        private val bodies: ResponseBodyStore,
        @IoDispatcher private val io: CoroutineDispatcher,
    ) {
        suspend fun run(
            ref: RequestRef,
            environmentId: EnvironmentId?,
            response: ReceivedResponse,
        ) {
            val body = response.body?.takeUnless { it.truncated } ?: return
            if (response.statusCode !in SUCCESS) return
            val usable =
                rules.rulesOf(ref).mapNotNull { rule ->
                    JsonPath.parse(rule.jsonPath)?.takeIf { Variables.isValidName(rule.variable) }?.let { it to rule.variable }
                }
            if (usable.isEmpty()) return
            val file = bodies.existing(body.fileName) ?: return
            val text =
                try {
                    withContext(io) { file.readText() }
                } catch (_: IOException) {
                    // Body 文件在读取前被清理：与"已清理"同样处理，不提取。
                    return
                }
            val values = usable.mapNotNull { (path, variable) -> path.extract(text)?.let { variable to it } }.toMap()
            writer.write(ref, environmentId, values)
        }

        private companion object {
            val SUCCESS = 200..299
        }
    }
