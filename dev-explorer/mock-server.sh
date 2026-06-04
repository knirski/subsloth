#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
FIXTURE_DIR="$PROJECT_ROOT/testing/api-contract/src/main/resources/media"
PORT="${PORT:-8124}"

if [ ! -d "$FIXTURE_DIR" ]; then
  echo "Error: Fixture directory not found at $FIXTURE_DIR"
  echo "Run the capture pipeline first:"
  echo "  ./gradlew :testing:api-contract:captureApi -Pemail=... -Ppassword=..."
  exit 1
fi

echo "Starting Subsloth Mock Server..."
echo "Serving fixtures from: $FIXTURE_DIR"
echo "Listening on: http://localhost:${PORT}"
echo "Press Ctrl+C to stop"
echo ""

cd "$FIXTURE_DIR"
python3 -m http.server "$PORT"
