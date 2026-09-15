@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package net.subsloth.database

import androidx.room3.Room
import androidx.sqlite.driver.web.WebWorkerSQLiteDriver
import org.w3c.dom.Worker

private val worker: Worker =
    js("""new Worker(new URL("sqlite-wasm-worker/worker.js", import.meta.url), { type: "module" })""")

actual fun createSubSlothDatabase(name: String): SubSlothDatabase = Room
    .databaseBuilder<SubSlothDatabase>(
        name = name,
        factory = SubSlothDatabaseCtor::initialize,
    )
    .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
    .setDriver(WebWorkerSQLiteDriver(worker))
    .fallbackToDestructiveMigration()
    .build()
