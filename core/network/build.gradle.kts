plugins {
    id("subsloth.kmp.library")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
            implementation(project(":core:domain"))
            implementation(project(":core:database"))
            implementation(project(":core:preferences"))
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.kermit)

            // Ktor
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.auth)
            implementation(libs.ktor.client.encoding)

            // Ktor client engine declared in platform source sets below
            // CIO is used for JVM only; native/iOS targets are currently disabled
        }

        jvmMain.dependencies {
            implementation(libs.ktor.client.cio)
        }

        // iosMain.dependencies {
        //     implementation(libs.ktor.client.cio)
        // }

        val wasmJsMain = sourceSets.getByName("wasmJsMain")
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.mock)
        }

        commonTest.dependencies {
            // kotlin("test") is already provided by subsloth.kmp.library convention
        }

        jvmTest.dependencies {
            implementation(project(":testing:assertions"))
            implementation(project(":testing:api-contract"))
            implementation(libs.ktor.client.mock)
            implementation(libs.coroutines.test)
            implementation(libs.turbine)
            implementation(libs.kotlinx.schema.generator.json)
        }
    }
}
