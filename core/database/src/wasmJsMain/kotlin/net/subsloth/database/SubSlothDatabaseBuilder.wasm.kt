@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package net.subsloth.database

import androidx.room3.Room
import org.w3c.dom.Worker

private val worker: Worker =
    js("""new Worker(new URL("sqlite-wasm-worker/worker.js", import.meta.url), { type: "module" })""")

actual fun createSubSlothDatabase(name: String): SubSlothDatabase = Room
    .databaseBuilder<SubSlothDatabase>(
        name = name,
        factory = SubSlothDatabaseCtor::initialize,
    )
    .setDriver(SubSlothSqliteDriver(worker))
    .fallbackToDestructiveMigration()
    .build()
