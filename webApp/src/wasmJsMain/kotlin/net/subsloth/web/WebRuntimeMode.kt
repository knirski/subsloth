package net.subsloth.web

/** Runtime modes supported by the currently deployed Web application. */
enum class WebRuntimeMode {
    Demo,
}

internal const val DEMO_CREDENTIAL_DATA_KEY = "subsloth_credentials_data"
internal const val DEMO_CREDENTIAL_KEY_KEY = "subsloth_credentials_key"

internal const val DEMO_BANNER_TEXT =
    "Demo mode: sample data only; Media credentials are not requested or stored."
