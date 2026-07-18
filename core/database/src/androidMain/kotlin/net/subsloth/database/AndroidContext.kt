package net.subsloth.database

import android.annotation.SuppressLint
import android.content.Context

/**
 * Process-wide Android [Context] holder.
 *
 * Initialised once during [android.app.Application.onCreate] so that
 * the Android Room 3 database builder (which wraps
 * `androidx.room.Room.databaseBuilder`) can obtain the app context.
 *
 * Must be set before any [createSubSlothDatabase] call on Android.
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
