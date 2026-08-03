import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("subsloth.web.library")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.gradle)
}

@OptIn(ExperimentalWasmDsl::class)
kotlin {
    wasmJs {
        browser {
            testTask {
                useKarma {
                    useFirefox()
                    useChromeHeadless()
                }
            }
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

            implementation(libs.ktor.client.core)
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

        wasmJsTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
            implementation(libs.kotlinx.browser)
        }
    }
}

// Compose compiler — share the project-wide stability configuration so that
// :core:model types (which no longer depend on the Compose runtime) are
// still recognised as stable during strong-skipping-mode analysis.
composeCompiler {
    stabilityConfigurationFiles.add(
        rootProject.layout.projectDirectory.file("config/compose_stability.conf"),
    )
}
