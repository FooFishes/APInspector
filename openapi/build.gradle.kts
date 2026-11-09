plugins {
    alias(libs.plugins.kotlin.jvm)
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
