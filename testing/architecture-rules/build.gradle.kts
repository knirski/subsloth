plugins {
    kotlin("jvm")
    alias(libs.plugins.detekt)
}

group = "net.subsloth.buildlogic"

dependencies {
    testImplementation(gradleTestKit())
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    systemProperty("subsloth.rootDir", rootProject.projectDir.absolutePath)
}
