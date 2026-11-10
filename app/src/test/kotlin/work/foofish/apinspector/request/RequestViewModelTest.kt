package work.foofish.apinspector.request

import app.cash.turbine.test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import work.foofish.apinspector.environment.EnvironmentId
import work.foofish.apinspector.history.RequestSource
import work.foofish.apinspector.navigation.OperationPageKey
import work.foofish.apinspector.network.SendTarget
import work.foofish.apinspector.openapi.HttpMethod
import work.foofish.apinspector.openapi.OperationKey
import work.foofish.apinspector.openapi.ParameterLocation
import work.foofish.apinspector.openapi.request.AssemblyProblem
import work.foofish.apinspector.openapi.request.BodyInput
import work.foofish.apinspector.openapi.request.HttpBody
import work.foofish.apinspector.openapi.request.JsonNode
import work.foofish.apinspector.openapi.request.ParameterValue
import work.foofish.apinspector.openapi.request.RequestInput
import work.foofish.apinspector.request.body.JsonPath
import work.foofish.apinspector.request.body.JsonStep
import work.foofish.apinspector.spec.SpecId
import work.foofish.apinspector.testing.FakeEnvironmentRepository
import work.foofish.apinspector.testing.FakeFileAccess
import work.foofish.apinspector.testing.FakeRequestInputRepository
import work.foofish.apinspector.testing.FakeRequestSender
import work.foofish.apinspector.testing.FakeSpecRepository
import work.foofish.apinspector.testing.MainDispatcherExtension
import work.foofish.apinspector.testing.loaded
import work.foofish.apinspector.testing.summary

class RequestViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val specs = FakeSpecRepository()
    private val environments = FakeEnvironmentRepository()
    private val inputs = FakeRequestInputRepository()
    private val sender = FakeRequestSender()
    private val specId = SpecId("a")
    private val operation = OperationKey(HttpMethod.POST, "/pets/{id}")

    init {
        specs.put(
            summary("a"),
            loaded(
                """
                servers:
                  - url: https://{host}/v1
                    variables:
                      host: { default: api.example.com }
                paths:
                  /pets/{id}:
                    post:
                      parameters:
                        - { name: id, in: path, required: true, schema: { type: string } }
                        - { name: limit, in: query, schema: { type: integer, default: 10 } }
                      requestBody:
                        content:
                          application/json:
                            schema:
                              type: object
                              properties:
                                name: { type: string, example: Rex }
                                age: { type: integer }
                """,
            ),
        )
    }

    // 在 setMain 之后创建：viewModelScope 与保存用的作用域都使用测试调度器。
    private val viewModel by lazy {
        RequestViewModel(
            key = OperationPageKey(specId, operation),
            specs = specs,
            environments = environments,
            inputs = inputs,
            executor = sender,
            files = FakeFileAccess(),
            userAgent = "APInspector/test",
            applicationScope = CoroutineScope(main.dispatcher),
        )
    }

    private fun RequestUiState.loaded(): RequestUiState.Loaded = assertInstanceOf(RequestUiState.Loaded::class.java, this)

    @Test
    fun `starts from document defaults and generates environments from servers`() =
        runTest {
            viewModel.uiState.test {
                val state = expectMostRecentItem().loaded()
                assertEquals("api.example.com", environments.find(EnvironmentId("env-0"))?.variableValues?.get("host"))
                assertEquals(listOf(ParameterLocation.PATH, ParameterLocation.QUERY), state.groups.map { it.location })
                val limit = state.groups[1].fields.single()
                assertEquals(ParameterValue.Text("10"), limit.value)
                assertFalse(limit.enabled)
                assertTrue(
                    state.groups[0]
                        .fields
                        .single()
                        .alwaysSent,
                )
                assertEquals("application/json", state.body?.selected)
                // 尚未编辑时不保存。
                assertTrue(inputs.saved.isEmpty())
            }
        }

    @Test
    fun `edits are saved immediately`() =
        runTest {
            viewModel.uiState.test {
                expectMostRecentItem()
                viewModel.setParameterValue(ParameterLocation.PATH, "id", ParameterValue.Text("7"))
                viewModel.setParameterEnabled(ParameterLocation.QUERY, "limit", true)

                val state = expectMostRecentItem().loaded()
                assertEquals(
                    ParameterValue.Text("7"),
                    state.groups[0]
                        .fields
                        .single()
                        .value,
                )
                assertTrue(
                    state.groups[1]
                        .fields
                        .single()
                        .enabled,
                )
                val saved = inputs.stored(specId, operation)?.input
                assertEquals(ParameterValue.Text("7"), saved?.parameter(ParameterLocation.PATH, "id")?.value)
                assertEquals(true, saved?.parameter(ParameterLocation.QUERY, "limit")?.enabled)
            }
        }

    @Test
    fun `sends the assembled request and reports it was sent`() =
        runTest {
            viewModel.uiState.test {
                expectMostRecentItem()
                viewModel.setParameterValue(ParameterLocation.PATH, "id", ParameterValue.Text("7"))
                viewModel.send()

                assertTrue(expectMostRecentItem().loaded().sent)
                val job = sender.sent.single()
                assertEquals(SendTarget(specId, operation), job.target)
                assertEquals("https://api.example.com/v1/pets/7", job.request.url)
                assertEquals(HttpBody.Text("application/json", "{\n  \"name\": \"Rex\",\n  \"age\": 0\n}"), job.request.body)
                assertEquals(RequestSource.OPERATION, job.source)
                assertTrue(job.loadCookieJar)

                viewModel.onSentShown()
                val sending = expectMostRecentItem().loaded()
                assertFalse(sending.sent)
                assertTrue(sending.sending)
            }
        }

    @Test
    fun `missing variables block sending`() =
        runTest {
            viewModel.uiState.test {
                expectMostRecentItem()
                viewModel.setParameterValue(ParameterLocation.PATH, "id", ParameterValue.Text("{{petId}}"))
                viewModel.send()

                val problems = expectMostRecentItem().loaded().problems
                assertEquals(listOf(SendProblem.Assembly(AssemblyProblem.MissingVariables(listOf("petId")))), problems)
                assertTrue(sender.sent.isEmpty())

                viewModel.dismissProblems()
                assertNull(expectMostRecentItem().loaded().problems)
            }
        }

    @Test
    fun `visual JSON edits rewrite the body text`() =
        runTest {
            viewModel.uiState.test {
                expectMostRecentItem()
                viewModel.setJsonNode(JsonPath.ROOT.child(JsonStep.Key("name")), JsonNode.VariableNode("petName"))
                viewModel.removeJsonNode(JsonPath.ROOT.child(JsonStep.Key("age")))
                viewModel.addJsonMember(JsonPath.ROOT, "age")

                val body = inputs.stored(specId, operation)?.input?.body
                assertEquals(BodyInput.Json("application/json", "{\n  \"name\": {{petName}},\n  \"age\": 0\n}"), body)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `presets track modification and load over the current params`() =
        runTest {
            viewModel.uiState.test {
                expectMostRecentItem()
                viewModel.setParameterValue(ParameterLocation.PATH, "id", ParameterValue.Text("1"))
                viewModel.saveAsPreset("first")
                val saved = expectMostRecentItem().loaded().presets
                assertEquals("first", saved.current?.name)
                assertFalse(saved.modified)

                viewModel.setParameterValue(ParameterLocation.PATH, "id", ParameterValue.Text("2"))
                assertTrue(expectMostRecentItem().loaded().presets.modified)

                viewModel.loadPreset(checkNotNull(saved.current).id)
                val reloaded = expectMostRecentItem().loaded()
                assertEquals(
                    ParameterValue.Text("1"),
                    reloaded.groups[0]
                        .fields
                        .single()
                        .value,
                )
                assertFalse(reloaded.presets.modified)
            }
        }

    @Test
    fun `adopts current params written elsewhere, such as restoring a record`() =
        runTest {
            viewModel.uiState.test {
                val defaults = expectMostRecentItem()
                assertInstanceOf(RequestUiState.Loaded::class.java, defaults)
                val restored = RequestInput(parameters = emptyList(), extras = emptyList(), body = null)
                inputs.saveWorking(specId, operation, WorkingParams(restored, presetId = null))

                val state = expectMostRecentItem().loaded()
                assertNull(state.body?.selected)
            }
        }
}
