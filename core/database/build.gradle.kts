plugins {
    id("subsloth.kmp.library")
    id("com.android.kotlin.multiplatform.library")
    alias(libs.plugins.room3)
    alias(libs.plugins.ksp)
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    android {
        namespace = "net.subsloth.database"
        compileSdk = 37
        minSdk = 26

        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            execution = "HOST"
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
            implementation(project(":core:domain"))
            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.room3.runtime)
        }

        jvmMain.dependencies {
            implementation(libs.sqlite.bundled)
        }

        androidMain.dependencies {
            implementation(libs.sqlite.bundled)
        }

        getByName("androidDeviceTest").dependencies {
            implementation("androidx.test:runner:1.6.2")
            implementation(libs.androidx.test.ext.junit)
        }

        // TODO: restore when iOS targets re-enabled — iosMain.dependencies needs
        // libs.sqlite.framework for NativeSQLiteDriver in core/database/src/iosMain/

        wasmJsMain.dependencies {
            implementation(libs.sqlite.web)
            implementation(libs.kotlinx.browser)
        }

        jvmTest.dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.coroutines.test)
            implementation(libs.turbine)
        }
    }
}

dependencies {
    kspJvm(libs.room3.compiler)
    kspAndroid(libs.room3.compiler)
    kspWasmJs(libs.room3.compiler)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
