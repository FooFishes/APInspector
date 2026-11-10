package work.foofish.apinspector.request

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import work.foofish.apinspector.core.coroutines.ApplicationScope
import work.foofish.apinspector.environment.Environment
import work.foofish.apinspector.environment.EnvironmentId
import work.foofish.apinspector.environment.EnvironmentRepository
import work.foofish.apinspector.history.RequestSource
import work.foofish.apinspector.navigation.OperationPageKey
import work.foofish.apinspector.network.FileAccess
import work.foofish.apinspector.network.RequestSender
import work.foofish.apinspector.network.SendJob
import work.foofish.apinspector.network.SendTarget
import work.foofish.apinspector.network.UserAgent
import work.foofish.apinspector.openapi.OpenApiDocument
import work.foofish.apinspector.openapi.Operation
import work.foofish.apinspector.openapi.OperationKey
import work.foofish.apinspector.openapi.ParameterLocation
import work.foofish.apinspector.openapi.Schema
import work.foofish.apinspector.openapi.request.Assembly
import work.foofish.apinspector.openapi.request.BodyInput
import work.foofish.apinspector.openapi.request.ExtraParameter
import work.foofish.apinspector.openapi.request.FileRef
import work.foofish.apinspector.openapi.request.HttpBody
import work.foofish.apinspector.openapi.request.HttpPart
import work.foofish.apinspector.openapi.request.JsonNode
import work.foofish.apinspector.openapi.request.JsonTemplate
import work.foofish.apinspector.openapi.request.JsonTemplateParse
import work.foofish.apinspector.openapi.request.MultipartPart
import work.foofish.apinspector.openapi.request.ParameterValue
import work.foofish.apinspector.openapi.request.PartValue
import work.foofish.apinspector.openapi.request.RequestAssembler
import work.foofish.apinspector.openapi.request.RequestInput
import work.foofish.apinspector.openapi.request.alignedTo
import work.foofish.apinspector.openapi.request.bodyContents
import work.foofish.apinspector.openapi.request.defaultBody
import work.foofish.apinspector.openapi.request.defaultInput
import work.foofish.apinspector.request.body.JsonPath
import work.foofish.apinspector.request.body.JsonStep
import work.foofish.apinspector.request.body.append
import work.foofish.apinspector.request.body.remove
import work.foofish.apinspector.request.body.replace
import work.foofish.apinspector.request.body.sampleNode
import work.foofish.apinspector.request.body.schemaAt
import work.foofish.apinspector.request.body.variantSample
import work.foofish.apinspector.spec.DocumentLoad
import work.foofish.apinspector.spec.ImportFailure
import work.foofish.apinspector.spec.SpecRepository
import work.foofish.apinspector.spec.observeDocument

sealed interface RequestUiState {
    data object Loading : RequestUiState

    /** API 文档已被删除，或刷新后该接口已不存在。 */
    data object Missing : RequestUiState

    data class Failed(
        val failure: ImportFailure,
    ) : RequestUiState

    /**
     * @property problems 阻止发送的原因；不为 `null` 时显示对话框。
     * @property sent 刚刚开始发送，界面切到"响应" Tab 后调用 [RequestViewModel.onSentShown]。
     */
    data class Loaded(
        val environments: ImmutableList<EnvironmentOption>,
        val environment: EnvironmentOption?,
        val variables: ImmutableList<String>,
        val presets: PresetState,
        val groups: ImmutableList<ParameterGroup>,
        val extras: ImmutableList<ExtraParameter>,
        val body: BodyEditor?,
        val sending: Boolean,
        val problems: ImmutableList<SendProblem>?,
        val sent: Boolean,
    ) : RequestUiState
}

/** 文件选择的目标：整个 Body，或 multipart 的第 [index] 个字段。 */
sealed interface FileTarget {
    data object Body : FileTarget

    data class Part(
        val index: Int,
    ) : FileTarget
}

/**
 * "请求" Tab（PRD 3、4）：编辑当前参数（即时保存）、选择环境与预设、发送。
 */
