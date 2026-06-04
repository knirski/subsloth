## 1. Explorer Scaffold

- [x] 1.1 Add a dev-only Stoplight Elements explorer entrypoint and wire it to load `api/subsloth.openapi.yaml`.
- [x] 1.2 Add local configuration for Media base URL and manual auth input without committing credentials.
- [x] 1.3 Add an easy source switch between live Media and local mock/replay responses.

## 2. Safety Boundaries

- [x] 2.1 Ensure the explorer is excluded from the production Android app runtime and release artifacts.
- [x] 2.2 Keep comments endpoints and other web-only Media flows out of the explorer.
- [x] 2.3 Keep the source switch local-only and backed by fixture-derived replay, not handwritten sample data.

## 3. Verification

- [ ] 3.1 Verify the explorer renders the current OpenAPI contract locally.
- [ ] 3.2 Verify no sensitive artifacts or credentials are committed as part of the explorer.
- [x] 3.3 Run the relevant local validation or preview command for the explorer entrypoint.
