package net.subsloth.preferences

import android.annotation.SuppressLint
import android.content.Context

/**
 * Process-wide Android [Context] holder.
 *
 * Initialised once during [android.app.Application.onCreate] so that the
 * Android actuals in this module (Keystore-backed [CredentialStore],
 * [createDataStorePreferences]) can obtain the app context.
 *
 * Mirrors `net.subsloth.database.AndroidContext` in `:core:database`, which
 * solves the identical problem for the Room database builder. Each module
 * keeps its own holder rather than sharing one, to avoid an inter-module
 * dependency that would violate the core module architecture boundaries.
 *
 * Must be set before any Android actual in this module is used.
 */
@SuppressLint("StaticFieldLeak")
object AndroidContext {
    @Volatile
    private var applicationContext: Context? = null

    /**
     * Returns the initialized application context.
     *
     * @throws IllegalStateException if [init] has not been called.
     */
    fun requireApplicationContext(): Context = checkNotNull(applicationContext) {
        "AndroidContext not initialized. Call AndroidContext.init(this) in Application.onCreate()."
    }

    fun init(context: Context) {
        applicationContext = context.applicationContext
    }

    /**
     * Clears the stored context reference.
     *
     * Intended for use in test teardown to prevent stale context references
     * from leaking between test cases.
     */
    fun reset() {
        applicationContext = null
    }
}
