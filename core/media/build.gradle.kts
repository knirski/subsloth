import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
    id("org.jetbrains.kotlin.plugin.compose")
    alias(libs.plugins.compose.gradle)
    alias(libs.plugins.kotlin.power.assert)
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        allWarningsAsErrors = true
    }

    android {
        compileSdk = 37
        minSdk = 26
        namespace = "net.subsloth.core.media"
    }

    jvm()

    iosArm64()
    iosSimulatorArm64()
    macosArm64()

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
            api(libs.compose.media.player)
            implementation(libs.compose.multiplatform.foundation)
            implementation(libs.compose.multiplatform.ui)
            implementation(libs.kermit)
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
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest.dependencies {
            implementation(project.dependencies.platform(libs.junit.bom))
            implementation(libs.junit.jupiter.api)
            runtimeOnly(libs.junit.jupiter.engine)
            runtimeOnly("org.junit.platform:junit-platform-launcher:${libs.versions.junitPlatform.get()}")
            implementation(project(":testing:assertions"))
        }
    }
}

@OptIn(ExperimentalKotlinGradlePluginApi::class)
powerAssert {
    functions =
        listOf(
            "kotlin.assert",
            "kotlin.require",
            "kotlin.check",
            "kotlin.test.assertTrue",
            "kotlin.test.assertFalse",
            "kotlin.test.assertEquals",
            "kotlin.test.assertNotEquals",
            "kotlin.test.assertNull",
            "kotlin.test.assertNotNull",
        )
}

spotless {
    kotlin {
        target("src/*/kotlin/**/*.kt")
        ktlint(libs.versions.ktlint.get())
            .editorConfigOverride(
                mapOf(
                    "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
                ),
            )
        toggleOffOn()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint(libs.versions.ktlint.get())
    }
}

detekt {
    config.setFrom(file("${rootProject.layout.projectDirectory}/config/detekt.yml"))
    basePath = rootProject.layout.projectDirectory.asFile
    baseline.set(file("${rootProject.layout.projectDirectory}/config/detekt-baseline.xml"))
}

dependencies {
    detektPlugins(project(":testing:detekt-rules"))
    detektPlugins(libs.compose.rules.detekt)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
