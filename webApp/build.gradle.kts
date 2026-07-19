import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.gradle)
}

@OptIn(ExperimentalWasmDsl::class)
kotlin {
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "subsloth-web.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.multiplatform.foundation)
            implementation(libs.compose.multiplatform.material3)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.multiplatform.ui)

            implementation(project(":core:model"))
            implementation(project(":core:domain"))
            implementation(project(":core:network"))
            implementation(project(":core:ui"))
            implementation(project(":feature:catalog"))
            implementation(project(":feature:details"))
            implementation(project(":feature:player"))
            implementation(project(":feature:settings"))
            implementation(project(":feature:library"))
            implementation(project(":feature:auth"))

            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.androidx.navigation3.runtime)
            implementation(libs.navigation3.ui.kmp)
            implementation(libs.lifecycle.viewmodel.navigation3)
            implementation(libs.savedstate)
            implementation(libs.savedstate.compose)

            implementation(npm("@sqlite.org/sqlite-wasm", "3.51.2-build5"))
            implementation(npm("sqlite-wasm-worker", "file:${project.projectDir}/sqlite-wasm-worker"))
        }
    }
}
