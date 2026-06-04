# Media API Explorer (Dev Only)

Local developer UI for browsing and manually testing Media API endpoints using [Stoplight Elements](https://github.com/stoplightio/elements).

## Quick Start

```bash
./serve.sh
# Open http://localhost:8123/dev-explorer/
```

## Features

- Browsable API reference rendered from `api/subsloth.openapi.yaml`
- "Try It" panel for manual request execution
- Base URL and auth token configuration (stored in localStorage)
- Live vs local mock source switching

## Source Switching

| Source | Description |
|--------|-------------|
| Live Media API | Sends requests to the configured base URL |
| Local Mock | Serves responses from sanitized capture fixtures |

For mock mode, start the mock server in a separate terminal:

```bash
./mock-server.sh
```

Then switch the source dropdown to "Local Mock" in the explorer UI.

## Files

| File | Purpose |
|------|---------|
| `index.html` | Explorer entrypoint (Stoplight Elements Web Component) |
| `serve.sh` | Starts a local HTTP server from the project root |
| `mock-server.sh` | Serves sanitized capture fixtures on port 8124 |

## Configuration

The explorer stores settings in localStorage (never committed):

- **Base URL** — Target Media API endpoint
- **Auth Token** — Bearer token or API key for authenticated requests
- **Source** — Live or Mock selection

## Safety

- Excluded from the production Android app build
- No credentials or signed URLs are committed
- Comments endpoints are not included in the OpenAPI contract
- Mock fixtures are sanitized per `scripts/capture/sanitization-rules.json`
