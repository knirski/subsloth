plugins {
    id("subsloth.kmp.android.library")
}

kotlin {
    android {
        namespace = "net.subsloth.preferences"

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
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.datastore.preferences.core)
            implementation(libs.okio)
        }

        jvmMain.dependencies {
            implementation(libs.datastore.preferences)
            implementation(libs.datastore.core.okio)
        }

        androidMain.dependencies {
            implementation(libs.datastore.preferences)
            implementation(libs.datastore.core.okio)
            implementation(libs.androidx.security.crypto)
        }

        getByName("androidDeviceTest").dependencies {
            implementation("androidx.test:runner:1.6.2")
            implementation(libs.androidx.test.ext.junit)
            implementation(libs.coroutines.test)
        }

        // iosMain.dependencies {
        //     implementation(libs.datastore.core.okio)
        // }

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
