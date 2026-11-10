package work.foofish.apinspector.request.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import work.foofish.apinspector.core.db.StorageJson
import work.foofish.apinspector.openapi.OperationKey
import work.foofish.apinspector.openapi.request.RequestInput
import work.foofish.apinspector.request.Preset
import work.foofish.apinspector.request.PresetId
import work.foofish.apinspector.request.RequestInputRepository
import work.foofish.apinspector.request.WorkingParams
import work.foofish.apinspector.spec.SpecId
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultRequestInputRepository
    @Inject
    constructor(
        private val dao: RequestInputDao,
        private val clock: Clock,
    ) : RequestInputRepository {
        override fun working(
            specId: SpecId,
            operation: OperationKey,
        ): Flow<WorkingParams?> =
            dao.observeWorking(specId.value, operation.method.name, operation.path).map { entity ->
                entity?.let { WorkingParams(decodeInput(it.input), it.presetId?.let(::PresetId)) }
            }

        override suspend fun saveWorking(
            specId: SpecId,
            operation: OperationKey,
            working: WorkingParams,
        ) {
            dao.upsertWorking(
                WorkingParamsEntity(
                    specId.value,
                    operation.method.name,
                    operation.path,
                    encodeInput(working.input),
                    working.presetId?.value,
                ),
            )
        }

        override fun presets(
            specId: SpecId,
            operation: OperationKey,
        ): Flow<List<Preset>> =
            dao.observePresets(specId.value, operation.method.name, operation.path).map { presets ->
                presets.map { Preset(PresetId(it.id), it.name, decodeInput(it.input)) }
            }

        override suspend fun createPreset(
            specId: SpecId,
            operation: OperationKey,
            name: String,
            input: RequestInput,
        ): PresetId {
            val id = PresetId(UUID.randomUUID().toString())
            dao.insertPreset(
                PresetEntity(id.value, specId.value, operation.method.name, operation.path, name, encodeInput(input), clock.millis()),
            )
            saveWorking(specId, operation, WorkingParams(input, id))
            return id
        }

        override suspend fun updatePreset(
            id: PresetId,
            input: RequestInput,
        ) {
            dao.updatePresetInput(id.value, encodeInput(input))
        }

        override suspend fun renamePreset(
            id: PresetId,
            name: String,
        ) {
            dao.renamePreset(id.value, name)
        }

        override suspend fun deletePreset(id: PresetId) {
            dao.deletePreset(id.value)
        }
    }

internal fun encodeInput(input: RequestInput): String = StorageJson.encodeToString(RequestInput.serializer(), input)

internal fun decodeInput(json: String): RequestInput = StorageJson.decodeFromString(RequestInput.serializer(), json)
