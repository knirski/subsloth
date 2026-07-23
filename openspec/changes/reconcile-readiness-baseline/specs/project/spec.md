# project Specification (delta)

## MODIFIED Requirements

### Requirement: Toolchain Baseline
The project SHALL treat `gradle/wrapper/gradle-wrapper.properties` (Gradle version), `gradle/libs.versions.toml` (AGP, Kotlin, `compileSdk`, `minSdk`, `targetSdk`), and `flake.nix` (JDK roles) as the single executable source of truth for toolchain versions. Prose documentation MAY describe current values for readability but SHALL NOT be treated as normative when it disagrees with the catalog, wrapper, or flake; a document found to disagree SHALL be corrected to match the executable source rather than the reverse.

#### Scenario: Build is reproducible from the Nix shell
- **WHEN** a developer runs `./gradlew help` from the project directory on a clean machine (with `direnv allow` completed and no system JDK configured)
- **THEN** the build succeeds — all required JDK versions are supplied exclusively by the Nix flake

#### Scenario: Emitted bytecode targets Java 17
- **WHEN** any compiled module artifact is inspected
- **THEN** its class files are at Java 17 level regardless of the JDK running the Gradle daemon

#### Scenario: Toolchain doc drift is corrected toward the catalog
- **WHEN** a document states a Gradle, AGP, Kotlin, `compileSdk`, `minSdk`, or `targetSdk` value
- **THEN** the stated value matches `gradle/wrapper/gradle-wrapper.properties` and `gradle/libs.versions.toml` at the time of writing, or the document is corrected
