# Secret & Artifact Scanning Policy

## Scope

This policy defines what constitutes a secret or sensitive artifact in the subsloth repository and how scanning must prevent accidental commits.

## Secrets That MUST Be Scanned

The following patterns MUST be caught by pre-commit, CI, or repository scanning:

| Category | Pattern | Example |
|----------|---------|---------|
| Media credentials | `SUBSLOTH_LOGIN`, `SUBSLOTH_PASSWORD` env values, or Basic auth tokens in clear text | `Basic dmFs...` |
| Basic auth headers | `Authorization: Basic` followed by Base64 payload in committed files | `Authorization: Basic base64string` |
| Signed media URLs | Stream, download, or subtitle URLs containing `?exp=`, `?signature=`, `?sig=`, or `?token=` | `https://cdn.media.tv/streams/...?exp=1700000000&sig=abc` |
| Signed artwork URLs | Poster, backdrop, fanart URLs with expiring signature parameters | Same pattern as media URLs |
| Cookies | `Cookie` or `Set-Cookie` headers, session tokens, or any cookie-like `key=value` pairs in config or log files | `session=abc123; token=xyz` |
| Browser traces | `.playwright-cli/` directory contents, protocol monitor logs, CDP traces | Any file under `.playwright-cli/` |
| HAR files | HTTP Archive files capturing full request/response bodies | `*.har` or `*.har.gz` |
| Screenshots | Authenticated app screenshots, browser screenshots, or UI state captures | Files under `screenshots/` or capture tooling directories |
| Signed APK materials | Keystore files, signing keys, or APK signing certificates | `*.jks`, `*.keystore`, `*.pk8`, `*.pem` |
| Token files | API tokens, PATs, or OAuth refresh tokens in any format | `ghp_*`, `github_pat_*` |

## Files That MUST Be Ignored

The following entries MUST be in `.gitignore` to prevent accidental commits:

```
# Secrets and credentials
*.jks
*.keystore
local.properties

# Authenticated browser traces
.playwright-cli/
*.har
*.har.gz

# Authenticated screenshots
screenshots/

# Log files
*.log
```

## Scanning Implementation

### Pre-commit (Recommended)

Use `git-secrets`, `trufflehog`, or `gitleaks` to scan staged changes:

```bash
# Example with gitleaks
gitleaks detect --source . --verbose
```

### CI Scan

In CI workflows, run scanning as an early job that fails fast before any build steps:

```yaml
- name: Secret scanning
  run: gitleaks detect --source . --verbose --no-git
```

## Handling Findings

1. If a secret is detected in a commit that has already been pushed:
   - Rotate the exposed credential immediately.
   - Force-push a corrected history (with team coordination).
   - File a security incident if the secret was production-facing.

2. If a secret is detected in a staged commit:
   - Use `git restore --staged <file>` to unstage and replace with a sanitised version.
   - Use placeholder values or environment variable references instead of hardcoded secrets.

3. False positives (e.g. test fixture URLs with `exp=` that are intentionally sanitised):
   - Add a `.gitleaks.toml` allowlist entry scoped to the specific file and pattern.

## Sanitised Fixture Policy

All committed API fixtures must pass through the export pipeline which applies `sanitization-rules.json` before writing. Each fixture must:

- Contain no Media credentials, auth headers, or session data
- Replace signed media URLs with plausible obfuscated placeholder domains on non-existent hosts using the `.invalid` IETF-reserved TLD
- Contain no HAR files, browser logs, committed screenshot captures, or `.playwright-cli/` artifacts
- Use `EXPIRED` or `0000000000` in query parameters if URL shape includes
  signature-like parameters

Fixtures live in two buckets:
- **Native contract** — `testing/api-contract/src/main/resources/media/`
- **Web-only discovery** — `testing/api-contract/src/main/resources/media/web-discovery/`

`core/network/src/test/resources/media/` is a bridge directory — the source-of-truth fixtures are in `:testing:api-contract`.

## References

- `.gitignore` at repository root
- `docs/api-discovery.md` for credential handling during live discovery
- `scripts/capture/sanitization-rules.json` for the authoritative sanitisation rules
- `testing/api-contract/src/main/resources/media/README.md` for fixture and replay layout
- `testing/api-contract/src/main/kotlin/net/subsloth/testing/contract/Endpoint.kt` for the sealed `Endpoint` type that drives fixture categorisation
