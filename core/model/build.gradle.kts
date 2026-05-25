plugins {
    id("subsloth.jvm.library")
}

dependencies {
    compileOnly(libs.androidx.compose.runtime.annotation)
    implementation(libs.kotlinx.collections.immutable)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(project(":testing:assertions"))
}
