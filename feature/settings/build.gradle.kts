plugins {
    id("subsloth.kmp.library")
    id("org.jetbrains.kotlin.plugin.compose")
    alias(libs.plugins.compose.gradle)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
            implementation(project(":core:domain"))

            implementation(libs.compose.runtime)
            implementation(libs.compose.multiplatform.foundation)
            implementation(libs.compose.multiplatform.material3)
            implementation(libs.compose.multiplatform.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.lifecycle.viewmodel.compose)
        }
        jvmMain.dependencies {
            implementation(libs.compose.multiplatform.ui.tooling.preview)
        }
        jvmTest.dependencies {
            implementation(project(":testing:assertions"))
            implementation(libs.turbine)
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
