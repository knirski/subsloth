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
    }
}
