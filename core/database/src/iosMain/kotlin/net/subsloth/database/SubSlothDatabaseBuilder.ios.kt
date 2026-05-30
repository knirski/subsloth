package net.subsloth.database

import androidx.room3.Room
import androidx.sqlite.driver.NativeSQLiteDriver

actual fun createSubSlothDatabase(name: String): SubSlothDatabase =
    Room
        .databaseBuilder<SubSlothDatabase>(
            name = name,
            factory = SubSlothDatabaseCtor::initialize,
        ).setDriver(NativeSQLiteDriver())
        .build()
