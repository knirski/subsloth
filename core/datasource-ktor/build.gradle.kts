plugins {
    id("subsloth.android.library")
}

android {
    namespace = "subsloth.core.datasource.ktor"
}

dependencies {
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.datasource)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.androidx.annotation)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.coroutines.test)
}
