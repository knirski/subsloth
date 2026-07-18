package net.subsloth.database

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

/**
 * Android actual of [createSubSlothDatabase].
 *
 * Uses the Android-native Room 3 [Room.databaseBuilder] that requires a
 * [android.content.Context] — obtained from [AndroidContext], which must be
 * initialised during [android.app.Application.onCreate].
 *
 * Android Room 3's `databaseBuilder(Context, String, Function0)` is a
 * regular (non-inline) instance method, so it avoids the inline-function
 * expansion issue that occurs with the common `reified` variant.
 */
actual fun createSubSlothDatabase(name: String): SubSlothDatabase = Room.databaseBuilder(
    context = AndroidContext.requireApplicationContext(),
    name = name,
    factory = SubSlothDatabaseCtor::initialize,
).setDriver(BundledSQLiteDriver())
    .fallbackToDestructiveMigration()
    .build()
