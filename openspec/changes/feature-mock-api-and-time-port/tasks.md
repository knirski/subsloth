## 1. CurrentTimePort

- [ ] 1.1 Add `CurrentTimePort` to `:core:domain/port/`. Keep `ClockPort` as a typealias for one release, then remove the alias.
- [ ] 1.2 Add `fun millisNow(): Long` to `CurrentTimePort`. Implementations call `System.currentTimeMillis()`.
- [ ] 1.3 Update `:androidApp` to use the new port name (rename is mechanical; the existing shell impl works as-is).
- [ ] 1.4 Add `CurrentTimePortTest` to `:core:domain/jvmTest` (parameterised over a fake clock).

## 2. MockApi module

- [ ] 2.1 Create `:testing:mock-api` module with the same `subsloth.kmp.library` convention as the other `:testing:*` modules.
- [ ] 2.2 Implement `MockApi` as an `object` with seed data:
  - 10 movies (`MovieSummary`) across 4 genres
  - 5 shows (`ShowSummary`) with 4 episodes each
  - 1 in-progress download per show for variety
  - 3 library items: 1 favorite, 1 watch-later, 1 history
- [ ] 2.3 Implement all relevant domain ports. For each, return `Result.success(...)` in steady state; support `expireSession()` so the next call returns `AuthError.SessionExpired` or `NetworkError.HttpError(401, ...)`.
- [ ] 2.4 The `login(email, password)` method accepts any non-empty pair and stores the user. `logout()` clears state.
- [ ] 2.5 Add `MockApiTest` covering: seed catalog is non-empty, library mutations are observable, session expiry round-trips.

## 3. Verify

- [ ] 3.1 `./gradlew spotlessApply spotlessCheck detekt :core:domain:compileKotlinJvm :core:domain:jvmTest :testing:mock-api:compileKotlinJvm :testing:mock-api:jvmTest test`
- [ ] 3.2 `openspec validate feature-mock-api-and-time-port --strict`
