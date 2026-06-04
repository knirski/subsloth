#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
PORT="${PORT:-8123}"

echo "Starting Subsloth Media API Explorer..."
echo "Open http://localhost:${PORT}/dev-explorer/ in your browser"
echo "Press Ctrl+C to stop"
echo ""

cd "$PROJECT_ROOT"
python3 -m http.server "$PORT"
