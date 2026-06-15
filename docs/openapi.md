# OpenAPI Contract Validation

The project's API contract lives at `api/subsloth.openapi.yaml`. Validation is done with **vacuum** — a fast, Spectral-compatible OpenAPI linter written in Go.

## Why vacuum instead of `org.openapi.generator`?

The project uses **handwritten `@Serializable` DTOs**, not generated ones. The `openApiValidate` Gradle task from `org.openapi.generator` was used solely as a spec validation gate — it never ran code generation.

Vacuum replaces it because:

| Factor | `./gradlew :core:network:openApiValidate` | `vacuum lint api/subsloth.openapi.yaml` |
|--------|---|---|
| Startup time | ~15–20s (cold), ~3–5s (warm) | ~100ms |
| Ruleset | 8 generator-specific rules | 50+ Spectral-compatible rules |
| OAS 3.1 support | Partial | Full |
| Runtime | JDK + full Gradle project config | Single Go binary |

Since we never run `openApiGenerate`, there is no parser-mismatch risk — vacuum's Go parser and the OpenAPI Generator's Swagger Parser never need to agree.

## Installation

Vacuum is included in the Nix flake shell (`flake.nix`). Just run `direnv allow` or `nix develop` and it's available.

For CI environments without Nix, install via:

```bash
# npm
npm i -g @quobix/vacuum

# curl (Linux amd64)
curl -fsSL -o "install_vacuum.sh" "https://quobix.com/scripts/install_vacuum.sh"
# After reviewing install_vacuum.sh, run:
sh "./install_vacuum.sh"

# Docker
docker run --rm -v $PWD:/work:ro dshanley/vacuum lint /work/api/subsloth.openapi.yaml
```

## Usage

### Validate the spec

```bash
vacuum lint api/subsloth.openapi.yaml
```

Exit code is non-zero on any error.

### See full details with code snippets

```bash
vacuum lint -d -s api/subsloth.openapi.yaml
```

### Error-only output

```bash
vacuum lint -d -e api/subsloth.openapi.yaml
```

### Interactive dashboard

```bash
vacuum dashboard api/subsloth.openapi.yaml
```

### CI integration

The CI workflow downloads the vacuum binary directly from GitHub and runs it inline (no Docker, no extra GitHub permissions):

```yaml
- name: Validate OpenAPI spec
  run: |
    curl -fsSL https://github.com/daveshanley/vacuum/releases/download/v0.26.5/vacuum_0.26.5_linux_x86_64.tar.gz \
      -o /tmp/vacuum.tar.gz
    tar xzf /tmp/vacuum.tar.gz -C /usr/local/bin vacuum
    vacuum lint api/subsloth.openapi.yaml --ruleset config/vacuum.yaml --fail-severity error
```

See `.github/workflows/ci.yml` for the current setup.

## Rust-based alternatives

Vacuum is written in Go. For those who prefer Rust:

| Tool | Language | Stars | Status |
|------|----------|-------|--------|
| [vacuum](https://github.com/daveshanley/vacuum) | Go | 5k+ | Mature, active |
| [refract](https://github.com/ilmu-org/refract) | Rust | 0 | Pre-release, experimental |

**refract** is a Rust-based Spectral-compatible linter started in April 2026, but it's brand new (0 stars, no releases) and not ready for production use. Vacuum is the current recommendation.
