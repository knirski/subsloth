plugins {
    id("subsloth.kmp.library")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
            implementation(project(":core:domain"))
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.datastore.preferences.core)
            implementation(libs.okio)
        }

        jvmMain.dependencies {
            implementation(libs.datastore.preferences)
            implementation(libs.datastore.core.okio)
        }

        iosMain.dependencies {
            implementation(libs.datastore.core.okio)
        }

        wasmJsMain.dependencies {
            implementation(libs.datastore.core.okio)
            implementation(libs.kotlinx.browser)
            implementation(libs.coroutines.core)
        }

        jvmTest.dependencies {
            implementation(project(":testing:assertions"))
            implementation(libs.coroutines.test)
            implementation(libs.turbine)
        }
    }
}
