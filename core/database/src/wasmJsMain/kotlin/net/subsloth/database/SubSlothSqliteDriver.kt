/*
 * Custom SQLiteDriver for wasmJs that closes the "WebWorkerSQLiteDriver
 * nullable bug" (github.com/linhvnguyen9/room3-sqlite-web-nullable-npe-repro).
 *
 * == Root cause ==
 *
 * The upstream StatementResult stores flat columnTypes: IntArray populated
 * from the FIRST row of the worker's step response.  All subsequent rows
 * reuse these cached types.  When row 1 has a non-null value in a nullable
 * column and row 2 has null, getCellType() returns the cached TEXT type
 * => isNull() returns false => Room calls getText() => as String on null
 * => NPE.
 *
 * == Fix ==
 *
 * The worker's step response carries columnTypes as a row-major 2D
 * array (Array<Array<number>>), and this driver uses per-row types for
 * every row access.  All getters are null-safe.
 *
 * == Protocol ==
 *
 * Messages are exchanged as JSON strings via Worker.postMessage() and
 * parsed with kotlinx.serialization.json on the Kotlin side.  The worker
 * also stringifys/parses JSON so the format is symmetric.
 */

package net.subsloth.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.*
import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8Array
import org.w3c.dom.MessageEvent
import org.w3c.dom.Worker
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// ---------------------------------------------------------------------------
// Driver
// ---------------------------------------------------------------------------

