## 1. Capture And Export

- [x] 1.0 Define sanitization patterns and PII redaction rules.
- [x] 1.1 Add a local-only browser traffic capture/export workflow for authenticated Media discovery sessions.
- [x] 1.2 Sanitize request/response data so committed fixtures contain no secrets, signed URLs, cookies, or raw traces.
- [x] 1.3 Split exports into native-contract fixtures and web-only discovery fixtures.
- [x] 1.4 Delete any raw session data/artifacts after sanitization completes and ignore temporary capture paths via `.gitignore`.

## 2. Mock Generation

- [x] 2.1 Derive programmatic replay stubs from the sanitized fixtures.
- [x] 2.2 Make fixture export and replay behavior deterministic from the same source capture.

## 3. Verification

- [x] 3.1 Verify sanitized fixtures still decode against the current `:core:network` DTOs.
- [x] 3.2 Verify programmatic replay serves the sanitized responses locally.
- [x] 3.3 Verify no raw browser-session artifacts are committed.
- [x] 3.4 Verify the sanitization rules from 1.0 and all PII categories listed in the spec are redacted from committed fixtures.
