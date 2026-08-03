## Context

GitHub Pages publishes the Web distribution as static files. The current Web
entrypoint sets `ClientConfig.useMock = true`, but still renders `SessionGate`,
whose login path can construct `LoginViewModel`. The Wasm `CredentialStore`
implementation uses Web Crypto to encrypt values but stores both the ciphertext
and the encryption key in `localStorage`; this is not an acceptable credential
boundary for a public demo site.

The repository already has a mock Ktor client and shared feature screens. The
smallest safe design is to make the deployment mode an explicit composition-root
decision: Demo mode renders the mock-backed experience without constructing
authentication or credential storage. An authenticated mode is deliberately not
implemented until a later change supplies a browser-compatible API/auth boundary.

## Goals

- Make the Pages deployment unambiguously a demo at runtime and in documentation.
- Keep demo data deterministic and offline from the live Media service.
- Prevent demo startup from touching credential persistence.
- Provide executable browser evidence for these properties.
- Leave a clear seam for a future authenticated Web composition root.

## Non-goals

- Do not add a proxy, backend, server-side session, or browser login flow.
- Do not use an environment variable in the Pages build to turn Demo mode into
  a live mode accidentally.
- Do not redesign shared feature ViewModels or the database layer.

## Decisions

### Runtime mode is selected by the Web composition root

The Wasm entrypoint owns the deployment mode. The Pages build selects Demo mode
at compile/runtime startup, and the mode is not user-configurable. The demo
composition sets the mock transport before any feature ViewModel is created and
does not construct `RootContainerViewModel`, `SessionGate`, `LoginViewModel`, or
`CredentialStore`.

The demo catalog/navigation host receives the existing mock-backed dependencies
through a small Web-owned composition seam. If a screen is not yet supported by
the mock data, it remains an explicitly labelled demo state rather than falling
back to a live request.

### Demo mode is visible and accessible

The root Web surface shows a persistent `Demo mode` label with supporting text:
the site uses sample data and does not accept or store Media credentials. The
label is rendered as normal Compose content with an accessible text
description, not only as a document title or developer-console message.

### Credential persistence is prohibited in Demo mode

Demo startup must not instantiate the Wasm `CredentialStore`. The test contract
also checks that the known credential keys are absent before and after startup.
The existing Web Crypto implementation may remain available for a future
non-Demo composition, but it is not part of the Pages runtime and must not be
treated as secure storage for a public Web deployment.

### Promotion requires a separate authenticated Web change

The documentation and readiness matrix state that promotion requires an
approved browser authentication/API design, API or proxy CORS support,
cross-origin isolation where OPFS persistence is claimed, safe credential
handling, meaningful browser tests, and a deployed acceptance check. This
change records those gates but does not satisfy them.

## Testing strategy

- Add Wasm browser tests that load the Pages composition and assert the Demo mode
  label is present.
- Assert startup does not call the credential store and leaves the known
  credential keys absent.
- Assert the mock client handles the catalog request and no live base URL is
  used by the Demo composition.
- Keep existing JVM and shared tests unchanged, then run the Web browser task
  and the repository's required static/compile checks.

## Rollout

Deploy the changed Web distribution through the existing Pages workflow. The
workflow and README continue to call it a stateless demo. A future change may
add an authenticated Web target separately, without changing the safety
properties of the Pages build.
