plugins {
    id("subsloth.jvm.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

dependencies {
    implementation(libs.wiremock)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(project(":testing:assertions"))
}

val rootDir = rootProject.layout.projectDirectory
val apiContractDir = layout.projectDirectory

val nativeFixturesDir = apiContractDir.dir("src/main/resources/media")
val webFixturesDir = apiContractDir.dir("src/main/resources/media/web-discovery")
val rulesFile = rootDir.file("scripts/capture/sanitization-rules.json")

val workerClasspath = sourceSets.main.get().runtimeClasspath

// ── Export HAR → sanitised fixtures ──────────────────────────────────────────

tasks.register<JavaExec>("exportFixtures") {
    group = "verification"
    description = "Sanitize captured HAR files and export committed fixtures"

    classpath = workerClasspath
    mainClass = "net.subsloth.testing.contract.ExportFixturesKt"

    val harCsv = project.providers.gradleProperty("harFiles").orElse("")
    val keepRaw = project.providers.gradleProperty("keepRaw").orElse("false")

    args(
        harCsv.get(),
        rulesFile.asFile.absolutePath,
        nativeFixturesDir.asFile.absolutePath,
        webFixturesDir.asFile.absolutePath,
        keepRaw.get(),
    )

    inputs
        .files(
            harCsv.map { csv ->
                csv
                    .split(",")
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .map(::File)
            },
        ).withPropertyName("harFiles")
        .optional(true)
    inputs.file(rulesFile).withPropertyName("sanitizationRules")
    inputs.property("keepRaw", keepRaw.map { it.toBoolean() })
    outputs.dir(nativeFixturesDir).withPropertyName("nativeFixtures")
    outputs.dir(webFixturesDir).withPropertyName("webFixtures")
}

// ── Capture API → fixture JSON directly ─────────────────────────────────────

tasks.register<JavaExec>("captureApi") {
    group = "verification"
    description = "Capture native Media API responses directly as fixture JSON files"

    classpath = workerClasspath
    mainClass = "net.subsloth.testing.contract.CaptureApi"

    val email =
        project.providers
            .gradleProperty("email")
            .orElse(project.providers.environmentVariable("SUBSLOTH_LOGIN"))
            .orElse("")
    val password =
        project.providers
            .gradleProperty("password")
            .orElse(project.providers.environmentVariable("SUBSLOTH_PASSWORD"))
            .orElse("")

    args(
        email.get(),
        password.get(),
        nativeFixturesDir.asFile.absolutePath,
        rulesFile.asFile.absolutePath,
    )

    inputs.property("email", email)
    inputs.property("password", password)
    inputs.file(rulesFile).withPropertyName("sanitizationRules")
    outputs.dir(nativeFixturesDir).withPropertyName("nativeFixtures")
}

// ── Offline fixture validation ──────────────────────────────────────────────

tasks.register("validateFixtures") {
    group = "verification"
    description = "Run all fixture-validation tests (offline, no network needed)"
    dependsOn(
        ":testing:api-contract:test",
        ":core:network:jvmTest",
    )
}

// ── Capture + validate (full pipeline) ──────────────────────────────────────

tasks.register("captureAndValidate") {
    group = "verification"
    description = "Capture fresh native fixtures from live API, then validate all fixtures offline"
    dependsOn(
        ":testing:api-contract:captureApi",
        ":testing:api-contract:validateFixtures",
    )
}

tasks.named("validateFixtures") {
    mustRunAfter(":testing:api-contract:captureApi")
}
