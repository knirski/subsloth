package net.subsloth.web

import net.subsloth.core.ui.AppNavKey
import net.subsloth.core.ui.CatalogKey

/** Runtime modes supported by the currently deployed Web application. */
enum class WebRuntimeMode {
    /** Fixture-backed demo (GitHub Pages): mock transport, no credentials. */
    Demo,

    /** Production tier: real adapters behind the build-injected API base URL. */
    Production,
}

internal const val DEMO_CREDENTIAL_DATA_KEY = "subsloth_credentials_data"
internal const val DEMO_CREDENTIAL_KEY_KEY = "subsloth_credentials_key"

internal const val DEMO_BANNER_TEXT =
    "Demo mode: sample data only; Media credentials are not requested or stored."

internal const val PRODUCTION_BANNER_TEXT = ""

/**
 * Selects the runtime tier from the build-injected API base URL: a
 * non-empty `SUBSLOTH_API_BASE_URL` (set by CI from the repo secret for
 * production builds) selects [WebRuntimeMode.Production] with a real
 * [WebProductionContainer]; otherwise the demo tier with the mock
 * transport is used (GitHub Pages default).
 */
internal fun createWebApp(): WebApp {
    val apiBaseUrl = subslothApiBaseUrlEnv()
    return if (apiBaseUrl.isEmpty()) {
        val runtime = createWebDemoRuntime()
        WebApp(
            runtime = runtime,
            mode = WebRuntimeMode.Demo,
            startDestination = CatalogKey,
            bannerText = DEMO_BANNER_TEXT,
            showBanner = true,
        )
    } else {
        WebApp(
            runtime = WebProductionContainer(),
            mode = WebRuntimeMode.Production,
            startDestination = CatalogKey,
            bannerText = PRODUCTION_BANNER_TEXT,
            showBanner = false,
        )
    }
}

/** Selected runtime plus the presentation bits [Main.kt] needs. */
internal class WebApp(
    val runtime: WebRuntime,
    val mode: WebRuntimeMode,
    val startDestination: AppNavKey,
    val bannerText: String,
    val showBanner: Boolean,
) {
    fun close() = runtime.close()
}
