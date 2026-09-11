plugins {
    id("subsloth.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
            implementation(project(":core:domain"))
            implementation(project(":core:network"))
            implementation(project(":core:database"))
            implementation(project(":core:preferences"))
            // Ktor client types (HttpClient/HttpClientEngine) are used directly
            // by the API-validated session state's test-support engine override.
            implementation(libs.ktor.client.core)
        }

        jvmTest.dependencies {
            implementation(project(":testing:assertions"))
            implementation(libs.ktor.client.mock)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.coroutines.test)
            implementation(libs.turbine)
            implementation(libs.room3.runtime)
            implementation(libs.sqlite.bundled)
            implementation(libs.datastore.preferences)
        }
    }
}
