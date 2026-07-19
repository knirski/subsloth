@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package net.subsloth.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8Array
import org.w3c.dom.MessageEvent
import org.w3c.dom.Worker
import org.w3c.dom.get
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// ---------------------------------------------------------------------------
// Fixed SQLiteDriver for wasmJs — closes the "WebWorkerSQLiteDriver nullable
// bug" documented at
//   github.com/linhvnguyen9/room3-sqlite-web-nullable-npe-repro
//
// ## Root cause
//
// The upstream `StatementResult` stores a flat `columnTypes: IntArray`
// populated from the FIRST row of the worker's `step` response.  All
// subsequent rows reuse these cached types.  When row 1 has a non-null value
// in a nullable column and row 2 has null:
//
//   [row 1]  value="hello"   → columnTypes[0] = TEXT (3)
//   [row 2]  value=null      → columnTypes[0] still = TEXT from row 1
//                              → StatementResult.getCellType() returns TEXT
//                              → statement.isNull()       returns false
//                              → Room calls getText()
//                              → `as String` on null JsAny  → NPE
//
// ## Fix
//
// Our worker sends `columnTypes` as `Array<Array<number>>` (row-major), and
// every method on the statement checks the current row's actual type.
// ---------------------------------------------------------------------------

/** @see [androidx.sqlite.SQLITE_DATA_INTEGER] */
private const val TYPE_INTEGER = 1

/** @see [androidx.sqlite.SQLITE_DATA_FLOAT] */
private const val TYPE_FLOAT = 2

/** @see [androidx.sqlite.SQLITE_DATA_TEXT] */
private const val TYPE_TEXT = 3

/** @see [androidx.sqlite.SQLITE_DATA_BLOB] */
private const val TYPE_BLOB = 4

/** @see [androidx.sqlite.SQLITE_DATA_NULL] */
private const val TYPE_NULL = 5

/**
 * Custom [SQLiteDriver] for wasmJs that correctly handles per-row column
 * types, avoiding the NPE documented in the upstream nullable bug.
 *
 * The message protocol is identical to `WebWorkerSQLiteDriver` except that
 * the `step` response carries `columnTypes` as `Array<Array<number>>`
 * (row-major) instead of `Array<number>`.
 */
class SubSlothSqliteDriver(private val worker: Worker) : SQLiteDriver {

    /** Pending response callbacks keyed by message id. */
    private val pending = mutableMapOf<Long, (dynamic) -> Unit>()
    private var nextId = 1L

    init {
        worker.onmessage = { event: MessageEvent ->
            val resp = event.data as dynamic
            val id = resp.id as Long
            pending.remove(id)?.invoke(resp)
        }
    }

    override val hasConnectionPool: Boolean get() = false

    override suspend fun open(fileName: String): SQLiteConnection {
        val resp = request("open", js("{ fileName: fileName }"))
        val dbId: Long = resp.data.databaseId as Long
        return Connection(dbId)
    }

    // ---- suspend request/reply ------------------------------------------

    private suspend fun request(cmd: String, payload: dynamic): dynamic {
        val id = nextId++
        return suspendCancellableCoroutine { cont ->
            pending[id] = { resp ->
                if (resp.error != null) {
                    cont.resumeWithException(
                        SubSlothSqliteException("$cmd: ${resp.error}"),
                    )
                } else {
                    cont.resume(resp)
                }
            }
            val msg = js("{ id: id, data: Object.assign({ cmd: cmd }, payload) }")
            worker.postMessage(msg)
        }
    }

    /** Blocking variant — only safe inside non-suspend step()/close(). */
    private fun blockingRequest(cmd: String, payload: dynamic): dynamic = runBlocking { request(cmd, payload) }

    // ---- Connection -----------------------------------------------------

    private inner class Connection(private val databaseId: Long) : SQLiteConnection {

        override val inTransaction: Boolean get() = false

        override suspend fun prepare(sql: String): SQLiteStatement {
            val resp = request(
                "prepare",
                js("{ databaseId: databaseId, sql: sql }"),
            )
            val stmtId = resp.data.statementId as Long
            val paramCount = (resp.data.parameterCount as Number).toInt()
            val colNames: List<String> =
                (resp.data.columnNames as Array<*>).map { it as String }
            return Statement(stmtId, paramCount, colNames)
        }

        override fun close() {
            // Resources released via Statement.close()
        }
    }

    // ---- Statement ------------------------------------------------------

