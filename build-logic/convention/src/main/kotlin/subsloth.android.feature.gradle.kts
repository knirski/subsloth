import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("subsloth.android.library.compose")
}

val libs = the<VersionCatalogsExtension>().named("libs")

dependencies {
    "implementation"(platform(libs.findLibrary("androidx-compose-bom").get()))
    "implementation"(libs.findLibrary("androidx-compose-ui").get())
    "implementation"(libs.findLibrary("androidx-compose-ui-tooling-preview").get())
    "implementation"(libs.findLibrary("androidx-compose-material3").get())
    "implementation"(libs.findLibrary("androidx-lifecycle-runtime-compose").get())
    "implementation"(libs.findLibrary("androidx-lifecycle-viewmodel-compose").get())
    "implementation"(libs.findLibrary("androidx-navigation3-runtime").get())
    "implementation"(libs.findLibrary("androidx-navigation3-ui").get())
    "implementation"(libs.findLibrary("androidx-lifecycle-viewmodel-navigation3").get())
    "implementation"(libs.findLibrary("kotlinx-collections-immutable").get())
}
