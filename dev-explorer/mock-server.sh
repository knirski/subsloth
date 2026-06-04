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
python3 -c "
import http.server
import socketserver

class CORSHandler(http.server.SimpleHTTPRequestHandler):
    def end_headers(self):
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', '*')
        super().end_headers()

    def do_OPTIONS(self):
        self.send_response(200)
        self.end_headers()

with socketserver.TCPServer(('127.0.0.1', $PORT), CORSHandler) as httpd:
    httpd.serve_forever()
"
