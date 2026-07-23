# JDK setup

Run `./gradlew` from inside the repo. The Nix dev shell is loaded automatically by `direnv` and provides everything required.

## What runs where

| Role | Version | Source |
|---|---|---|
| Gradle daemon | JDK 25 | flake → `JAVA_HOME` |
| Compile toolchain (`kotlin { jvmToolchain(17) }`) | JDK 17 | flake → `JAVA17_HOME` |
| Bytecode emitted | Java 17 | convention plugins |

The daemon JDK and the toolchain JDK are independent. The daemon runs Gradle / AGP / Kotlin plugin code; the toolchain is the JDK used to compile sources. Bytecode level follows the toolchain.

## Why these versions

- **JDK 25** for the daemon — latest LTS.
- **JDK 17** for the toolchain — what AGP 9.x and the current Kotlin version (see `gradle/libs.versions.toml`) officially target on Android. Anything newer would force desugaring without buying anything for the current `compileSdk`/`minSdk` (see `gradle/libs.versions.toml` and the convention plugins in `build-logic/convention/`; do not duplicate the numbers here — they drift).

## How it's wired

1. **`flake.nix`** ships both JDKs and exports them:

   ```nix
   JAVA_HOME    = "${pkgs.openjdk25}";
   JAVA17_HOME  = "${pkgs.openjdk17}/lib/openjdk";
   ```

   `JAVA_HOME` points at the Nix wrapper root — Gradle resolves `lib/openjdk/` itself when launching the daemon. `JAVA17_HOME` must point directly at `lib/openjdk/` because the Kotlin compiler needs to locate `jmods/` there for cross-compilation targeting Java 17.

2. **`gradle.properties`** points Gradle's toolchain resolver at `JAVA17_HOME`:

   ```properties
   org.gradle.java.installations.fromEnv=JAVA17_HOME
   ```

3. **Convention plugins** declare the toolchain:

   ```kotlin
   kotlin { jvmToolchain(17) }
   ```

## Verifying

```bash
./gradlew --version
# Launcher JVM: 25.x.x …

./gradlew :app:javaToolchains
# Lists a JDK 17 entry "Detected by: environment variable 'JAVA17_HOME'"
```

## Bumping

- **Daemon JDK**: change `openjdk25` → `openjdkNN` in `flake.nix` (both in `packages` and `JAVA_HOME`).
- **Toolchain / bytecode JDK**: bump `jvmToolchain(17)` in `subsloth.android.application.gradle.kts`, `subsloth.android.library.gradle.kts`, and `subsloth.jvm.library.gradle.kts`. In `flake.nix` rename `JAVA17_HOME` → e.g. `JAVA21_HOME` and set it to `${pkgs.openjdkNN}/lib/openjdk` (the `lib/openjdk` suffix is required — see above). Update `gradle.properties` `fromEnv` to match the new name.

## Reference

- [Gradle: toolchains](https://docs.gradle.org/current/userguide/toolchains.html)
