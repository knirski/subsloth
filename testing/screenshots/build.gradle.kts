plugins {
    id("subsloth.android.library.compose")
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "subsloth.testing.screenshot"

    lint {
        disable += "InvalidPackage"
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)

    implementation(libs.roborazzi)
    implementation(libs.roborazzi.compose)
    implementation(libs.roborazzi.junit.rule)
}