@HiltViewModel(assistedFactory = RequestViewModel.Factory::class)
class RequestViewModel
    @AssistedInject
    constructor(
        @Assisted key: OperationPageKey,
        specs: SpecRepository,
        private val environments: EnvironmentRepository,
        private val inputs: RequestInputRepository,
        private val executor: RequestSender,
        private val files: FileAccess,
        @UserAgent private val userAgent: String,
        @ApplicationScope private val applicationScope: CoroutineScope,
    ) : ViewModel(),
        RequestEditorActions {
        @AssistedFactory
        interface Factory {
            fun create(key: OperationPageKey): RequestViewModel
        }

        private val specId = key.specId
        private val operationKey = key.operation
        private val target = SendTarget(specId, operationKey)

        private val context: StateFlow<EditContext?> =
            specs
                .observeDocument(specId)
                .map { load -> EditContext.of(load, operationKey) }
                .stateIn(viewModelScope, SharingStarted.Eagerly, null)

        private val currentEnvironment: StateFlow<Environment?> =
            environments.current(specId).stateIn(viewModelScope, SharingStarted.Eagerly, null)

        private val presets = inputs.presets(specId, operationKey).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

        /** 本地的当前参数：编辑立即生效，随后在应用作用域中保存。 */
        private val working = MutableStateFlow<WorkingState>(WorkingState.Unknown)
        private val view = MutableStateFlow(ViewState())

        private val saving = Mutex()
        private var pending: WorkingParams? = null
        private var persisted: WorkingParams? = null

        /** 自己写入过的值：仓库回传它们时不必采用，以免覆盖之后的编辑。 */
        private val written = LinkedHashSet<WorkingParams>()

        init {
            viewModelScope.launch {
                inputs.working(specId, operationKey).collect { stored ->
                    val ours = synchronized(written) { stored in written }
                    when {
                        stored == null -> working.update { if (it is WorkingState.Unknown) WorkingState.Known(null) else it }
                        !ours -> working.value = WorkingState.Known(stored)
                    }
                }
            }
            viewModelScope.launch {
                context.collect { context ->
                    if (context is EditContext.Ready) environments.ensureEnvironments(specId, context.document)
                }
            }
        }

        private val editorInputs =
            combine(context, environments.environments(specId), currentEnvironment, presets) { context, all, current, presets ->
                EditorInputs(context, all, current, presets)
            }

        val uiState: StateFlow<RequestUiState> =
            combine(
                editorInputs,
                working,
                view,
                executor.inFlight.map { target in it }.distinctUntilChanged(),
            ) { inputs, working, view, sending ->
                when (val context = inputs.context) {
                    null -> {
                        RequestUiState.Loading
                    }

                    EditContext.Missing -> {
                        RequestUiState.Missing
                    }

                    is EditContext.Failed -> {
                        RequestUiState.Failed(context.failure)
                    }

                    is EditContext.Ready -> {
                        if (working is WorkingState.Unknown) {
                            RequestUiState.Loading
                        } else {
                            loaded(
                                context,
                                inputs,
                                working,
                                view,
                                sending,
                            )
                        }
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), RequestUiState.Loading)

        private fun loaded(
            context: EditContext.Ready,
            inputs: EditorInputs,
            state: WorkingState,
            view: ViewState,
            sending: Boolean,
        ): RequestUiState.Loaded {
            val params = aligned(context, (state as? WorkingState.Known)?.params)
            val builder = RequestEditorBuilder(context.document, context.operation)
            val preset = inputs.presets.firstOrNull { it.id == params.presetId }
            return RequestUiState.Loaded(
                environments = inputs.environments.map(Environment::toOption).toImmutableList(),
                environment = inputs.current?.toOption(),
                variables =
                    inputs.current
                        ?.variables
                        .orEmpty()
                        .map { it.name }
                        .filter { it.isNotEmpty() }
                        .toImmutableList(),
                presets =
                    PresetState(
                        presets = inputs.presets.map { PresetOption(it.id, it.name) }.toImmutableList(),
                        current = preset?.let { PresetOption(it.id, it.name) },
                        modified = preset != null && preset.input.alignedTo(context.operation, context.document) != params.input,
                    ),
                groups = builder.groups(params.input),
                extras = params.input.extras.toImmutableList(),
                body = builder.body(params.input, view.mode, view.collapsed),
                sending = sending,
                problems = view.problems,
                sent = view.sent,
            )
        }

        override fun selectEnvironment(id: EnvironmentId) {
            viewModelScope.launch { environments.select(specId, id) }
        }

        override fun setParameterValue(
            location: ParameterLocation,
            name: String,
            value: ParameterValue,
        ) {
            edit { input ->
                input.copy(
                    parameters =
                        input.parameters.map {
                            if (it.location == location &&
                                it.name == name
                            ) {
                                it.copy(value = value)
                            } else {
                                it
                            }
                        },
                )
            }
        }

        override fun setParameterEnabled(
            location: ParameterLocation,
            name: String,
            enabled: Boolean,
        ) {
            edit { input ->
                input.copy(
                    parameters =
                        input.parameters.map {
                            if (it.location == location &&
                                it.name == name
                            ) {
                                it.copy(enabled = enabled)
                            } else {
                                it
                            }
                        },
                )
            }
        }

        override fun addExtra(extra: ExtraParameter) {
            edit { it.copy(extras = it.extras + extra) }
        }

        override fun updateExtra(
            index: Int,
            extra: ExtraParameter,
        ) {
            edit { input -> input.copy(extras = input.extras.mapIndexed { i, old -> if (i == index) extra else old }) }
        }

        override fun removeExtra(index: Int) {
            edit { input -> input.copy(extras = input.extras.filterIndexed { i, _ -> i != index }) }
        }

        /** 切换 content type（`null` 为"无 Body"）：新类型取文档默认值，原 Body 丢弃（PRD 4.3）。 */
        override fun selectContentType(contentType: String?) {
            val context = context.value as? EditContext.Ready ?: return
            edit { input ->
                input.copy(body = contentType?.let { defaultBody(it, context.operation.bodyContents[it], context.document) })
            }
        }

        override fun setBody(body: BodyInput) {
            edit { input -> input.copy(body = body) }
        }

        override fun setBodyMode(mode: BodyMode) {
            view.update { it.copy(mode = mode) }
        }

        /** 格式化 JSON 原文；有语法错误时不变。 */
        override fun formatJson() {
            updateJson { it }
        }

        override fun toggleJsonNode(path: JsonPath) {
            view.update { it.copy(collapsed = if (path in it.collapsed) it.collapsed - path else it.collapsed + path) }
        }

        override fun setJsonNode(
            path: JsonPath,
            node: JsonNode,
        ) {
            updateJson { it.replace(path, node) }
        }

        override fun removeJsonNode(path: JsonPath) {
            updateJson { it.remove(path) }
        }

        /** 添加字段：[name] 为 schema 中声明的字段时按其 schema 生成值，否则为空字符串。 */
        override fun addJsonMember(
            parent: JsonPath,
            name: String,
        ) {
            val context = context.value as? EditContext.Ready ?: return
            updateJson { root ->
                val schema = schemaAt(root, jsonSchema(context), parent.child(JsonStep.Key(name)), context.document)
                root.append(parent, name, if (schema == null) JsonNode.StringNode("") else sampleNode(schema, context.document))
            }
        }

        /** 在数组末尾添加按元素 schema 生成的值。 */
        override fun addJsonItem(parent: JsonPath) {
            val context = context.value as? EditContext.Ready ?: return
            updateJson { root ->
                val items = schemaAt(root, jsonSchema(context), parent.child(JsonStep.Index(Int.MAX_VALUE)), context.document)
                root.append(parent, name = null, value = sampleNode(items, context.document))
            }
        }

        /** 把节点替换为第 [index] 个 `oneOf` / `anyOf` 变体的示例。 */
        override fun selectJsonVariant(
            path: JsonPath,
            index: Int,
        ) {
            val context = context.value as? EditContext.Ready ?: return
            updateJson { root ->
                variantSample(
                    schemaAt(root, jsonSchema(context), path, context.document),
                    index,
                    context.document,
                )?.let { root.replace(path, it) }
                    ?: root
            }
        }

        override fun pickFile(
            target: FileTarget,
            uri: Uri,
        ) {
            viewModelScope.launch {
                val file = files.adopt(uri)
                if (file == null) {
                    view.update {
                        it.copy(
                            problems = listOf<SendProblem>(SendProblem.FileUnavailable(uri.lastPathSegment.orEmpty())).toImmutableList(),
                        )
                    }
                    return@launch
                }
                edit { input -> input.copy(body = input.body?.withFile(target, file)) }
            }
        }

        override fun loadPreset(id: PresetId) {
            val preset = presets.value.firstOrNull { it.id == id } ?: return
            val context = context.value as? EditContext.Ready ?: return
            replaceWorking(WorkingParams(preset.input.alignedTo(context.operation, context.document), id))
        }

        /** 用当前参数覆盖当前预设。 */
        override fun saveToPreset() {
            val context = context.value as? EditContext.Ready ?: return
            val params = aligned(context, (working.value as? WorkingState.Known)?.params)
            val id = params.presetId ?: return
            viewModelScope.launch { inputs.updatePreset(id, params.input) }
        }

        override fun saveAsPreset(name: String) {
            val context = context.value as? EditContext.Ready ?: return
            val params = aligned(context, (working.value as? WorkingState.Known)?.params)
            applicationScope.launch {
                val id = inputs.createPreset(specId, operationKey, name, params.input)
                val linked = WorkingParams(params.input, id)
                remember(linked)
                working.value = WorkingState.Known(linked)
            }
        }

        override fun renamePreset(
            id: PresetId,
            name: String,
        ) {
            viewModelScope.launch { inputs.renamePreset(id, name) }
        }

        override fun deletePreset(id: PresetId) {
            viewModelScope.launch { inputs.deletePreset(id) }
        }

        /** 重置为文档默认值（PRD 4.1）；仍关联原预设，因此会显示为"已修改"。 */
        override fun resetToDefaults() {
            val context = context.value as? EditContext.Ready ?: return
            val presetId = (working.value as? WorkingState.Known)?.params?.presetId
            replaceWorking(WorkingParams(context.operation.defaultInput(context.document), presetId))
        }

        override fun send() {
            val context = context.value as? EditContext.Ready ?: return
            val environment = currentEnvironment.value ?: return showProblems(listOf(SendProblem.NoEnvironment))
            val params = aligned(context, (working.value as? WorkingState.Known)?.params)
            when (
                val assembly =
                    RequestAssembler(
                        context.document,
                        userAgent,
                    ).assemble(context.operation, params.input, environment.toRequestEnvironment())
            ) {
                is Assembly.Blocked -> {
                    showProblems(assembly.problems.map(SendProblem::Assembly))
                }

                is Assembly.Ready -> {
                    viewModelScope.launch {
                        val unavailable =
                            assembly.request.body
                                .files()
                                .filterNot { files.isReadable(it) }
                        if (unavailable.isNotEmpty()) return@launch showProblems(unavailable.map { SendProblem.FileUnavailable(it.name) })
                        val preset = presets.value.firstOrNull { it.id == params.presetId }
                        val fromPreset = preset != null && preset.input.alignedTo(context.operation, context.document) == params.input
                        val job =
                            SendJob(
                                target = target,
                                environment = environment,
                                request = assembly.request,
                                cookies = assembly.cookies,
                                loadCookieJar = true,
                                input = params.input,
                                source = if (fromPreset) RequestSource.PRESET else RequestSource.OPERATION,
                                presetName = preset?.name?.takeIf { fromPreset },
                            )
                        if (executor.send(job)) view.update { it.copy(sent = true) }
                    }
                }
            }
        }

        override fun cancel() {
            executor.cancel(target)
        }

        override fun dismissProblems() {
            view.update { it.copy(problems = null) }
        }

        fun onSentShown() {
            view.update { it.copy(sent = false) }
        }

        private fun showProblems(problems: List<SendProblem>) {
            view.update { it.copy(problems = problems.toImmutableList()) }
        }

        private fun updateJson(transform: (JsonNode) -> JsonNode) {
            edit { input ->
                val body = input.body as? BodyInput.Json ?: return@edit input
                val parsed = JsonTemplate.parse(body.text) as? JsonTemplateParse.Success ?: return@edit input
                input.copy(body = body.copy(text = JsonTemplate.format(transform(parsed.node))))
            }
        }

        private fun jsonSchema(context: EditContext.Ready): Schema? {
            val body = currentInput(context).body ?: return null
            return context.operation.bodyContents[body.contentType]?.schema
        }

        private fun currentInput(context: EditContext.Ready): RequestInput =
            aligned(context, (working.value as? WorkingState.Known)?.params).input

        /** 以与接口对齐后的当前参数为基础；尚未填写过时取文档默认值。 */
        private fun aligned(
            context: EditContext.Ready,
            params: WorkingParams?,
        ): WorkingParams =
            WorkingParams(
                input = params?.input?.alignedTo(context.operation, context.document) ?: context.operation.defaultInput(context.document),
                presetId = params?.presetId,
            )

        private fun edit(transform: (RequestInput) -> RequestInput) {
            val context = context.value as? EditContext.Ready ?: return
            val current = aligned(context, (working.value as? WorkingState.Known)?.params)
            val next = current.copy(input = transform(current.input))
            if (next != current) replaceWorking(next)
        }

        private fun replaceWorking(params: WorkingParams) {
            working.value = WorkingState.Known(params)
            persist(params)
        }

        /** 只保存最新的值；在应用作用域中进行，离开页面也会完成。 */
        private fun persist(params: WorkingParams) {
            pending = params
            remember(params)
            applicationScope.launch {
                saving.withLock {
                    val next = pending ?: return@withLock
                    if (next != persisted) {
                        inputs.saveWorking(specId, operationKey, next)
                        persisted = next
                    }
                }
            }
        }

        private fun remember(params: WorkingParams) {
            synchronized(written) {
                written += params
                if (written.size > REMEMBERED_WRITES) written.remove(written.first())
            }
        }

        private sealed interface WorkingState {
            data object Unknown : WorkingState

            /** [params] 为 `null` 表示尚未填写过。 */
            data class Known(
                val params: WorkingParams?,
            ) : WorkingState
        }

        private data class ViewState(
            val mode: BodyMode = BodyMode.VISUAL,
            val collapsed: Set<JsonPath> = emptySet(),
            val problems: ImmutableList<SendProblem>? = null,
            val sent: Boolean = false,
        )

        private data class EditorInputs(
            val context: EditContext?,
            val environments: List<Environment>,
            val current: Environment?,
            val presets: List<Preset>,
        )

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
            const val REMEMBERED_WRITES = 32
        }
    }

