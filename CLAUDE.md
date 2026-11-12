# APInspector

Android 上的 OpenAPI 查看与请求工具。需求见 [PRD.md](./docs/PRD.md)，术语见 [GLOSSARY.md](./GLOSSARY.md)（代码命名、UI 文案都必须使用其中的规范术语），关键取舍见 [docs/adr/](./docs/adr/)。

## 工程取向

项目只支持 Android 14 及以上（minSdk 34），**不为旧版本兼容性妥协**。以高标准写代码：

- 一律采用当下最新的稳定版工具链与 Jetpack 推荐写法；有更现代的官方方案时，不用旧方案、不写"能跑就行"的代码。
- 引入依赖前核对其**当前最新稳定版本**，不凭记忆写版本号；需要 alpha / beta 才有的能力（如部分 M3 Expressive API）可以用，但要显式 opt-in 并注明原因。
- 不留 TODO 式的临时实现、不吞异常、不写魔法数字 / 字符串、不复制粘贴。
- 偏好不可变数据、纯函数、显式类型边界；领域逻辑与 Android 框架解耦，可在 JVM 上单测。

## 技术约定

- **语言 / 构建**：Kotlin 2.x（K2）、Gradle Kotlin DSL、Version Catalog（`libs.versions.toml`）、KSP（不用 kapt）。
- **模块化**：只有两个模块。`:openapi` 是纯 Kotlin / JVM 模块（不得依赖 Android），包含 OpenAPI 与 `$ref` 解析、领域模型、变量替换、cURL 解析 / 导出、提取规则；开启 `explicitApi()`，其中的类型必须深度不可变（`:app` 的 Compose 稳定性配置据此把整个包声明为稳定）；`:app` 包含其余全部，内部按功能分包。不写 `build-logic` 约定插件。
- **UI**：Jetpack Compose + Material 3 Expressive（参考 `.claude/skills/material-3`），Material3 Adaptive；单向数据流，每屏一个不可变 `UiState` + `StateFlow`，`collectAsStateWithLifecycle`；开启 Compose 强跳过与稳定性配置。
- **导航**：Navigation 3（类型安全、状态由应用持有，便于与自适应布局协作）。
- **异步**：Coroutines + Flow；结构化并发，不使用 `GlobalScope`。
- **DI**：Hilt。
- **序列化**：kotlinx.serialization。
- **存储**：Room（KSP）；轻量设置用 DataStore。
- **网络**：OkHttp（利用 EventListener 获取耗时分解）。
- **质量**：Spotless + ktlint（`ktlint_official`）+ Compose 规则；Detekt 2；Android Lint 与 Kotlin 编译器均把警告视为错误。
- **测试**：只写单元测试，覆盖领域逻辑（OpenAPI 解析、`$ref` 解析、变量替换、cURL 解析 / 导出、提取规则等）与 ViewModel（JUnit 6 Jupiter + Turbine）。不写 UI 测试、截图测试和 e2e 测试。

## 常用命令

- `./gradlew spotlessApply`：格式化。
- `./gradlew check`：Spotless、Detekt、Android Lint 与全部单元测试；提交前必须通过。
- `./gradlew :app:assembleDebug`：构建调试包。
