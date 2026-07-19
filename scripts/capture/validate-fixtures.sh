#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────────────
# Automated offline fixture/schema validation pipeline.
#
# Usage:
#   ./scripts/capture/validate-fixtures.sh              # capture + validate all fixtures
#   ./scripts/capture/validate-fixtures.sh --validate    # offline validation only (skip capture)
#   ./scripts/capture/validate-fixtures.sh --capture     # capture only (skip validation)
#
# Credentials are read from SUBSLOTH_LOGIN and SUBSLOTH_PASSWORD environment
# variables. The API base URL is read from SUBSLOTH_URL (falls back to the
# default media-mirror.tv endpoint). These are the same env vars used by
# ApiLiveDriftTest.
# ──────────────────────────────────────────────────────────────────────────────
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
MODE="${1:-all}"

case "$MODE" in
  --validate|-v)
    echo "=== Offline fixture validation ==="
    cd "$ROOT"
    ./gradlew :testing:api-contract:validateFixtures
    ;;
  --capture|-c)
    if [ -z "${SUBSLOTH_LOGIN:-}" ] || [ -z "${SUBSLOTH_PASSWORD:-}" ]; then
      echo "ERROR: SUBSLOTH_LOGIN and SUBSLOTH_PASSWORD must be set for capture" >&2
      exit 1
    fi
    echo "=== Capture fresh native API fixtures ==="
    cd "$ROOT"
    ./gradlew :testing:api-contract:captureApi
    ;;
  all|*)
    if [ -z "${SUBSLOTH_LOGIN:-}" ] || [ -z "${SUBSLOTH_PASSWORD:-}" ]; then
      echo "WARNING: SUBSLOTH_LOGIN/SUBSLOTH_PASSWORD not set -- skipping capture, running validation only"
      exec "$0" --validate
    fi
    echo "=== Full pipeline: capture + validate ==="
    cd "$ROOT"
    ./gradlew :testing:api-contract:captureAndValidate
    ;;
esac

echo "=== Fixture validation complete ==="
