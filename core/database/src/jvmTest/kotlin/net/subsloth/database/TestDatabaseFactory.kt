package net.subsloth.database

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

suspend fun createTestDatabase(): SubSlothDatabase = Room.inMemoryDatabaseBuilder<SubSlothDatabase>(
    factory = SubSlothDatabaseCtor::initialize,
).setDriver(BundledSQLiteDriver())
    .build()
