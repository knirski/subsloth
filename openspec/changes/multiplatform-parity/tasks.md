## 1. Spec and Requirements

- [x] 1.1 Write the `platform-parity` spec with supported platforms, feature parity definition, and iOS/macOS exemption.
- [x] 1.2 Add platform-support table to the spec enumerating Android, desktop, web, and the iOS/macOS exemption.

## 2. Canonical Spec Updates

- [x] 2.1 Update `openspec/specs/project/spec.md` — replace "greenfield native Android app" language with multiplatform-scope language reflecting Android, desktop, and web targets.
- [x] 2.2 Audit `openspec/specs/` for other Android-only language that should be updated to multiplatform language (e.g., `android-ui` spec, `playback` spec, `auth-security` spec).
- [x] 2.3 Audit `openspec/changes/archive/*/specs/` for Archived specs that contain Android-only language relevant to the platform-parity delta.

## 3. Active Change Awareness

- [x] 3.1 Review active changes (`offline-downloads`, `library-settings-diagnostics`, `media-api-developer-ui`, `verification-release`) to ensure their tasks do not assume Android-only scope without documenting platform boundaries.

## 4. Verification

- [x] 4.1 Run `openspec validate multiplatform-parity --strict`.
- [x] 4.2 Confirm the updated project spec reads correctly with the new multiplatform scope.
- [x] 4.3 Run `openspec validate --all --strict` to confirm no cross-change validation failures.
