package subsloth.core.network.media.client

import io.ktor.client.plugins.logging.Logger as KtorLogger

/**
 * KMP-compatible logger that uses [println] for all platforms.
 * Implements Ktor's [KtorLogger] so it can be used as a logging plugin destination.
 */
internal object InterceptorLogger : KtorLogger {
    fun v(tag: String, msg: String) = log("V", tag, msg)

    fun w(tag: String, msg: String) = log("W", tag, msg)

    fun e(tag: String, msg: String) = log("E", tag, msg)

    private fun log(level: String, tag: String, msg: String) {
        println("$level/$tag: $msg")
    }

    override fun log(message: String) {
        println(message)
    }
}
