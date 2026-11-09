plugins {
    alias(libs.plugins.android.application) apply false
    // AGP 9 内置 Kotlin 支持；在根项目声明 KGP 让 :app 与 :openapi 共用同一个 Kotlin 版本。
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
}

// 显式声明，使 .editorconfig 成为格式化任务的输入；否则修改它不会让缓存失效。
val editorConfig = file(".editorconfig")

spotless {
    kotlin {
        target("*/src/**/*.kt")
        ktlint(libs.versions.ktlint.get())
            .setEditorConfigPath(editorConfig)
            .customRuleSets(listOf(libs.compose.rules.ktlint.get().toString()))
    }
    kotlinGradle {
        target("*.gradle.kts", "*/*.gradle.kts")
        ktlint(libs.versions.ktlint.get())
            .setEditorConfigPath(editorConfig)
    }
}

detekt {
    buildUponDefaultConfig = true
    parallel = true
    config.setFrom(file("config/detekt/detekt.yml"))
    source.setFrom("app/src", "openapi/src")
}
