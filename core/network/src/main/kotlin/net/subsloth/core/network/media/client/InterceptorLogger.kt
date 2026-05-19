package net.subsloth.core.network.media.client

import android.util.Log

/**
 * Thin wrapper around [android.util.Log] that gracefully degrades in unit
 * test environments where the Android SDK stubs throw [RuntimeException].
 *
 * Under the JVM (Robolectric-less unit tests), bridge calls to [println].
 * Under Android runtime, delegate directly to [android.util.Log].
 */
internal object InterceptorLogger {
    fun v(tag: String, msg: String) = log(tag, msg) { Log.v(it, msg) }

    fun w(tag: String, msg: String) = log(tag, msg) { Log.w(it, msg) }

    fun e(tag: String, msg: String) = log(tag, msg) { Log.e(it, msg) }

    private fun log(tag: String, msg: String, androidLog: (String) -> Unit) {
        try {
            androidLog(tag)
        } catch (_: RuntimeException) {
            // android.util.Log throws "Stub!" on JVM without Robolectric
            println("$tag: $msg")
        }
    }
}