    private inner class Statement(
        private val statementId: Long,
        override val parameterCount: Int,
        override val columnNames: List<String>,
    ) : SQLiteStatement {

        /** Pending bindings accumulated via typed bind* calls. */
        private val pendingBindings = mutableMapOf<Int, Any?>()

        /** Eagerly fetched rows + per-row types from the first step(). */
        private var rows: Array<Array<Any?>> = emptyArray()
        private var rowTypes: Array<IntArray> = emptyArray()
        private var pos = -1

        // ---- step / reset / clear ---------------------------------------

        override fun step(): Boolean {
            if (pos == -1) {
                val resp = blockingRequest("step", buildPayload())
                parseRowsAndTypes(resp.data)
            }
            pos++
            return pos < rows.size
        }

        override fun reset() {
            pos = -1
            rows = emptyArray()
            rowTypes = emptyArray()
        }

        override fun clearBindings() {
            pendingBindings.clear()
        }

        // ---- typed bind helpers -----------------------------------------

        override fun bindText(index: Int, value: String) {
            pendingBindings[index] = value
        }
        override fun bindLong(index: Int, value: Long) {
            pendingBindings[index] = value
        }
        override fun bindDouble(index: Int, value: Double) {
            pendingBindings[index] = value
        }
        override fun bindFloat(index: Int, value: Float) {
            pendingBindings[index] = value.toDouble()
        }
        override fun bindInt(index: Int, value: Int) {
            pendingBindings[index] = value.toLong()
        }
        override fun bindBoolean(index: Int, value: Boolean) {
            pendingBindings[index] = if (value) 1L else 0L
        }
        override fun bindBlob(index: Int, value: ByteArray) {
            pendingBindings[index] = value
        }
        override fun bindNull(index: Int) {
            pendingBindings[index] = null
        }

        // ---- row getters (all null-safe) ---------------------------------

        override fun getText(index: Int): String {
            assertRow()
            val v = rows[pos][index]
            return v as? String ?: ""
        }

        override fun getLong(index: Int): Long {
            assertRow()
            val v = rows[pos][index]
            return (v as? Number)?.toLong() ?: 0L
        }

        override fun getDouble(index: Int): Double {
            assertRow()
            val v = rows[pos][index]
            return (v as? Number)?.toDouble() ?: 0.0
        }

        override fun getFloat(index: Int): Float = getDouble(index).toFloat()
        override fun getInt(index: Int): Int = getLong(index).toInt()
        override fun getBoolean(index: Int): Boolean = getLong(index) != 0L

        override fun getBlob(index: Int): ByteArray {
            assertRow()
            val v = rows[pos][index]
            if (v == null) return ByteArray(0)
            return when (v) {
                is Uint8Array -> ByteArray(v.length.toInt()) { v[it].toByte() }
                is Int8Array -> ByteArray(v.length.toInt()) { v[it] }
                else -> throw SubSlothSqliteException("Column $index is not a blob")
            }
        }

        override fun isNull(index: Int): Boolean {
            assertRow()
            // Check the ACTUAL value, not the cached column type.
            return rows[pos][index] == null
        }

        override fun getColumnType(index: Int): Int {
            assertRow()
            // Return the per-row column type.
            return rowTypes.getOrNull(pos)?.getOrNull(index) ?: TYPE_NULL
        }

        override fun getColumnName(index: Int): String = columnNames[index]
        override fun getColumnCount(): Int = columnNames.size

        override fun close() {
            blockingRequest("close", js("{ statementId: statementId }"))
        }

        // ---- internal helpers -------------------------------------------

        private fun assertRow() {
            if (pos < 0 || pos >= rows.size) {
                throw SubSlothSqliteException(
                    "No current row — call step() first (pos=$pos, count=${rows.size})",
                )
            }
        }

        /**
         * Build the bindings array from pending typed bind* calls.
         * 1‑based SQLite indices are mapped to 0‑based array indices.
         */
        private fun buildPayload(): dynamic {
            val maxIdx = pendingBindings.keys.maxOrNull() ?: -1
            val arr = Array<Any?>(maxIdx + 1) { i -> pendingBindings.remove(i) }
            pendingBindings.clear()
            return js("{ statementId: statementId, bindings: arr }")
        }

        /** Parse the worker's `step` response into [rows] and [rowTypes]. */
        private fun parseRowsAndTypes(data: dynamic) {
            val rawRows: Array<dynamic> = data.rows as Array<dynamic>
            val rawTypes: Array<dynamic> = data.columnTypes as Array<dynamic>

            // Unwrap rows: dynamic → concrete Kotlin values
            rows = Array(rawRows.size) { r ->
                val src = rawRows[r] as Array<dynamic>
                Array(src.size) { c -> src[c] }
            }

            // columnTypes is now row-major: Array<Array<number>>
            rowTypes = Array(rawTypes.size) { r ->
                val src = rawTypes[r] as Array<dynamic>
                IntArray(src.size) { c -> (src[c] as Number).toInt() }
            }
        }
    }
}

/** SQLite exception thrown by [SubSlothSqliteDriver]. */
private class SubSlothSqliteException(message: String) : RuntimeException(message)
