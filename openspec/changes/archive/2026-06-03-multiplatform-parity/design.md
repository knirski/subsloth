## Context

subsloth's build tree already includes three entrypoints: `:androidApp`, `:desktopApp`, and `:webApp`. Shared UI lives in `:core:ui` and shared feature modules under `:feature:*`. However, all v1 requirements and verification gates target only Android phone, tablet, and TV. The desktop and web apps are built and runnable but their behavior, testing, and release processes are undefined.

The existing project spec (`openspec/specs/project/spec.md`) describes the project as a "greenfield native Android app" — this is no longer accurate and will cause confusion for new contributors and agent workflows that consult the spec as source of truth.

## Goals

- Define which platforms are "supported" and subject to feature parity.
- Define what "feature parity" means in concrete, verifiable terms.
- Exempt iOS and macOS explicitly, with rationale.
- Update the project spec to reflect the actual multiplatform scope.
- Keep the parity requirement additive — future changes that add platform-specific features must provide equivalent capability on all supported platforms.

## Non-goals

- No change to the existing architecture or codebase in this change itself — this is a spec/requirements change only.
- No introduction of iOS or macOS support or build targets.
- No mandate to block Android feature development until desktop/web catch up — parity is a forward-looking requirement for new work, not a retroactive backlog.
- No new CI or verification infrastructure for desktop/web in this change — those belong in a follow-up change.

## Decisions

### 1. Supported platforms

Android (phone, tablet, TV), desktop (JVM / Compose Desktop), and web (Wasm JS / Compose for Web) are supported platforms and subject to feature parity.

Desktop packaging targets (Dmg, Msi, Deb) are distribution formats, not distinct platforms — parity applies to the Compose Desktop runtime, not to each packaging format.

### 2. iOS and macOS exemption

iOS and macOS are explicitly exempt from the feature parity requirement. Apple hardware is required to build, test, and debug these targets. Until hardware is available, no parity obligation exists. Future introduction of iOS/macOS support requires a new OpenSpec change that lifts the exemption.

### 3. Feature parity definition

Feature parity means: when a user-facing capability (screen, flow, action, setting, or notification) is available on one supported platform, every other supported platform provides a functionally equivalent version adapted to platform conventions and input modality.

Examples of acceptable differences:
- Android TV uses D-pad navigation; desktop uses mouse/keyboard; web uses browser-native pointer/keyboard.
- Platform-specific features (Android TV focus, desktop window management, web URL deep links) are additive and do not require back-porting.
- Android notifications have no desktop/web equivalent — parity does not require recreating notifications outside Android.

Examples of unacceptable differences:
- A catalog screen exists on Android but is missing on desktop or web.
- A settings toggle works on Android but is a no-op or missing on another platform.
- A detail view renders different data or omits entire sections on a non-Android platform.

### 4. Platform-conditional features

Some v1 features are inherently Android-specific (auth persistence via EncryptedSharedPreferences and AccountManager, offline downloads via app-private storage and WorkManager, Android TV focus). These SHALL use platform-conditional patterns: `expect`/`actual` for Core-FC types, interface- or inject-based dispatching for shell integrations, and no-op stubs or degraded-but-functional behavior on non-Android platforms.

### 5. Shared module coverage

The following modules are already shared across all three entrypoints and SHALL remain in `commonMain` or JVM source sets as appropriate:

- `:core:model`
- `:core:domain`
- `:core:network`
- `:core:preferences`
- `:core:ui`
- `:feature:catalog`
- `:feature:details`
- `:feature:player`
- `:feature:settings`

Android-only modules (`:core:database`, `:core:media`, `:feature:auth`, `:feature:library`) SHALL provide documented platform-conditional behavior for desktop/web.

## Risks

- Desktop and web may lack platform equivalents for Android APIs (Room, WorkManager, Media3). Mitigation: use shared interface/abstraction patterns with platform implementations where needed.
- Feature parity as a policy may slow Android development if interpreted as blocking. Mitigation: parity is forward-looking for new features, not a gate on Android-only work.
- Web Wasm JS target has performance and API constraints (single-threaded, no filesystem, browser sandbox). Mitigation: parity applies to functionally equivalent behavior, not identical implementation.

## Migration Plan

1. Create the spec and requirements for multiplatform parity (this change).
2. Update `openspec/specs/project/spec.md` to reflect multiplatform scope.
3. Audit existing specs for Android-only language that should be updated to multiplatform language.
4. Archive this change after verification.

## Open Questions

- Should desktop and web have separate formal CI verification in this change, or in a follow-up?
- How should the platform-exemption list be maintained as new targets are evaluated (e.g., iOS in future)?
