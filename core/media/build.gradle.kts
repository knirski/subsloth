import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("subsloth.kmp.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
    alias(libs.plugins.compose.gradle)
}

kotlin {
    android {
        namespace = "net.subsloth.core.media"
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "subsloth-media.js"
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
            implementation(project(":core:domain"))
            implementation(project(":core:database"))
            api(libs.compose.media.player)
            implementation(libs.compose.multiplatform.foundation)
            implementation(libs.compose.multiplatform.ui)
        }
        androidMain.dependencies {
            implementation(project(":core:database"))
            implementation(project(":core:preferences"))
            implementation(libs.media3.exoplayer.hls)
            implementation(libs.androidx.core.ktx)
            implementation(libs.kotlinx.collections.immutable)
        }
        jvmMain.dependencies {
            implementation(libs.compose.multiplatform.ui.tooling.preview)
        }
        jvmTest.dependencies {
            implementation(project(":testing:assertions"))
            implementation(project(":core:database"))
            implementation(libs.coroutines.test)
            implementation(libs.turbine)
        }
    }
}
