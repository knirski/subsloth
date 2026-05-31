import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test

plugins {
    id("com.android.library")
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

android {
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    lint {
        abortOnError = true
        warningsAsErrors = true
        checkReleaseBuilds = false
        checkAllWarnings = true
        disable += "GradleDependency"
        // Ktor's ktor-utils references java.lang.management not available on Android
        disable += "InvalidPackage"
    }
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
    testImplementation(kotlin("test"))
    androidTestImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:${libs.findVersion("junitPlatform").get().requiredVersion}")
}
