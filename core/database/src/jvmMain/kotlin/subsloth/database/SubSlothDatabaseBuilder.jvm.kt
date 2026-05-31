package subsloth.database

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

actual fun createSubSlothDatabase(name: String): SubSlothDatabase = Room
    .databaseBuilder<SubSlothDatabase>(
        name = name,
        factory = SubSlothDatabaseCtor::initialize,
    ).setDriver(BundledSQLiteDriver())
    .build()
