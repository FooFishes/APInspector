package work.foofish.apinspector.spec

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map

/**
 * 持续提供某份 API 文档的已解析内容：导入或刷新后（更新时间变化）重新加载，被删除后发出 [DocumentLoad.Missing]。
 * 置顶等不影响内容的变更不会触发重新加载。
 */
fun SpecRepository.observeDocument(id: SpecId): Flow<DocumentLoad> =
    spec(id)
        .distinctUntilChangedBy { it?.updatedAt }
        .map { spec -> if (spec == null) DocumentLoad.Missing else document(id) }
