package subsloth.network

/**
 * Provides the Kodi-style User-Agent string for Media API requests.
 *
 * The value is derived from the Kodi plugin's documented identity for
 * normal authenticated traffic. The User-Agent is not derived from
 * WebView/Chrome, and no headless, automation, test, OkHttp/Dalvik,
 * emulator/debug, or Android-browser strings are sent for production traffic.
 */
object KodiUserAgentProvider {
    /** Kodi-compatible User-Agent for the Media API. */
    const val USER_AGENT = "Kodi/21.0 (SubSloth; Android)"
}
