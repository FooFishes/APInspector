package work.foofish.apinspector.extraction

import kotlinx.coroutines.flow.Flow
import work.foofish.apinspector.request.RequestRef

/** 提取规则的标识。 */
@JvmInline
value class ExtractionRuleId(
    val value: Long,
)

/**
 * 提取规则（PRD 5）：2xx 响应后按 [jsonPath] 从 Body 取值，写入变量 [variable]。
 *
 * 挂在接口上的规则对它的当前参数与所有预设生效，写入发送时所用的环境；挂在临时请求上的规则写入它自己的变量。
 */
data class ExtractionRule(
    val id: ExtractionRuleId,
    val jsonPath: String,
    val variable: String,
)

/** 提取规则的存取。 */
interface ExtractionRuleRepository {
    /** 按添加顺序。 */
    fun rules(ref: RequestRef): Flow<List<ExtractionRule>>

    suspend fun rulesOf(ref: RequestRef): List<ExtractionRule>

    suspend fun add(
        ref: RequestRef,
        jsonPath: String,
        variable: String,
    )

    suspend fun update(rule: ExtractionRule)

    suspend fun delete(id: ExtractionRuleId)
}
