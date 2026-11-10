package work.foofish.apinspector.request

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import work.foofish.apinspector.openapi.OperationKey
import work.foofish.apinspector.openapi.request.RequestInput
import work.foofish.apinspector.spec.SpecId

/** 预设的标识。 */
@JvmInline
@Serializable
value class PresetId(
    val value: String,
)

/** 一个接口的命名预设（PRD 4.1）。 */
data class Preset(
    val id: PresetId,
    val name: String,
    val input: RequestInput,
)

/**
 * 接口的当前参数。
 *
 * @property presetId 当前参数加载自哪个预设；是否"已修改"由逐项比较得出，不单独保存。
 */
data class WorkingParams(
    val input: RequestInput,
    val presetId: PresetId?,
)

/** 当前参数与预设的存取（PRD 4.1）。 */
interface RequestInputRepository {
    /** 尚未填写过时为 `null`，此时使用文档默认值。 */
    fun working(
        specId: SpecId,
        operation: OperationKey,
    ): Flow<WorkingParams?>

    suspend fun saveWorking(
        specId: SpecId,
        operation: OperationKey,
        working: WorkingParams,
    )

    /** 按创建顺序。 */
    fun presets(
        specId: SpecId,
        operation: OperationKey,
    ): Flow<List<Preset>>

    /** 新建预设，并让当前参数指向它。 */
    suspend fun createPreset(
        specId: SpecId,
        operation: OperationKey,
        name: String,
        input: RequestInput,
    ): PresetId

    suspend fun updatePreset(
        id: PresetId,
        input: RequestInput,
    )

    suspend fun renamePreset(
        id: PresetId,
        name: String,
    )

    suspend fun deletePreset(id: PresetId)
}
