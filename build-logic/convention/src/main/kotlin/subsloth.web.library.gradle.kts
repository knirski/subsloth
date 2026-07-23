import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.diffplug.spotless")
    id("dev.detekt")
}

val libs = the<VersionCatalogsExtension>().named("libs")
val ktlintVersion = libs.findVersion("ktlint").get().toString()

kotlin {
    jvmToolchain(17)
    compilerOptions {
        allWarningsAsErrors = true
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
    detektPlugins(this.project(":testing:detekt-rules"))
    detektPlugins(libs.findLibrary("compose-rules-detekt").get())
}
