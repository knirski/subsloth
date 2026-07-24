plugins {
    id("subsloth.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
            // Previously transitive via :core:model's api(libs.compose.runtime)
            // (Compose runtime depends on kotlinx-coroutines-core). Now that
            // :core:model no longer depends on the Compose runtime, this
            // module's own use of StateFlow (see SessionPort) must declare
            // the dependency explicitly. api because StateFlow appears in
            // this module's public interfaces.
            api(libs.coroutines.core)
        }

        commonTest.dependencies {
            // InMemorySessionStateTest exercises SessionPort's suspend
            // functions (open/close/invalidate) and needs runTest; this
            // is commonTest (not jvmTest) because the test file is
            // compiled for every target (jvm, wasmJs).
            implementation(libs.coroutines.test)
        }

        jvmTest.dependencies {
            implementation(project(":testing:assertions"))
            implementation(libs.junit.jupiter.params)
        }
    }
}
