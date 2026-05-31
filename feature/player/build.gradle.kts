plugins {
    id("subsloth.kmp.library")
    alias(libs.plugins.compose.gradle)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
            implementation(project(":core:domain"))
            implementation(project(":core:network"))

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.lifecycle.viewmodel.compose)
        }
        jvmTest.dependencies {
            implementation(project(":testing:assertions"))
            implementation(libs.turbine)
            implementation(libs.coroutines.test)
        }
    }
}
