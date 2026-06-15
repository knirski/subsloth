## 1. Session domain type

- [ ] 1.1 Create `Session` sealed interface in `:core:domain/port/`:
  `data object Anonymous : Session` and `data class Authenticated(val userId: String, val openedAtEpochSeconds: Long, val credentials: Credentials) : Session`.
- [ ] 1.2 Add `SessionPort` interface with `state: StateFlow<Session>`, `current(): Session`, `open(credentials: Credentials): Outcome<Unit>`, `close(): Outcome<Unit>`, `invalidate(): Outcome<Unit>`.

## 2. Default in-memory implementation

- [ ] 2.1 Create `InMemorySessionState` in `:core:domain` that implements `SessionPort` using a `MutableStateFlow<Session>`. Default state is `Anonymous`. `open` transitions to `Authenticated`; `invalidate` transitions back to `Anonymous`; `close` is an alias for `invalidate`.
- [ ] 2.2 Add `InMemorySessionStateTest` covering all four transitions.

## 3. Verify

- [ ] 3.1 `./gradlew :core:domain:compileKotlinJvm :core:domain:jvmTest spotlessCheck detekt`
- [ ] 3.2 `openspec validate feature-session-port --strict`
