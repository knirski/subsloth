package net.subsloth.database

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

/**
 * JVM (desktop) actual of [createSubSlothDatabase].
 *
 * Uses Room 3's inline `databaseBuilder<T>(name, factory)` which expands to
 * `RoomDatabase.Builder(KClass, String, Function0)` — a constructor that
 * exists on `room3-runtime-jvm` (used on desktop JVM).
 *
 * This implementation is NOT used on Android — see [androidMain]'s
 * [SubSlothDatabaseBuilder.android.kt] for the Android variant.
 */
actual fun createSubSlothDatabase(name: String): SubSlothDatabase = Room
    .databaseBuilder<SubSlothDatabase>(
        name = name,
        factory = SubSlothDatabaseCtor::initialize,
    ).setDriver(BundledSQLiteDriver())
    .fallbackToDestructiveMigration()
    .build()
