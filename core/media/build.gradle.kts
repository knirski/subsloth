plugins {
    id("subsloth.android.library")
}

android {
    namespace = "subsloth.core.media"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(project(":core:datasource-ktor"))
    implementation(libs.kotlinx.collections.immutable)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(project(":testing:assertions"))
}
