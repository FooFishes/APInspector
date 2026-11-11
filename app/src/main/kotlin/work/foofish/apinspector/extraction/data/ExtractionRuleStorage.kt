package work.foofish.apinspector.extraction.data

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import work.foofish.apinspector.custom.data.CustomRequestEntity
import work.foofish.apinspector.extraction.ExtractionRule
import work.foofish.apinspector.extraction.ExtractionRuleId
import work.foofish.apinspector.extraction.ExtractionRuleRepository
import work.foofish.apinspector.request.RequestRef
import work.foofish.apinspector.spec.data.SpecEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 提取规则（PRD 5）。挂在接口上时以 [specId]、[method]、[path] 标识接口，[customId] 为 `null`；
 * 挂在自定义请求上时只有 [customId]。所属的 API 文档或自定义请求删除时一并删除。
 */
@Serializable
@Entity(
    tableName = "extraction_rule",
    foreignKeys = [
        ForeignKey(entity = SpecEntity::class, parentColumns = ["id"], childColumns = ["spec_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(
            entity = CustomRequestEntity::class,
            parentColumns = ["id"],
            childColumns = ["custom_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("spec_id", "method", "path"), Index("custom_id")],
)
data class ExtractionRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "spec_id") val specId: String?,
    val method: String?,
    val path: String?,
    @ColumnInfo(name = "custom_id") val customId: String?,
    @ColumnInfo(name = "json_path") val jsonPath: String,
    val variable: String,
)

@Dao
interface ExtractionRuleDao {
    @Query("SELECT * FROM extraction_rule WHERE spec_id = :specId AND method = :method AND path = :path ORDER BY id")
    fun observeForOperation(
        specId: String,
        method: String,
        path: String,
    ): Flow<List<ExtractionRuleEntity>>

    @Query("SELECT * FROM extraction_rule WHERE custom_id = :customId ORDER BY id")
    fun observeForCustom(customId: String): Flow<List<ExtractionRuleEntity>>

    @Query("SELECT * FROM extraction_rule WHERE spec_id = :specId AND method = :method AND path = :path ORDER BY id")
    suspend fun forOperation(
        specId: String,
        method: String,
        path: String,
    ): List<ExtractionRuleEntity>

    @Query("SELECT * FROM extraction_rule WHERE custom_id = :customId ORDER BY id")
    suspend fun forCustom(customId: String): List<ExtractionRuleEntity>

    @Insert
    suspend fun insert(rule: ExtractionRuleEntity)

    @Query("UPDATE extraction_rule SET json_path = :jsonPath, variable = :variable WHERE id = :id")
    suspend fun update(
        id: Long,
        jsonPath: String,
        variable: String,
    )

    @Query("DELETE FROM extraction_rule WHERE id = :id")
    suspend fun delete(id: Long)
}

@Singleton
class DefaultExtractionRuleRepository
    @Inject
    constructor(
        private val dao: ExtractionRuleDao,
    ) : ExtractionRuleRepository {
        override fun rules(ref: RequestRef): Flow<List<ExtractionRule>> =
            when (ref) {
                is RequestRef.Operation -> dao.observeForOperation(ref.specId.value, ref.key.method.name, ref.key.path)
                is RequestRef.Custom -> dao.observeForCustom(ref.id.value)
            }.map { rules -> rules.map { it.toRule() } }

        override suspend fun rulesOf(ref: RequestRef): List<ExtractionRule> =
            when (ref) {
                is RequestRef.Operation -> dao.forOperation(ref.specId.value, ref.key.method.name, ref.key.path)
                is RequestRef.Custom -> dao.forCustom(ref.id.value)
            }.map { it.toRule() }

        override suspend fun add(
            ref: RequestRef,
            jsonPath: String,
            variable: String,
        ) {
            dao.insert(
                when (ref) {
                    is RequestRef.Operation -> {
                        ExtractionRuleEntity(
                            specId = ref.specId.value,
                            method = ref.key.method.name,
                            path = ref.key.path,
                            customId = null,
                            jsonPath = jsonPath,
                            variable = variable,
                        )
                    }

                    is RequestRef.Custom -> {
                        ExtractionRuleEntity(
                            specId = null,
                            method = null,
                            path = null,
                            customId = ref.id.value,
                            jsonPath = jsonPath,
                            variable = variable,
                        )
                    }
                },
            )
        }

        override suspend fun update(rule: ExtractionRule) {
            dao.update(rule.id.value, rule.jsonPath, rule.variable)
        }

        override suspend fun delete(id: ExtractionRuleId) {
            dao.delete(id.value)
        }
    }

private fun ExtractionRuleEntity.toRule(): ExtractionRule = ExtractionRule(ExtractionRuleId(id), jsonPath, variable)