/** 编辑所需的文档与接口。 */
private sealed interface EditContext {
    data object Missing : EditContext

    data class Failed(
        val failure: ImportFailure,
    ) : EditContext

    data class Ready(
        val document: OpenApiDocument,
        val operation: Operation,
    ) : EditContext

    companion object {
        fun of(
            load: DocumentLoad,
            key: OperationKey,
        ): EditContext =
            when (load) {
                DocumentLoad.Missing -> {
                    Missing
                }

                is DocumentLoad.Failed -> {
                    Failed(load.failure)
                }

                is DocumentLoad.Loaded -> {
                    load.content.document
                        .operations
                        .firstOrNull { it.key == key }
                        ?.let { Ready(load.content.document, it) } ?: Missing
                }
            }
    }
}

private fun BodyInput.withFile(
    target: FileTarget,
    file: FileRef,
): BodyInput =
    when {
        this is BodyInput.Binary && target == FileTarget.Body -> {
            copy(file = file)
        }

        this is BodyInput.Multipart && target is FileTarget.Part -> {
            copy(
                parts =
                    parts.mapIndexed { index, part ->
                        if (index ==
                            target.index
                        ) {
                            MultipartPart(part.name, PartValue.File(file))
                        } else {
                            part
                        }
                    },
            )
        }

        else -> {
            this
        }
    }

private fun HttpBody?.files(): List<FileRef> =
    when (this) {
        is HttpBody.File -> listOf(file)
        is HttpBody.Multipart -> parts.filterIsInstance<HttpPart.File>().map { it.file }
        is HttpBody.Text, null -> emptyList()
    }
