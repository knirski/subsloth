import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.diffplug.spotless")
    id("dev.detekt")
    id("org.jetbrains.kotlin.plugin.power-assert")
}

val libs = the<VersionCatalogsExtension>().named("libs")
val ktlintVersion = libs.findVersion("ktlint").get().toString()

kotlin {
    jvmToolchain(17)
    compilerOptions {
        allWarningsAsErrors = true
    }

    jvm()
    iosArm64()
    iosSimulatorArm64()
    macosArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.findLibrary("kotlinx-collections-immutable").get())
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest.dependencies {
            val junitBom = libs.findLibrary("junit-bom").get()
            implementation(project.dependencies.platform(junitBom))
            implementation(libs.findLibrary("junit-jupiter-api").get())
            runtimeOnly(libs.findLibrary("junit-jupiter-engine").get())
            runtimeOnly("org.junit.platform:junit-platform-launcher:${libs.findVersion("junitPlatform").get().requiredVersion}")
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

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

spotless {
    kotlin {
        target("src/*/kotlin/**/*.kt")
        ktlint(ktlintVersion)
            .editorConfigOverride(
                mapOf(
                    "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
                ),
            )
        toggleOffOn()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint(ktlintVersion)
    }
}

detekt {
    config.setFrom(file("${rootProject.layout.projectDirectory}/config/detekt.yml"))
    basePath = rootProject.layout.projectDirectory.asFile
    baseline.set(file("${rootProject.layout.projectDirectory}/config/detekt-baseline.xml"))
}

dependencies {
    detektPlugins(project(":testing:detekt-rules"))
    detektPlugins(libs.findLibrary("compose-rules-detekt").get())
}
