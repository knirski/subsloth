@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

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

// ---------------------------------------------------------------------------
// Top-level JS interop helpers
// Kotlin/Wasm allows js() only in top-level function single-expression bodies
// or property initializers.
// ---------------------------------------------------------------------------

/** Post a JSON payload to a Worker (serialize to string, parse as JS object). */
private val jsWorkerPost: (Worker, String) -> Unit =
    js("(w, s) => { w.postMessage(JSON.parse(s)) }")

/** Parse a JSON string from Worker message into a JsonObject. */
private val json = Json { ignoreUnknownKeys = true }

// ---- SQLite type constants ------------------------------------------------

private const val TYPE_NULL = 5

// ---------------------------------------------------------------------------
// Driver
// ---------------------------------------------------------------------------

class SubSlothSqliteDriver(private val worker: Worker) : SQLiteDriver {

    private val pending = mutableMapOf<Long, (JsonObject) -> Unit>()
    private var nextId = 1L

    init {
        worker.onmessage = { event: MessageEvent ->
            val raw: String = event.data as String
            val root: JsonObject = json.parseToJsonElement(raw).jsonObject
            val id = root["id"]!!.jsonPrimitive.content.toLong()
            pending.remove(id)?.invoke(root)
        }
    }

    override val hasConnectionPool: Boolean get() = false

    override suspend fun open(fileName: String): SQLiteConnection {
        val resp = request("open", buildJsonObject { put("fileName", fileName) })
        val dbId = resp["databaseId"]!!.jsonPrimitive.content.toLong()
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
            jsWorkerPost(worker, jsonStr)
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
            val stmtId = data["statementId"]!!.jsonPrimitive.content.toLong()
            val colNames = data["columnNames"]!!.jsonArray.map { it.jsonPrimitive.content }
            return Statement(stmtId, colNames)
        }

        override fun close() { /* Resources released via Statement.close() */ }
    }

    // ---- Statement ------------------------------------------------------

    private inner class Statement(private val statementId: Long, private val colNames: List<String>) :
        SQLiteStatement {

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
            if (cell(index) == null) return ByteArray(0)
            return ByteArray(0) // Blob support limited on wasmJs
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
            jsWorkerPost(worker, jsonStr)
        }

        // ---- internal helpers -------------------------------------------

        private fun currentRow(): List<Any?>? = if (pos in rows.indices) rows[pos] else null

        private fun cell(index: Int): Any? = currentRow()?.getOrNull(index)

        private fun encodeBindings(): JsonArray {
            val maxIdx = pendingBindings.keys.maxOrNull() ?: 0
            val elements = Array<JsonElement>(maxIdx) { i ->
                val v = pendingBindings.remove(i + 1)
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

            rowTypes = rawTypes.map { rowTypeElement ->
                rowTypeElement.jsonArray.map { it.jsonPrimitive.content.toInt() }.toIntArray()
            }
        }

        private fun parsePrimitive(p: JsonPrimitive): Any {
            val s = p.content
            s.toLongOrNull()?.let { return it }
            s.toDoubleOrNull()?.let { return it }
            return s
        }
    }
}
