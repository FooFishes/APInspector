# 参与贡献

感谢你愿意花时间改进 APInspector。这份文档说明如何反馈问题、提交改动，以及项目对代码的要求。

## 反馈问题与建议

- **Bug**：请通过 [Issue](https://github.com/FooFishes/APInspector/issues/new/choose) 提交，并附上复现步骤。如果问题与某份 OpenAPI 文档有关，请尽量提供能复现问题的最小片段，提交前去掉其中的真实地址和凭证。
- **功能建议**：请先提交 Issue 讨论，再动手实现。[PRD 第 9 节](docs/PRD.md#9-明确不做v1)列出了当前版本有意不做的功能，这些功能需要先就范围达成一致。
- **安全问题**：请不要公开提交，按 [SECURITY.md](SECURITY.md) 的方式报告。

## 开发环境

- JDK 21
- Android SDK，推荐使用最新稳定版 Android Studio
- 运行应用需要 Android 14（API 34）或更高版本的设备或模拟器

```bash
./gradlew :app:assembleDebug   # 构建调试包
./gradlew spotlessApply        # 格式化
./gradlew check                # Spotless、Detekt、Android Lint 与全部单元测试
```

## 提交改动

1. Fork 仓库，从 `main` 创建分支。
2. 完成改动，并为领域逻辑补充单元测试。
3. 确认 `./gradlew check` 通过。Kotlin 编译器与 Android Lint 的警告都按错误处理，"有新版本可用"类的提示除外。
4. 发起 Pull Request，说明改动的目的，涉及界面时附上截图。

提交信息沿用现有历史的格式，即类型前缀加一句中文描述：

```
feat: 自定义请求支持从 cURL 导入
fix: 分段按钮的中文标签不再折行
docs: 补充环境变量的说明
```

## 代码约定

完整的技术约定见 [CLAUDE.md](CLAUDE.md)，其中最需要留意的几点：

- **术语**：代码命名和界面文案使用 [GLOSSARY.md](GLOSSARY.md) 中的规范术语。例如用"接口"而不是"端点"，用"预设"而不是"用例"。
- **模块边界**：`:openapi` 是纯 Kotlin / JVM 模块，不能依赖 Android，其中的类型需要深度不可变。
- **测试**：只写单元测试，覆盖领域逻辑与 ViewModel。项目不写 UI 测试、截图测试和端到端测试。
- **依赖**：新增依赖前请确认它的最新稳定版本，并在 PR 中说明引入的理由。
- **界面文案**：字符串全部放在资源文件中，目前只提供中文。

影响较大的设计取舍请记录为一篇 ADR，放在 [docs/adr/](docs/adr/) 下，格式参考已有的几篇。

## 许可

提交到本仓库的贡献，将与项目一样以 [GPL-3.0](LICENSE) 发布。
