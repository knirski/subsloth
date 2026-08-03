## 1. Define the Web runtime mode

- [x] 1.1 Add a Web-owned runtime mode contract with `Demo` as the only mode
  available to the GitHub Pages composition root.
- [x] 1.2 Make the Pages entrypoint select Demo mode before constructing feature
  ViewModels and keep the mock client mandatory.
- [x] 1.3 Remove the Demo composition's dependency on `SessionGate`, login UI,
  `LoginViewModel`, and credential storage while preserving supported mock-backed
  navigation.
- [x] 1.4 Add a persistent accessible Demo mode label stating that sample data is
  used and credentials are not requested or stored.

## 2. Protect the Demo credential boundary

- [x] 2.1 Add a browser-testable seam proving the Demo composition never
  constructs the Wasm `CredentialStore`.
- [x] 2.2 Add a browser test that seeds the known Web credential keys and proves
  Demo startup does not read, decrypt, refresh, or use them.
- [x] 2.3 Ensure Demo mode makes no live API request and cannot be switched to
  live mode through URL parameters, browser storage, or a public runtime toggle.

## 3. Browser and deployment verification

- [x] 3.1 Add meaningful Wasm browser tests for Demo labelling, mock-backed
  startup, and credential-storage absence.
- [x] 3.2 Update the Pages workflow and Web deployment documentation to call the
  result a stateless Demo and to state that no authenticated production Web
  runtime is deployed.
- [x] 3.3 Update `docs/readiness/platform-support-matrix.md` with evidence for
  the Demo label and credential boundary, leaving authenticated promotion gates
  open.

## 4. Validation

- [x] 4.1 `openspec validate define-web-runtime-tier --strict`
- [x] 4.2 `./gradlew spotlessApply spotlessCheck detekt`
- [x] 4.3 `./gradlew :webApp:wasmJsBrowserTest`
- [x] 4.4 `./gradlew :core:model:compileKotlinWasmJs :core:domain:compileKotlinWasmJs`
- [x] 4.5 `./gradlew test`
- [x] 4.6 `openspec validate --all --strict`
