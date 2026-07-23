# project Specification (delta)

## MODIFIED Requirements

### Requirement: Android Project Baseline
The project SHALL build a greenfield multiplatform KMP app with application id and namespace `net.subsloth` for Android (phone, tablet, TV), desktop (JVM), and web (Wasm JS).

#### Scenario: Scaffolded modules are present
- **WHEN** `./gradlew projects` is executed
- **THEN** the listed modules include `:androidApp`, `:desktopApp`, `:webApp`, `:core:model`, `:core:domain`, `:core:network`, `:core:data`, `:core:database`, `:core:preferences`, `:core:media`, `:core:ui`, `:feature:auth`, `:feature:catalog`, `:feature:details`, `:feature:player`, `:feature:library`, and `:feature:settings`

#### Scenario: App identity is locked
- **WHEN** the app module is configured
- **THEN** its namespace and application id are both `net.subsloth`
