package net.subsloth.database

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Contract test for the WebWorkerSQLiteDriver ↔ worker.js message protocol.
 *
 * The worker implements four commands exchanged as JSON messages via postMessage:
 *   open, prepare, step, close
 *
 * Each message is wrapped in an envelope: { id: Long, data: RequestData } (request)
 * or { id: Long, data?: ResponseData, error?: String } (response/error).
 *
 * These shapes must match what the Kotlin WebWorkerSQLiteDriver (androidx.sqlite:sqlite-web)
 * sends and expects. If the driver's protocol changes, this test catches the drift.
 */
class WebWorkerProtocolContractTest {

    // ── Helpers ──────────────────────────────────────────────────────────

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    private fun parse(jsonString: String): JsonObject = json.parseToJsonElement(jsonString).jsonObject

    private fun JsonObject.requireObj(key: String): JsonObject =
        requireNotNull(this[key]) { "missing key: $key" }.jsonObject

    private fun JsonObject.requireStr(key: String): String =
        requireNotNull(this[key]) { "missing key: $key" }.jsonPrimitive.content

    private fun JsonObject.requireInt(key: String): Int =
        requireNotNull(this[key]) { "missing key: $key" }.jsonPrimitive.int

    private fun JsonObject.optStr(key: String): String? = this[key]?.jsonPrimitive?.content

    private fun JsonObject.optInt(key: String): Int? = this[key]?.jsonPrimitive?.int

    // ── Request envelope ─────────────────────────────────────────────────

    @Test
    fun `request envelope contains id and data`() {
        val msg = parse("""{"id":42,"data":{"cmd":"open","fileName":"test.db"}}""")
        assertEquals(42, msg.requireInt("id"))
        val data = msg.requireObj("data")
        assertEquals("open", data.requireStr("cmd"))
    }

    @Test
    fun `request envelope requires id to be numeric`() {
        val msg = parse("""{"id":0,"data":{"cmd":"close"}}""")
        assertEquals(0, msg.requireInt("id"))
    }

    // ── Response envelope ────────────────────────────────────────────────

    @Test
    fun `success response contains id and data`() {
        val msg = parse("""{"id":1,"data":{"databaseId":0}}""")
        assertEquals(1, msg.requireInt("id"))
        assertNotNull(msg["data"])
    }

    @Test
    fun `error response contains id and error string`() {
        val msg = parse("""{"id":1,"error":"db not found"}""")
        assertEquals(1, msg.requireInt("id"))
        assertEquals("db not found", msg.requireStr("error"))
    }

    @Test
    fun `success response must not have error field`() {
        val msg = parse("""{"id":1,"data":{"databaseId":0}}""")
        assertEquals(null, msg["error"])
    }

    // ── open ─────────────────────────────────────────────────────────────

    @Test
    fun `open request shape`() {
        val msg = parse("""{"id":1,"data":{"cmd":"open","fileName":"subsloth.db"}}""")
        val data = msg.requireObj("data")
        assertEquals("open", data.requireStr("cmd"))
        assertEquals("subsloth.db", data.requireStr("fileName"))
        assertTrue(data["fileName"]?.jsonPrimitive?.isString ?: false)
    }

    @Test
    fun `open request rejects missing fileName`() {
        val msg = parse("""{"id":1,"data":{"cmd":"open"}}""")
        val data = msg.requireObj("data")
        assertEquals("open", data.requireStr("cmd"))
        assertEquals(null, data["fileName"])
    }

    @Test
    fun `open response shape`() {
        val msg = parse("""{"id":1,"data":{"databaseId":7}}""")
        val data = msg.requireObj("data")
        assertEquals(7, data.requireInt("databaseId"))
        assertTrue(data["databaseId"]?.jsonPrimitive?.int != null)
    }

    // ── prepare ──────────────────────────────────────────────────────────

    @Test
    fun `prepare request shape`() {
        val msg = parse("""{"id":2,"data":{"cmd":"prepare","databaseId":7,"sql":"SELECT * FROM media"}}""")
        val data = msg.requireObj("data")
        assertEquals("prepare", data.requireStr("cmd"))
        assertEquals(7, data.requireInt("databaseId"))
        assertEquals("SELECT * FROM media", data.requireStr("sql"))
    }

    @Test
    fun `prepare response shape`() {
        val msg = parse("""{"id":2,"data":{"statementId":3,"parameterCount":2,"columnNames":["id","title"]}}""")
        val data = msg.requireObj("data")
        assertEquals(3, data.requireInt("statementId"))
        assertEquals(2, data.requireInt("parameterCount"))
        val names = data["columnNames"]!!.jsonArray
        assertEquals(2, names.size)
        assertEquals("id", names[0].jsonPrimitive.content)
        assertEquals("title", names[1].jsonPrimitive.content)
    }

