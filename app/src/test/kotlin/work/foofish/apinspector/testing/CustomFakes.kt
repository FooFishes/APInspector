package work.foofish.apinspector.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import work.foofish.apinspector.custom.CustomRequest
import work.foofish.apinspector.custom.CustomRequestDraft
import work.foofish.apinspector.custom.CustomRequestId
import work.foofish.apinspector.custom.CustomRequestRepository
import work.foofish.apinspector.extraction.ExtractionRule
import work.foofish.apinspector.extraction.ExtractionRuleId
import work.foofish.apinspector.extraction.ExtractionRuleRepository
import work.foofish.apinspector.request.RequestRef
import work.foofish.apinspector.request.isSameRequest
import work.foofish.apinspector.spec.SpecId

/** 内存中的自定义请求；标识按创建顺序为 `custom-0`、`custom-1`…… */
class FakeCustomRequestRepository : CustomRequestRepository {
    private val all = MutableStateFlow(emptyList<CustomRequest>())
    private var created = 0

    override fun requests(specId: SpecId?): Flow<List<CustomRequest>> = all.map { list -> list.filter { it.specId == specId } }

    override fun request(id: CustomRequestId): Flow<CustomRequest?> = all.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun find(id: CustomRequestId): CustomRequest? = all.value.firstOrNull { it.id == id }

    override suspend fun create(
        specId: SpecId?,
        draft: CustomRequestDraft,
    ): CustomRequestId {
        val id = CustomRequestId("custom-${created++}")
        all.update { it + CustomRequest(id, specId, draft.name, draft.method, draft.url, draft.input, variables = emptyList()) }
        return id
    }

    override suspend fun update(request: CustomRequest) {
        all.update { list -> list.map { if (it.id == request.id) request else it } }
    }

    override suspend fun move(
        id: CustomRequestId,
        specId: SpecId,
        url: String,
    ) {
        all.update { list -> list.map { if (it.id == id) it.copy(specId = specId, url = url, variables = emptyList()) else it } }
    }

    override suspend fun delete(id: CustomRequestId) {
        all.update { list -> list.filterNot { it.id == id } }
    }
}

/** 内存中的提取规则。 */
class FakeExtractionRuleRepository : ExtractionRuleRepository {
    private val all = MutableStateFlow(emptyList<Pair<RequestRef, ExtractionRule>>())
    private var created = 0L

    override fun rules(ref: RequestRef): Flow<List<ExtractionRule>> = all.map { it.of(ref) }

    override suspend fun rulesOf(ref: RequestRef): List<ExtractionRule> = all.value.of(ref)

    override suspend fun add(
        ref: RequestRef,
        jsonPath: String,
        variable: String,
    ) {
        all.update { it + (ref to ExtractionRule(ExtractionRuleId(++created), jsonPath, variable)) }
    }

    override suspend fun update(rule: ExtractionRule) {
        all.update { list -> list.map { (ref, old) -> ref to if (old.id == rule.id) rule else old } }
    }

    override suspend fun delete(id: ExtractionRuleId) {
        all.update { list -> list.filterNot { it.second.id == id } }
    }

    private fun List<Pair<RequestRef, ExtractionRule>>.of(ref: RequestRef): List<ExtractionRule> =
        filter { it.first.isSameRequest(ref) }.map { it.second }
}
