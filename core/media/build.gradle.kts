plugins {
    id("subsloth.android.library")
}

android {
    namespace = "net.subsloth.core.media"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.okhttp)
}
