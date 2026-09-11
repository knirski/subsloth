import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    id("subsloth.kmp.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
    alias(libs.plugins.compose.gradle)
}

kotlin {
    // Create the jvmShared intermediate source set through the default
    // hierarchy template rather than manual dependsOn() edges: KGP warns when
    // explicit edges are configured while the template is in use. The
    // download byte-transfer worker lives here because it uses java.io, so it
    // cannot be in commonMain (wasmJs compiles commonMain), and duplicating
    // it across androidMain/jvmMain would be worse. The wasmJs target never
    // depends on it.
    applyDefaultHierarchyTemplate {
        common {
            group("jvmShared") {
                withJvm()
                // withAndroidTarget() only matches the legacy
                // KotlinAndroidTarget, not the AGP `com.android.kotlin.
                // multiplatform.library` target, so match by platform type.
                withCompilations { it.target.platformType == KotlinPlatformType.androidJvm }
            }
        }
    }

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
        wasmJsMain.dependencies {
            // Document access for the autoplay muted-retry in PlayerBridgeSurface.
            implementation(libs.kotlinx.browser)
        }
        jvmTest.dependencies {
            implementation(project(":testing:assertions"))
            implementation(project(":core:database"))
            implementation(libs.coroutines.test)
            implementation(libs.turbine)
            implementation(libs.ktor.client.mock)
        }

        // jvmSharedMain is created by the hierarchy template above; only its
        // dependencies are configured here.
        val jvmSharedMain by getting {
            dependencies {
                implementation(libs.ktor.client.core)
            }
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
