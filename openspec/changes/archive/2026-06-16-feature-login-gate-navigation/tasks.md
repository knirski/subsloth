## 1. :core:ui module

- [ ] 1.1 Verify `:core:ui` exists in the build; if not, create it as a
  thin Compose-Multiplatform module that depends on `:core:domain`.
- [ ] 1.2 Add `SessionGate` composable in `:core:ui` with signature
  `fun SessionGate(sessionPort: SessionPort, login: @Composable () -> Unit, authenticated: @Composable () -> Unit)`.
  Observes `sessionPort.state` and routes to `login()` or `authenticated()`.

## 2. feature/auth rewiring

- [ ] 2.1 Replace `LoginViewModel`'s `hasStoredCredentials` /
  `validateCredentials` lambdas with direct dependencies on
  `SessionPort` and `CredentialsPort`. Keep the `LoginUiState` sealed
  interface and the `Loading → LoggedIn` flow.
- [ ] 2.2 Update `LoginViewModelTest` to inject a fake `SessionPort`
  and `CredentialsPort`; the state-machine assertions remain.

## 3. App shells

- [ ] 3.1 In `androidApp` `MainActivity` root composable, wrap the
  `AppRoot` `NavHost` in `SessionGate` (with `login = LoginScreen`).
- [ ] 3.2 In `desktopApp` `App` composable, do the same.
- [ ] 3.3 In `webApp` `App` composable, do the same.

## 4. Verify

- [ ] 4.1 `./gradlew spotlessApply spotlessCheck detekt :core:ui:compileKotlinJvm :feature:auth:compileKotlinJvm :feature:auth:jvmTest :androidApp:assembleDebug :desktopApp:assembleLinux :webApp:assembleWasmJs`
- [ ] 4.2 `openspec validate feature-login-gate-navigation --strict`
