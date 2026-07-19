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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.serializer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8Array
import org.w3c.dom.MessageEvent
import org.w3c.dom.Worker
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// ---- Extension: parse jsonPrimitive content as Long / Int / Double ---------

private val JsonPrimitive.longVal: Long get() = content.toLong()
private val JsonPrimitive.intVal: Int get() = content.toInt()
private val JsonPrimitive.doubleVal: Double get() = content.toDouble()

// ---- SQLite type constants ------------------------------------------------

private const val TYPE_NULL = 5

// ---------------------------------------------------------------------------
// Driver
// ---------------------------------------------------------------------------

class SubSlothSqliteDriver(private val worker: Worker) : SQLiteDriver {

    private val pending = mutableMapOf<Long, (JsonObject) -> Unit>()
    private var nextId = 1L
    private val json = Json { ignoreUnknownKeys = true }

    init {
        worker.onmessage = { event: MessageEvent ->
            val raw: String = event.data as String
            val root: JsonObject = json.parseToJsonElement(raw).jsonObject
            val id = root["id"]!!.jsonPrimitive.content.toLong()
            val cb = pending.remove(id)
            if (cb != null) cb(root)
        }
    }

    override val hasConnectionPool: Boolean get() = false

    override suspend fun open(fileName: String): SQLiteConnection {
        val resp = request("open", buildJsonObject { put("fileName", fileName) })
        val dbId = resp["databaseId"]!!.jsonPrimitive.longVal
        return Connection(dbId)
    }

    // ---- Coroutine request/reply ----------------------------------------

    private suspend fun request(cmd: String, payload: JsonObject): JsonObject {
        val id = nextId++
        return suspendCancellableCoroutine { cont ->
            pending[id] = { resp ->
                val errEl = resp["error"]?.jsonPrimitive
                if (errEl != null) {
                    cont.resumeWithException(RuntimeException("$cmd: ${errEl.content}"))
                } else {
                    cont.resume(resp["data"]!!.jsonObject)
                }
            }
            val msg = buildJsonObject {
                put("id", id)
                put(
                    "data",
                    buildJsonObject {
                        put("cmd", cmd)
                        payload.forEach { (k, v) -> put(k, v) }
                    },
                )
            }
            val jsonStr = json.encodeToString(serializer<JsonObject>(), msg)
            @Suppress("UNCHECKED_CAST")
            worker.postMessage(jsonStr as Any)
        }
    }

    // ---- Connection -----------------------------------------------------

    private inner class Connection(private val databaseId: Long) : SQLiteConnection {

        override suspend fun prepare(sql: String): SQLiteStatement {
            val data = request(
                "prepare",
                buildJsonObject {
                    put("databaseId", databaseId)
                    put("sql", sql)
                },
            )
            val stmtId = data["statementId"]!!.jsonPrimitive.longVal
            val paramCount = data["parameterCount"]!!.jsonPrimitive.intVal
            val colNames = data["columnNames"]!!.jsonArray.map { it.jsonPrimitive.content }
            return Statement(stmtId, paramCount, colNames)
        }

        override fun close() {
            // Resources released via Statement.close()
        }
    }

    // ---- Statement ------------------------------------------------------

    private inner class Statement(
        private val statementId: Long,
        @Suppress("UNUSED_PARAMETER") private val paramCount: Int,
        private val colNames: List<String>,
    ) : SQLiteStatement {

        private val pendingBindings = mutableMapOf<Int, Any?>()

        /** Eagerly fetched rows + per-row types from the first step(). */
        private var rows: List<List<Any?>> = emptyList()
        private var rowTypes: List<IntArray> = emptyList()
        private var pos = -1
        private var stepExecuted = false

        // ---- step / reset / clear ---------------------------------------

        override suspend fun step(): Boolean {
            if (!stepExecuted) {
                stepExecuted = true
                val payload = buildJsonObject {
                    put("statementId", statementId)
                    put("bindings", encodeBindings())
                }
                val data = request("step", payload)
                parseResponse(data)
            }
            pos++
            return pos < rows.size
        }

        override fun reset() {
            pos = -1
            rows = emptyList()
            rowTypes = emptyList()
            stepExecuted = false
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

        override fun getText(index: Int): String = cell(index) as? String ?: ""

        override fun getLong(index: Int): Long = (cell(index) as? Number)?.toLong() ?: 0L

        override fun getDouble(index: Int): Double = (cell(index) as? Number)?.toDouble() ?: 0.0

        override fun getFloat(index: Int): Float = getDouble(index).toFloat()
        override fun getInt(index: Int): Int = getLong(index).toInt()
        override fun getBoolean(index: Int): Boolean = getLong(index) != 0L

        override fun getBlob(index: Int): ByteArray {
            val v = cell(index)
            if (v == null) return ByteArray(0)
            @Suppress("UNCHECKED_CAST")
            return when (v) {
                is Uint8Array -> ByteArray(v.length.toInt()) { i -> v[i].toByte() }
                is Int8Array -> ByteArray(v.length.toInt()) { i -> v[i] }
                else -> ByteArray(0)
            }
        }

        override fun isNull(index: Int): Boolean {
            val row = currentRow() ?: return true
            return index < 0 || index >= row.size || row[index] == null
        }

        override fun getColumnType(index: Int): Int {
            if (pos < 0 || pos >= rowTypes.size) return TYPE_NULL
            return rowTypes[pos].getOrNull(index) ?: TYPE_NULL
        }

        override fun getColumnName(index: Int): String = colNames[index]
        override fun getColumnCount(): Int = colNames.size

        override fun close() {
            // Fire-and-forget close message; no response needed.
            val msg = buildJsonObject {
                put("id", 0L)
                put(
                    "data",
                    buildJsonObject {
                        put("cmd", "close")
                        put("statementId", statementId)
                    },
                )
            }
            val jsonStr = json.encodeToString(serializer<JsonObject>(), msg)
            @Suppress("UNCHECKED_CAST")
            worker.postMessage(jsonStr as Any)
        }

        // ---- internal helpers -------------------------------------------

        private fun currentRow(): List<Any?>? = if (pos in rows.indices) rows[pos] else null

        private fun cell(index: Int): Any? = currentRow()?.getOrNull(index)

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
                        cell is JsonPrimitive -> parsePrimitive(cell)
                        else -> null
                    }
                }
            }

            // columnTypes is row-major: [[col0_row1, col1_row1], ...]
            rowTypes = rawTypes.map { rowTypeElement ->
                rowTypeElement.jsonArray.map { it.jsonPrimitive.content.toInt() }.toIntArray()
            }
        }

        /** Convert a JsonPrimitive to the most appropriate Kotlin type. */
        private fun parsePrimitive(p: JsonPrimitive): Any {
            val s = p.content
            // Try to parse as a number — long first, then double.
            val asLong = s.toLongOrNull()
            if (asLong != null) return asLong
            val asDouble = s.toDoubleOrNull()
            if (asDouble != null) return asDouble
            return s
        }
    }
}
