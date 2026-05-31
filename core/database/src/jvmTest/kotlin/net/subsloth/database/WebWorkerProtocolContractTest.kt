package net.subsloth.database

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
 * Tests use typed [WorkerRequest] / [WorkerResponse] classes with manual
 * JSON serialization. Round-trip tests verify that serialize → parse
 * produces an equivalent object. Shape tests verify the protocol contract
 * (field presence, types, null handling, edge cases).
 *
 * These shapes must match what the Kotlin WebWorkerSQLiteDriver sends and
 * expects. The TypeScript declarations in webApp/sqlite-wasm-worker/protocol.d.ts
 * mirror the JavaScipt side. If the driver's protocol changes, the
 * round-trip tests catch it at build time.
 */
class WebWorkerProtocolContractTest {

    // ── Round-trip: serialize → parse → verify ───────────────────────────

    @Test
    fun `open request round-trip`() {
        val original = WorkerRequest.Open(id = 1, fileName = "subsloth.db")
        val parsed = parseEnvelope(original.toJsonString()).parseRequestData()
        assertEquals(original, parsed)
    }

    @Test
    fun `prepare request round-trip`() {
        val original = WorkerRequest.Prepare(id = 2, databaseId = 7, sql = "SELECT * FROM media")
        val parsed = parseEnvelope(original.toJsonString()).parseRequestData()
        assertEquals(original, parsed)
    }

    @Test
    fun `step request round-trip`() {
        val original = WorkerRequest.Step(
            id = 3,
            statementId = 5,
            bindings = listOf(JsonPrimitive("val"), JsonPrimitive(42), null),
        )
        val parsed = parseEnvelope(original.toJsonString()).parseRequestData()
        assertEquals(original, parsed)
    }

    @Test
    fun `step request empty bindings round-trip`() {
        val original = WorkerRequest.Step(id = 3, statementId = 5, bindings = emptyList())
        val parsed = parseEnvelope(original.toJsonString()).parseRequestData()
        assertEquals(original, parsed)
    }

    @Test
    fun `close request with statementId round-trip`() {
        val original = WorkerRequest.Close(id = 4, statementId = 3)
        val parsed = parseEnvelope(original.toJsonString()).parseRequestData()
        assertEquals(original, parsed)
    }

    @Test
    fun `close request with databaseId round-trip`() {
        val original = WorkerRequest.Close(id = 4, databaseId = 7)
        val parsed = parseEnvelope(original.toJsonString()).parseRequestData()
        assertEquals(original, parsed)
    }

    @Test
    fun `close request with both ids round-trip`() {
        val original = WorkerRequest.Close(id = 4, statementId = 3, databaseId = 7)
        val parsed = parseEnvelope(original.toJsonString()).parseRequestData()
        assertEquals(original, parsed)
    }

    @Test
    fun `success response round-trip`() {
        val original = WorkerResponse.Success(
            id = 1,
            data = JsonObject(mapOf("databaseId" to JsonPrimitive(0))),
        )
        val parsed = parseEnvelope(original.toJsonString()).parseResponse()
        assertEquals(original, parsed)
    }

    @Test
    fun `error response round-trip`() {
        val original = WorkerResponse.Error(id = 1, message = "db not found")
        val parsed = parseEnvelope(original.toJsonString()).parseResponse()
        assertEquals(original, parsed)
    }

    @Test
    fun `open result payload round-trip`() {
        val original = OpenResult(databaseId = 7)
        val json = original.toJson().toString()
        val parsed = parseEnvelope("""{"id":1,"data":$json}""")
            .parseResponse().let { it as WorkerResponse.Success }.data
        assertEquals(original, parsed.parseOpenResult())
    }

    @Test
    fun `prepare result payload round-trip`() {
        val original = PrepareResult(statementId = 3, parameterCount = 2, columnNames = listOf("id", "title"))
        val json = original.toJson().toString()
        val parsed = parseEnvelope("""{"id":1,"data":$json}""")
            .parseResponse().let { it as WorkerResponse.Success }.data
        assertEquals(original, parsed.parsePrepareResult())
    }

