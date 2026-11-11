package work.foofish.apinspector.custom.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import work.foofish.apinspector.core.db.StorageJson
import work.foofish.apinspector.custom.CustomRequest
import work.foofish.apinspector.custom.CustomRequestDraft
import work.foofish.apinspector.custom.CustomRequestId
import work.foofish.apinspector.custom.CustomRequestRepository
import work.foofish.apinspector.history.data.ResponseBodyStore
import work.foofish.apinspector.openapi.HttpMethod
import work.foofish.apinspector.openapi.request.EnvironmentVariable
import work.foofish.apinspector.request.data.decodeInput
import work.foofish.apinspector.request.data.encodeInput
import work.foofish.apinspector.spec.SpecId
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultCustomRequestRepository
    @Inject
    constructor(
        private val dao: CustomRequestDao,
        private val bodies: ResponseBodyStore,
        private val clock: Clock,
    ) : CustomRequestRepository {
        override fun requests(specId: SpecId?): Flow<List<CustomRequest>> =
            dao.observeAll(specId?.value).map { list -> list.mapNotNull { it.toRequest() } }

        override fun request(id: CustomRequestId): Flow<CustomRequest?> = dao.observe(id.value).map { it?.toRequest() }

        override suspend fun find(id: CustomRequestId): CustomRequest? = dao.find(id.value)?.toRequest()

        override suspend fun create(
            specId: SpecId?,
            draft: CustomRequestDraft,
        ): CustomRequestId {
            val id = CustomRequestId(UUID.randomUUID().toString())
            dao.insert(
                CustomRequestEntity(
                    id = id.value,
                    specId = specId?.value,
                    name = draft.name,
                    method = draft.method.name,
                    url = draft.url,
                    input = encodeInput(draft.input),
                    variables = NO_VARIABLES,
                    createdAt = clock.millis(),
                ),
            )
            return id
        }

        override suspend fun update(request: CustomRequest) {
            dao.update(
                id = request.id.value,
                name = request.name,
                method = request.method.name,
                url = request.url,
                input = encodeInput(request.input),
                variables = StorageJson.encodeToString(VariablesSerializer, request.variables),
            )
        }

        override suspend fun move(
            id: CustomRequestId,
            specId: SpecId,
            url: String,
        ) {
            dao.move(id.value, specId.value, url, NO_VARIABLES)
        }

        /** 请求记录随外键级联删除；它们的 Body 文件在这里清理。 */
        override suspend fun delete(id: CustomRequestId) {
            val files = dao.bodyFiles(id.value)
            dao.delete(id.value)
            bodies.delete(files)
        }

        private companion object {
            const val NO_VARIABLES = "[]"
        }
    }

private val VariablesSerializer = ListSerializer(EnvironmentVariable.serializer())

/** 方法名不合法（只可能来自损坏的数据）时忽略该条。 */
private fun CustomRequestEntity.toRequest(): CustomRequest? =
    HttpMethod.of(method)?.let { method ->
        CustomRequest(
            id = CustomRequestId(id),
            specId = specId?.let(::SpecId),
            name = name,
            method = method,
            url = url,
            input = decodeInput(input),
            variables = StorageJson.decodeFromString(VariablesSerializer, variables),
        )
    }
