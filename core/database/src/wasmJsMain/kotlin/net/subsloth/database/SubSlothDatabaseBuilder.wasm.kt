@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package net.subsloth.database

import androidx.room3.Room
import androidx.sqlite.driver.web.WebWorkerSQLiteDriver
import org.w3c.dom.Worker

actual fun createSubSlothDatabase(name: String): SubSlothDatabase = Room
    .inMemoryDatabaseBuilder<SubSlothDatabase>()
    .setDriver(WebWorkerSQLiteDriver(jsWorker()))
    .build()

private fun jsWorker(): Worker = js("""new Worker(new URL("sqlite-wasm-worker/worker.js", import.meta.url))""")
