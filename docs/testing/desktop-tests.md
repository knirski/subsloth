# Desktop Tests and Live Desktop E2E

Compose Desktop UI tests live in `desktopApp/src/test/kotlin/net/subsloth/` and
run on the JVM, but they need an X server (AWT), so on headless Linux run them
under Xvfb exactly like CI does:

```bash
xvfb-run -a ./gradlew :desktopApp:test
```

On Wayland/Sway desktops, XWayland or the running session also works; the test
JVM inherits the Gradle daemon's environment, so `DISPLAY` must be present when
the daemon is started. If a long-lived daemon predates your display variables:

```bash
./gradlew --stop
DISPLAY=:0 ./gradlew :desktopApp:test
```

## Live Desktop E2E (real backend)

`LiveDesktopE2ETest` drives the real desktop composition root
(`DesktopContainer` rooted at a temporary data directory) and the real nav host
against a live backend:

1. signs in through the production login form (base URL + credentials from the
   environment);
2. waits for the catalog sync and opens a show found through search, asserting
   the episode list renders;
3. resolves the first playable episode's stream through the desktop playback
   port and asserts it is an online source with a stream URL.

The class skips itself unless credentials are present, so CI never touches a
live backend and no secret is stored anywhere. Run it with a display available:

```bash
DISPLAY=:0 \
  SUBSLOTH_LOGIN=… SUBSLOTH_PASSWORD=… \
  SUBSLOTH_API_BASE_URL=https://…/api/v2/ \
  ./gradlew :desktopApp:test --tests '*LiveDesktopE2ETest*'
```

`SUBSLOTH_API_BASE_URL` must include the API version path (for example
`/api/v2/`). The stream-resolution step retries briefly: the upstream CDN
occasionally answers an otherwise valid API request with a bot challenge page
right after the catalog-sync burst, and a retry succeeds.

Desktop tests must never touch the real user profile: pass a temporary
directory to `DesktopContainer(dataDirOverride)` — preferences, the Room
database, and downloads all live under that directory.