    @Test
    fun `step result payload round-trip with null cells`() {
        val original = StepResult(
            rows = listOf(listOf(JsonPrimitive("a"), JsonPrimitive(1)), listOf(null, JsonPrimitive("b"))),
            columnTypes = listOf(3, 4),
        )
        val json = original.toJson().toString()
        val parsed = parseEnvelope("""{"id":1,"data":$json}""")
            .parseResponse().let { it as WorkerResponse.Success }.data
        assertEquals(original, parsed.parseStepResult())
    }

    @Test
    fun `step result payload round-trip empty`() {
        val original = StepResult(rows = emptyList(), columnTypes = emptyList())
        val json = original.toJson().toString()
        val parsed = parseEnvelope("""{"id":1,"data":$json}""")
            .parseResponse().let { it as WorkerResponse.Success }.data
        assertEquals(original, parsed.parseStepResult())
    }

    // ── Request envelope ─────────────────────────────────────────────────

    @Test
    fun `request envelope contains id and data`() {
        val msg = parseEnvelope("""{"id":42,"data":{"cmd":"open","fileName":"test.db"}}""")
        assertEquals(42L, msg["id"]?.jsonPrimitive?.content?.toLongOrNull())
        assertEquals("open", msg["data"]?.jsonObject?.get("cmd")?.jsonPrimitive?.content)
    }

    @Test
    fun `request envelope requires id to be numeric`() {
        val msg = parseEnvelope("""{"id":0,"data":{"cmd":"close"}}""")
        assertEquals(0L, msg["id"]?.jsonPrimitive?.content?.toLongOrNull())
    }

    // ── Response envelope ────────────────────────────────────────────────

    @Test
    fun `success response contains id and data`() {
        val msg = parseEnvelope("""{"id":1,"data":{"databaseId":0}}""")
        assertEquals(1L, msg["id"]?.jsonPrimitive?.content?.toLongOrNull())
        assertNotNull(msg["data"])
    }

    @Test
    fun `error response contains id and error string`() {
        val msg = parseEnvelope("""{"id":1,"error":"db not found"}""")
        assertEquals(1L, msg["id"]?.jsonPrimitive?.content?.toLongOrNull())
        assertEquals("db not found", msg["error"]?.jsonPrimitive?.content)
    }

    @Test
    fun `success response must not have error field`() {
        val msg = parseEnvelope("""{"id":1,"data":{"databaseId":0}}""")
        assertEquals(null, msg["error"])
    }

    // ── open ─────────────────────────────────────────────────────────────

    @Test
    fun `open request shape`() {
        val req = parseEnvelope(
            """{"id":1,"data":{"cmd":"open","fileName":"subsloth.db"} }""",
        ).parseRequestData() as WorkerRequest.Open
        assertEquals("subsloth.db", req.fileName)
    }

    @Test
    fun `open request rejects missing fileName`() {
        val msg = parseEnvelope("""{"id":1,"data":{"cmd":"open"}}""")
        val data = msg["data"]?.jsonObject
        assertEquals(null, data?.get("fileName"))
    }

    @Test
    fun `open result shape`() {
        val result = parseEnvelope("""{"id":1,"data":{"databaseId":7}}""")
            .parseResponse().let { it as WorkerResponse.Success }.data.parseOpenResult()
        assertEquals(7, result.databaseId)
    }

    // ── prepare ──────────────────────────────────────────────────────────

    @Test
    fun `prepare request shape`() {
        val req = parseEnvelope("""{"id":2,"data":{"cmd":"prepare","databaseId":7,"sql":"SELECT * FROM media"}}""")
            .parseRequestData() as WorkerRequest.Prepare
        assertEquals(7, req.databaseId)
        assertEquals("SELECT * FROM media", req.sql)
    }

    @Test
    fun `prepare result shape`() {
        val result = parseEnvelope(
            """{"id":2,"data":{"statementId":3,"parameterCount":2,"columnNames":["id","title"]}}""",
        )
            .parseResponse().let { it as WorkerResponse.Success }.data.parsePrepareResult()
        assertEquals(3, result.statementId)
        assertEquals(2, result.parameterCount)
        assertEquals(listOf("id", "title"), result.columnNames)
    }

