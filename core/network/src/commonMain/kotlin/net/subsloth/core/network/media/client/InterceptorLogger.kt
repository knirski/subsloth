package net.subsloth.core.network.media.client

import co.touchlab.kermit.Logger
import io.ktor.client.plugins.logging.Logger as KtorLogger

private val log = Logger.withTag("Ktor")

/**
 * KMP-compatible logger backed by Kermit.
 * Implements Ktor's [KtorLogger] so it can be used as a logging plugin destination.
 */
internal object InterceptorLogger : KtorLogger {
    fun v(tag: String, msg: String) = Logger.withTag(tag).v { msg }

    fun w(tag: String, msg: String) = Logger.withTag(tag).w { msg }

    fun e(tag: String, msg: String) = Logger.withTag(tag).e { msg }

    override fun log(message: String) {
        log.i { message }
    }
}
