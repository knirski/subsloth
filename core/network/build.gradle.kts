plugins {
    id("subsloth.kmp.library")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.collections.immutable)

            // Ktor
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.auth)
            implementation(libs.ktor.client.encoding)

            // Platform engines — CIO supports all targets (JVM, Native, JS)
            implementation(libs.ktor.client.cio)
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
