plugins {
    id("subsloth.kmp.library")
    id("org.jetbrains.kotlin.plugin.compose")
    alias(libs.plugins.compose.gradle)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            implementation(project(":core:domain"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.multiplatform.foundation)
            implementation(libs.compose.multiplatform.material3)
            implementation(libs.compose.multiplatform.ui)
            implementation(libs.compose.components.resources)
            api(libs.kotlinx.serialization)
            api(libs.savedstate)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.androidx.navigation3.runtime)
            implementation(libs.navigation3.ui.kmp)
            implementation(libs.lifecycle.viewmodel.navigation3)
        }
        jvmMain.dependencies {
            implementation(libs.compose.multiplatform.ui.tooling.preview)
        }
        commonTest.dependencies {
            // SessionGateTest exercises SessionPort's suspend functions
            // (open/close/invalidate) and needs runTest; this is
            // commonTest (not jvmTest) because the test file is
            // compiled for every target (jvm, wasmJs).
            implementation(libs.coroutines.test)
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
