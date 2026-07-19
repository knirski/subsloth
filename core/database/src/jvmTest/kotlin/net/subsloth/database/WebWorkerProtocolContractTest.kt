package net.subsloth.database

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
private fun parse(s: String): JsonObject = json.parseToJsonElement(s).jsonObject
private fun JsonObject.str(key: String): String = requireNotNull(this[key]).jsonPrimitive.content
private fun JsonObject.int(key: String): Int = str(key).toInt()
private fun JsonObject.obj(key: String): JsonObject = requireNotNull(this[key]).jsonObject

class WebWorkerProtocolContractTest {

    @Test
    fun `open request`() {
        val d = parse("""{"id":1,"data":{"cmd":"open","fileName":"test.db"}}""")
        assertEquals(1L, d.int("id").toLong())
        assertEquals("open", d.obj("data").str("cmd"))
        assertEquals("test.db", d.obj("data").str("fileName"))
    }

    @Test
    fun `open response`() {
        val d = parse("""{"id":1,"data":{"databaseId":7}}""")
        assertEquals(7, d.obj("data").int("databaseId"))
    }

    @Test
    fun `prepare request`() {
        val d = parse("""{"id":2,"data":{"cmd":"prepare","databaseId":7,"sql":"SELECT 1"}}""")
        val dd = d.obj("data")
        assertEquals("prepare", dd.str("cmd"))
        assertEquals(7, dd.int("databaseId"))
        assertEquals("SELECT 1", dd.str("sql"))
    }

    @Test
    fun `prepare response`() {
        val d = parse("""{"id":2,"data":{"statementId":3,"parameterCount":2,"columnNames":["id","title"]}}""")
        val dd = d.obj("data")
        assertEquals(3, dd.int("statementId"))
        assertEquals(2, dd.int("parameterCount"))
        assertEquals(listOf("id", "title"), dd["columnNames"]!!.jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun `step request`() {
        val d = parse("""{"id":3,"data":{"cmd":"step","statementId":3,"bindings":["v",42]}}""")
        val dd = d.obj("data")
        assertEquals("step", dd.str("cmd"))
        assertEquals(3, dd.int("statementId"))
        assertEquals(listOf(JsonPrimitive("v"), JsonPrimitive(42)), dd["bindings"]!!.jsonArray.toList())
    }

    @Test
    fun `step request empty bindings`() {
        val d = parse("""{"id":3,"data":{"cmd":"step","statementId":5,"bindings":[]}}""")
        assertTrue(d.obj("data")["bindings"]!!.jsonArray.isEmpty())
    }

    @Test
    fun `step response`() {
        // columnTypes is now row-major: [[col0_row1, col1_row1], [col0_row2, col1_row2]]
        val d = parse("""{"id":3,"data":{"rows":[["a","b"],["c",null]],"columnTypes":[[3,4],[3,5]]}}""")
        val dd = d.obj("data")
        val rows = dd["rows"]!!.jsonArray
        assertEquals(2, rows.size)
        assertEquals(listOf(JsonPrimitive("a"), JsonPrimitive("b")), rows[0].jsonArray.toList())
        assertEquals(listOf(JsonPrimitive("c"), JsonNull), rows[1].jsonArray.toList())
        val types = dd["columnTypes"]!!.jsonArray.map { it.jsonArray.map { it.jsonPrimitive.content.toInt() } }
        assertEquals(listOf(listOf(3, 4), listOf(3, 5)), types)
    }

    @Test
    fun `step response empty`() {
        val d = parse("""{"id":3,"data":{"rows":[],"columnTypes":[]}}""")
        assertTrue(d.obj("data")["rows"]!!.jsonArray.isEmpty())
    }

    @Test
    fun `step response nullable bug scenario`() {
        // Row 1 has non-null TEXT, row 2 has NULL in same nullable column.
        // With per-row types each row's type is reported correctly.
        val d = parse("""{"id":3,"data":{"rows":[["hello"],[null]],"columnTypes":[[3],[5]]}}""")
        val dd = d.obj("data")
        val rows = dd["rows"]!!.jsonArray
        assertEquals(2, rows.size)
        assertEquals(JsonPrimitive("hello"), rows[0].jsonArray.single())
        assertEquals(JsonNull, rows[1].jsonArray.single())
        val types = dd["columnTypes"]!!.jsonArray.map { it.jsonArray.map { it.jsonPrimitive.content.toInt() } }
        assertEquals(listOf(listOf(3), listOf(5)), types, "Row 1: TEXT; Row 2: NULL — per-row types")
    }

    @Test
    fun `close request statementId`() {
        val d = parse("""{"id":4,"data":{"cmd":"close","statementId":3}}""")
        val dd = d.obj("data")
        assertEquals("close", dd.str("cmd"))
        assertEquals(3, dd.int("statementId"))
        assertEquals(null, dd["databaseId"])
    }

    @Test
    fun `close request databaseId`() {
        val d = parse("""{"id":4,"data":{"cmd":"close","databaseId":7}}""")
        assertEquals(7, d.obj("data").int("databaseId"))
    }

    @Test
    fun `close request both ids`() {
        val d = parse("""{"id":4,"data":{"cmd":"close","statementId":3,"databaseId":7}}""")
        val dd = d.obj("data")
        assertEquals(3, dd.int("statementId"))
        assertEquals(7, dd.int("databaseId"))
    }

    @Test
    fun `close response empty`() {
        val d = parse("""{"id":4}""")
        assertEquals(4L, d.int("id").toLong())
        assertEquals(null, d["data"])
        assertEquals(null, d["error"])
    }

    @Test
    fun `error response`() {
        val d = parse("""{"id":1,"error":"something went wrong"}""")
        assertEquals(1L, d.int("id").toLong())
        assertEquals("something went wrong", d.str("error"))
        assertEquals(null, d["data"])
    }

    @Test
    fun `success response must not have error`() {
        val d = parse("""{"id":1,"data":{"databaseId":0}}""")
        assertEquals(null, d["error"])
    }
}