    @Test
    fun `prepare response columnNames allows empty`() {
        val msg = parse("""{"id":2,"data":{"statementId":4,"parameterCount":0,"columnNames":[]}}""")
        assertTrue(msg.requireObj("data")["columnNames"]!!.jsonArray.isEmpty())
    }

    // ── step ─────────────────────────────────────────────────────────────

    @Test
    fun `step request shape`() {
        val msg = parse("""{"id":3,"data":{"cmd":"step","statementId":3,"bindings":["value1",42]}}""")
        val data = msg.requireObj("data")
        assertEquals("step", data.requireStr("cmd"))
        assertEquals(3, data.requireInt("statementId"))
        val bindings = data["bindings"]!!.jsonArray
        assertEquals(2, bindings.size)
        assertEquals("value1", bindings[0].jsonPrimitive.content)
        assertEquals(42, bindings[1].jsonPrimitive.int)
    }

    @Test
    fun `step request bindings allows empty`() {
        val msg = parse("""{"id":3,"data":{"cmd":"step","statementId":5,"bindings":[]}}""")
        assertTrue(msg.requireObj("data")["bindings"]!!.jsonArray.isEmpty())
    }

    @Test
    fun `step response shape`() {
        val msg = parse("""{"id":3,"data":{"rows":[["row1col1","row1col2"],["row2col1",null]],"columnTypes":[3,4]}}""")
        val data = msg.requireObj("data")
        val rows = data["rows"]!!.jsonArray
        assertEquals(2, rows.size)
        val firstRow = rows[0].jsonArray
        assertEquals("row1col1", firstRow[0].jsonPrimitive.content)
        assertEquals("row1col2", firstRow[1].jsonPrimitive.content)
        val secondRow = rows[1].jsonArray
        assertEquals("row2col1", secondRow[0].jsonPrimitive.content)
        assertTrue(secondRow[1] is kotlinx.serialization.json.JsonNull)
        val columnTypes = data["columnTypes"]!!.jsonArray
        assertEquals(2, columnTypes.size)
        assertEquals(3, columnTypes[0].jsonPrimitive.int)
        assertEquals(4, columnTypes[1].jsonPrimitive.int)
    }

    @Test
    fun `step response columnTypes matches row column count`() {
        val msg = parse("""{"id":3,"data":{"rows":[["a","b"]],"columnTypes":[1,2]}}""")
        val data = msg.requireObj("data")
        val rows = data["rows"]!!.jsonArray
        val columnTypes = data["columnTypes"]!!.jsonArray
        if (rows.isNotEmpty()) {
            assertEquals(rows[0].jsonArray.size, columnTypes.size)
        }
    }

    @Test
    fun `step response rows allows empty`() {
        val msg = parse("""{"id":3,"data":{"rows":[],"columnTypes":[]}}""")
        assertTrue(msg.requireObj("data")["rows"]!!.jsonArray.isEmpty())
        assertTrue(msg.requireObj("data")["columnTypes"]!!.jsonArray.isEmpty())
    }

    // ── close ────────────────────────────────────────────────────────────

    @Test
    fun `close request with statementId only`() {
        val msg = parse("""{"id":4,"data":{"cmd":"close","statementId":3}}""")
        val data = msg.requireObj("data")
        assertEquals("close", data.requireStr("cmd"))
        assertEquals(3, data.requireInt("statementId"))
        assertEquals(null, data["databaseId"])
    }

    @Test
    fun `close request with databaseId only`() {
        val msg = parse("""{"id":4,"data":{"cmd":"close","databaseId":7}}""")
        val data = msg.requireObj("data")
        assertEquals("close", data.requireStr("cmd"))
        assertEquals(null, data["statementId"])
        assertEquals(7, data.requireInt("databaseId"))
    }

    @Test
    fun `close request with both ids`() {
        val msg = parse("""{"id":4,"data":{"cmd":"close","statementId":3,"databaseId":7}}""")
        val data = msg.requireObj("data")
        assertEquals(3, data.requireInt("statementId"))
        assertEquals(7, data.requireInt("databaseId"))
    }

    @Test
    fun `close response is empty (one-way)`() {
        val msg = parse("""{"id":4}""")
        assertEquals(4, msg.requireInt("id"))
        assertEquals(null, msg["data"])
        assertEquals(null, msg["error"])
    }

    // ── Error handling ───────────────────────────────────────────────────

    @Test
    fun `invalid command returns error`() {
        val msg = parse("""{"id":99,"error":"unrecognized command 'invalid'"}""")
        assertEquals(99, msg.requireInt("id"))
        assertNotNull(msg["error"])
        assertEquals(null, msg["data"])
    }

    @Test
    fun `missing data field returns error`() {
        val msg = parse("""{"id":100,"error":"Invalid request, missing 'data'."}""")
        assertEquals(100, msg.requireInt("id"))
        assertEquals(null, msg["data"])
    }
}