class SubSlothSqliteDriver(
    private val worker: Worker,
) : SQLiteDriver {

    private val pending = mutableMapOf<Long, (JsonObject) -> Unit>()
    private var nextId = 1L
    private val json = Json { ignoreUnknownKeys = true }

    init {
        worker.onmessage = { event: MessageEvent ->
            val raw: String = event.data as String
            val root: JsonObject = json.decodeFromString(raw)
            val id = root["id"]!!.jsonPrimitive.long
            val cb = pending.remove(id)
            if (cb != null) cb(root)
        }
    }

    override val hasConnectionPool: Boolean get() = false

    override suspend fun open(fileName: String): SQLiteConnection {
        val resp = requestCmd("open", buildJsonObject { put("fileName", fileName) })
        val dbId = resp["databaseId"]!!.jsonPrimitive.long
        return Connection(dbId)
    }

    // ---- Coroutine request/reply ----------------------------------------

    /**
     * Send a command and await the response via coroutine suspension.
     * The caller is responsible for wrapping in [runBlocking] if needed.
     */
    private suspend fun requestCmd(cmd: String, payload: JsonObject): JsonObject {
        val id = nextId++
        return suspendCancellableCoroutine { cont ->
            pending[id] = { resp ->
                val error = resp["error"]?.jsonPrimitive?.contentOrNull
                if (error != null) {
                    cont.resumeWithException(
                        RuntimeException("$cmd: $error")
                    )
                } else {
                    cont.resume(resp["data"]!!.jsonObject)
                }
            }
            val msg = buildJsonObject {
                put("id", id)
                put("data", buildJsonObject {
                    put("cmd", cmd)
                    payload.forEach { (k, v) -> put(k, v) }
                })
            }
            worker.postMessage(json.encodeToString(msg))
        }
    }

    /** Blocking variant — bridges non-suspend step()/close() to suspend. */
    private fun blockingRequest(cmd: String, payload: JsonObject): JsonObject =
        runBlocking { requestCmd(cmd, payload) }

    // ---- Connection -----------------------------------------------------

    private inner class Connection(
        private val databaseId: Long,
    ) : SQLiteConnection {

        override val inTransaction: Boolean get() = false

        override suspend fun prepare(sql: String): SQLiteStatement {
            val data = requestCmd(
                "prepare",
                buildJsonObject {
                    put("databaseId", databaseId)
                    put("sql", sql)
                },
            )
            val stmtId = data["statementId"]!!.jsonPrimitive.long
            val paramCount = data["parameterCount"]!!.jsonPrimitive.int
            val colNames = data["columnNames"]!!.jsonArray.map { it.jsonPrimitive.content }
            return Statement(stmtId, paramCount, colNames)
        }

        override fun close() {
            // Resources are released via Statement.close()
        }
    }

    // ---- Statement ------------------------------------------------------

    private inner class Statement(
        private val statementId: Long,
        override val parameterCount: Int,
        override val columnNames: List<String>,
    ) : SQLiteStatement {

        private val pendingBindings = mutableMapOf<Int, Any?>()

        /** Eagerly fetched rows + per-row types from the first step(). */
        private var rows: List<List<Any?>> = emptyList()
        private var rowTypes: List<IntArray> = emptyList()
        private var pos = -1

        // ---- step / reset / clear ---------------------------------------

        override fun step(): Boolean {
            if (rows.isEmpty()) {
                val payload = buildJsonObject {
                    put("statementId", statementId)
                    put("bindings", encodeBindings())
                }
                val data = blockingRequest("step", payload)
                parseResponse(data)
            }
            pos++
            return pos < rows.size
        }

        override fun reset() {
            pos = -1
            rows = emptyList()
            rowTypes = emptyList()
        }

        override fun clearBindings() {
            pendingBindings.clear()
        }

        // ---- typed bind helpers -----------------------------------------

        override fun bindText(index: Int, value: String) { pendingBindings[index] = value }
        override fun bindLong(index: Int, value: Long) { pendingBindings[index] = value }
        override fun bindDouble(index: Int, value: Double) { pendingBindings[index] = value }
        override fun bindFloat(index: Int, value: Float) { pendingBindings[index] = value.toDouble() }
        override fun bindInt(index: Int, value: Int) { pendingBindings[index] = value.toLong() }
        override fun bindBoolean(index: Int, value: Boolean) { pendingBindings[index] = if (value) 1L else 0L }
        override fun bindBlob(index: Int, value: ByteArray) { pendingBindings[index] = value }
        override fun bindNull(index: Int) { pendingBindings[index] = null }

        // ---- row getters (all null-safe) ---------------------------------

        override fun getText(index: Int): String {
            return currentCell(index) as? String ?: ""
        }

        override fun getLong(index: Int): Long {
            return (currentCell(index) as? Number)?.toLong() ?: 0L
        }

        override fun getDouble(index: Int): Double {
            return (currentCell(index) as? Number)?.toDouble() ?: 0.0
        }

        override fun getFloat(index: Int): Float = getDouble(index).toFloat()
        override fun getInt(index: Int): Int = getLong(index).toInt()
        override fun getBoolean(index: Int): Boolean = getLong(index) != 0L

        override fun getBlob(index: Int): ByteArray {
            val v = currentCell(index)
            if (v == null) return ByteArray(0)
            return when (v) {
                is Uint8Array -> ByteArray(v.length.toInt()) { v[it].toByte() }
                is Int8Array -> ByteArray(v.length.toInt()) { v[it] }
                else -> throw RuntimeException("Column $index is not a blob")
            }
        }

        override fun isNull(index: Int): Boolean {
            // Check the ACTUAL value, not a cached column type.
            val row = currentRow() ?: return true
            return index < 0 || index >= row.size || row[index] == null
        }

        override fun getColumnType(index: Int): Int {
            if (pos < 0 || pos >= rowTypes.size) return 5 /* NULL */
            val types = rowTypes[pos]
            return types.getOrNull(index) ?: 5
        }

        override fun getColumnName(index: Int): String = columnNames[index]
        override fun getColumnCount(): Int = columnNames.size

        override fun close() {
            blockingRequest("close", buildJsonObject {
                put("statementId", statementId)
            })
        }

        // ---- internal helpers -------------------------------------------

        private fun currentRow(): List<Any?>? {
            return if (pos in rows.indices) rows[pos] else null
        }

        private fun currentCell(index: Int): Any? {
            return currentRow()?.getOrNull(index)
        }

        /** Build JSON array of bindings from pending typed bind* calls. */
        private fun encodeBindings(): JsonArray {
            val maxIdx = pendingBindings.keys.maxOrNull() ?: -1
            val elements = Array<JsonElement>(maxIdx + 1) { i ->
                val v = pendingBindings.remove(i)
                when (v) {
                    null -> JsonNull
                    is String -> JsonPrimitive(v)
                    is Number -> JsonPrimitive(v.toDouble())
                    is Boolean -> JsonPrimitive(v)
                    else -> JsonNull
                }
            }
            pendingBindings.clear()
            return JsonArray(elements.toList())
        }

        /** Parse the worker's `step` response into [rows] and [rowTypes]. */
        private fun parseResponse(data: JsonObject) {
            val rawRows = data["rows"]!!.jsonArray
            val rawTypes = data["columnTypes"]!!.jsonArray

            rows = rawRows.map { rowElement ->
                rowElement.jsonArray.map { cell ->
                    when {
                        cell is JsonNull -> null
                        cell is JsonPrimitive && cell.isString -> cell.content
                        cell is JsonPrimitive && cell.contentOrNull?.toLongOrNull() != null ->
                            cell.long
                        cell is JsonPrimitive && cell.contentOrNull?.toDoubleOrNull() != null ->
                            cell.double
                        cell is JsonPrimitive -> cell.content
                        else -> null
                    }
                }
            }

            // columnTypes is row-major: [[col0_row1, col1_row1], [col0_row2, col1_row2], ...]
            rowTypes = rawTypes.map { rowTypeElement ->
                rowTypeElement.jsonArray.map { it.jsonPrimitive.int }.toIntArray()
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Extensions
// ---------------------------------------------------------------------------

private fun JsonObject.long(key: String): Long = this[key]!!.jsonPrimitive.long
