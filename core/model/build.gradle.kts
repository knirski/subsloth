plugins {
    id("subsloth.jvm.library")
}

dependencies {
    compileOnly(libs.androidx.compose.runtime.annotation)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(project(":testing:assertions"))
}
