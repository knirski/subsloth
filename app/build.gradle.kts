plugins {
    id("subsloth.android.application.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val appVersionName =
    try {
        val gitVersion =
            providers
                .exec {
                    workingDir = rootProject.projectDir
                    commandLine("git", "describe", "--tags", "--abbrev=0", "--match=v*")
                    isIgnoreExitValue = true
                }.standardOutput.asText
                .get()
                .trim()
                .removePrefix("v")
        if (gitVersion.isEmpty()) "0.0.1" else gitVersion
    } catch (_: Exception) {
        "0.0.1"
    }
val appVersionCode =
    appVersionName
        .takeWhile { it.isDigit() || it == '.' }
        .split(".")
        .let { parts ->
            val major = parts.getOrElse(0) { "0" }.toIntOrNull() ?: 0
            val minor = parts.getOrElse(1) { "0" }.toIntOrNull() ?: 0
            val patch = parts.getOrElse(2) { "0" }.toIntOrNull() ?: 0
            val code = major * 1000000 + minor * 1000 + patch
            if (code > 0) code else 1
        }

android {
    namespace = "net.subsloth"

    defaultConfig {
        applicationId = "net.subsloth"
        versionCode = appVersionCode
        versionName = appVersionName
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    androidComponents {
        onVariants { variant ->
            variant.outputs.forEach { output ->
                output.outputFileName.set(
                    "subsloth-${output.versionName.get()}-${variant.buildType}.apk",
                )
            }
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:domain"))
    implementation(project(":core:network"))
    implementation(project(":core:database"))
    implementation(project(":core:preferences"))
    implementation(project(":core:media"))

    implementation(project(":feature:auth"))
    implementation(project(":feature:catalog"))
    implementation(project(":feature:details"))
    implementation(project(":feature:player"))
    implementation(project(":feature:library"))
    implementation(project(":feature:settings"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.material3.adaptive)
    implementation(libs.androidx.material3.adaptive.layout)
    implementation(libs.androidx.material3.adaptive.navigation)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)

    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)

    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(project(":testing:tv-focus-harness"))
}
