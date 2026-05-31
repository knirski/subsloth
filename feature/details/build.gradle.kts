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
            implementation(project(":core:network"))
            implementation(project(":core:ui"))

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
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