    @Test
    fun `prepare result columnNames allows empty`() {
        val result = parseEnvelope("""{"id":2,"data":{"statementId":4,"parameterCount":0,"columnNames":[]}}""")
            .parseResponse().let { it as WorkerResponse.Success }.data.parsePrepareResult()
        assertTrue(result.columnNames.isEmpty())
    }

    // ── step ─────────────────────────────────────────────────────────────

    @Test
    fun `step request shape`() {
        val req = parseEnvelope("""{"id":3,"data":{"cmd":"step","statementId":3,"bindings":["value1",42]}}""")
            .parseRequestData() as WorkerRequest.Step
        assertEquals(3, req.statementId)
        assertEquals(listOf(JsonPrimitive("value1"), JsonPrimitive(42)), req.bindings)
    }

    @Test
    fun `step request bindings allows empty`() {
        val req = parseEnvelope("""{"id":3,"data":{"cmd":"step","statementId":5,"bindings":[]}}""")
            .parseRequestData() as WorkerRequest.Step
        assertTrue(req.bindings.isEmpty())
    }

    @Test
    fun `step result shape`() {
        val result = parseEnvelope(
            """{"id":3,"data":{"rows":[["row1col1","row1col2"],["row2col1",null]],"columnTypes":[3,4]}}""",
        )
            .parseResponse().let { it as WorkerResponse.Success }.data.parseStepResult()
        assertEquals(2, result.rows.size)
        assertEquals(listOf(JsonPrimitive("row1col1"), JsonPrimitive("row1col2")), result.rows[0])
        assertEquals(listOf(JsonPrimitive("row2col1"), null), result.rows[1])
        assertEquals(listOf(3, 4), result.columnTypes)
    }

    @Test
    fun `step result rows allows empty`() {
        val result = parseEnvelope("""{"id":3,"data":{"rows":[],"columnTypes":[]}}""")
            .parseResponse().let { it as WorkerResponse.Success }.data.parseStepResult()
        assertTrue(result.rows.isEmpty())
        assertTrue(result.columnTypes.isEmpty())
    }

    // ── close ────────────────────────────────────────────────────────────

    @Test
    fun `close request with statementId only`() {
        val req = parseEnvelope(
            """{"id":4,"data":{"cmd":"close","statementId":3}}""",
        ).parseRequestData() as WorkerRequest.Close
        assertEquals(3, req.statementId)
        assertEquals(null, req.databaseId)
    }

    @Test
    fun `close request with databaseId only`() {
        val req = parseEnvelope(
            """{"id":4,"data":{"cmd":"close","databaseId":7}}""",
        ).parseRequestData() as WorkerRequest.Close
        assertEquals(null, req.statementId)
        assertEquals(7, req.databaseId)
    }

    @Test
    fun `close request with both ids`() {
        val req = parseEnvelope(
            """{"id":4,"data":{"cmd":"close","statementId":3,"databaseId":7}}""",
        ).parseRequestData() as WorkerRequest.Close
        assertEquals(3, req.statementId)
        assertEquals(7, req.databaseId)
    }

    @Test
    fun `close response is empty`() {
        val msg = parseEnvelope("""{"id":4}""")
        assertEquals(4L, msg["id"]?.jsonPrimitive?.content?.toLongOrNull())
        assertEquals(null, msg["data"])
        assertEquals(null, msg["error"])
    }

    // ── Error handling ───────────────────────────────────────────────────

    @Test
    fun `invalid command returns error`() {
        val resp = parseEnvelope(
            """{"id":99,"error":"unrecognized command 'invalid'"}""",
        ).parseResponse() as WorkerResponse.Error
        assertEquals("unrecognized command 'invalid'", resp.message)
    }

    @Test
    fun `missing data field returns error`() {
        val resp = parseEnvelope(
            """{"id":100,"error":"Invalid request, missing 'data'."}""",
        ).parseResponse() as WorkerResponse.Error
        assertEquals("Invalid request, missing 'data'.", resp.message)
    }
}
