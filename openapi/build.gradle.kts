plugins {
    alias(libs.plugins.kotlin.jvm)
    // 当前参数、预设、实际请求等需要持久化的领域模型直接标注 @Serializable。
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(libs.versions.jvm.toolchain.get().toInt())
    explicitApi()
    compilerOptions {
        allWarningsAsErrors = true
    }
}

dependencies {
    // 模型中的示例值、默认值、枚举值以 JsonElement 表示，属于公开 API。
    api(libs.kotlinx.serialization.json)
    implementation(libs.snakeyaml.engine)
}

testing {
    suites {
        named<JvmTestSuite>("test") {
            useJUnitJupiter(libs.versions.junit)
        }
    }
}
