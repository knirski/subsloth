plugins {
    id("subsloth.kmp.library")
    alias(libs.plugins.compose.gradle)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.androidx.navigation3.runtime)
            implementation(libs.navigation3.ui.kmp)
            implementation(libs.lifecycle.viewmodel.navigation3)
        }
    }
}
