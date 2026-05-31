package net.subsloth.database

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
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

// ── Command types ───────────────────────────────────────────────────────

sealed interface WorkerRequest {
    val cmd: String
    val id: Long

    fun toJson(): JsonObject
    fun toJsonString(): String = json.encodeToString(JsonElement.serializer(), toJson())

    data class Open(override val id: Long, val fileName: String) : WorkerRequest {
        override val cmd = "open"
        override fun toJson() = buildJsonObject {
            put("id", JsonPrimitive(id))
            putJsonObject("data") {
                put("cmd", JsonPrimitive(cmd))
                put("fileName", JsonPrimitive(fileName))
            }
        }
    }

    data class Prepare(override val id: Long, val databaseId: Int, val sql: String) : WorkerRequest {
        override val cmd = "prepare"
        override fun toJson() = buildJsonObject {
            put("id", JsonPrimitive(id))
            putJsonObject("data") {
                put("cmd", JsonPrimitive(cmd))
                put("databaseId", JsonPrimitive(databaseId))
                put("sql", JsonPrimitive(sql))
            }
        }
    }

    data class Step(override val id: Long, val statementId: Int, val bindings: List<JsonElement?>) : WorkerRequest {
        override val cmd = "step"
        override fun toJson() = buildJsonObject {
            put("id", JsonPrimitive(id))
            putJsonObject("data") {
                put("cmd", JsonPrimitive(cmd))
                put("statementId", JsonPrimitive(statementId))
                putJsonArray("bindings") {
                    bindings.forEach { b -> if (b == null) add(JsonNull) else add(b) }
                }
            }
        }
    }

    data class Close(override val id: Long, val statementId: Int? = null, val databaseId: Int? = null) :
        WorkerRequest {
        override val cmd = "close"
        override fun toJson() = buildJsonObject {
            put("id", JsonPrimitive(id))
            putJsonObject("data") {
                put("cmd", JsonPrimitive(cmd))
                statementId?.let { put("statementId", JsonPrimitive(it)) }
                databaseId?.let { put("databaseId", JsonPrimitive(it)) }
            }
        }
    }
}

// ── Response types ──────────────────────────────────────────────────────

sealed interface WorkerResponse {
    val id: Long

    fun toJson(): JsonObject
    fun toJsonString(): String = json.encodeToString(JsonElement.serializer(), toJson())

    data class Success(override val id: Long, val data: JsonObject) : WorkerResponse {
        override fun toJson() = buildJsonObject {
            put("id", JsonPrimitive(id))
            put("data", data)
        }
    }

    data class Error(override val id: Long, val message: String) : WorkerResponse {
        override fun toJson() = buildJsonObject {
            put("id", JsonPrimitive(id))
            put("error", JsonPrimitive(message))
        }
    }
}

// ── Command-specific response payloads ──────────────────────────────────

data class OpenResult(val databaseId: Int)
data class PrepareResult(val statementId: Int, val parameterCount: Int, val columnNames: List<String>)
data class StepResult(val rows: List<List<JsonElement?>>, val columnTypes: List<Int>)

fun OpenResult.toJson(): JsonObject = buildJsonObject {
    put("databaseId", JsonPrimitive(databaseId))
}

fun PrepareResult.toJson(): JsonObject = buildJsonObject {
    put("statementId", JsonPrimitive(statementId))
    put("parameterCount", JsonPrimitive(parameterCount))
    putJsonArray("columnNames") { columnNames.forEach { add(JsonPrimitive(it)) } }
}

fun StepResult.toJson(): JsonObject = buildJsonObject {
    putJsonArray("rows") {
        rows.forEach { row ->
            add(
                buildJsonArray {
                    row.forEach { cell -> if (cell == null) add(JsonNull) else add(cell) }
                },
            )
        }
    }
    putJsonArray("columnTypes") { columnTypes.forEach { add(JsonPrimitive(it)) } }
}

// ── Parsing ─────────────────────────────────────────────────────────────

private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

fun parseEnvelope(jsonString: String): JsonObject = json.parseToJsonElement(jsonString).jsonObject

fun JsonObject.parseRequestData(): WorkerRequest {
    val id = jsonPrimitiveLong(requireNotNull(this["id"]) { "missing id" })
    val data = requireNotNull(this["data"]) { "missing data" }.jsonObject
    val cmd = requireNotNull(data["cmd"]) { "missing cmd" }.jsonPrimitive.content
    return when (cmd) {
        "open" -> WorkerRequest.Open(
            id = id,
            fileName = requireNotNull(data["fileName"]) { "missing fileName" }.jsonPrimitive.content,
        )

        "prepare" -> WorkerRequest.Prepare(
            id = id,
            databaseId = jsonPrimitiveInt(requireNotNull(data["databaseId"]) { "missing databaseId" }),
            sql = requireNotNull(data["sql"]) { "missing sql" }.jsonPrimitive.content,
        )

        "step" -> WorkerRequest.Step(
            id = id,
            statementId = jsonPrimitiveInt(requireNotNull(data["statementId"]) { "missing statementId" }),
            bindings = (data["bindings"] ?: JsonArray(emptyList())).jsonArray.map { it.takeIf { it !is JsonNull } },
        )

        "close" -> WorkerRequest.Close(
            id = id,
            statementId = data["statementId"]?.let { jsonPrimitiveInt(it) },
            databaseId = data["databaseId"]?.let { jsonPrimitiveInt(it) },
        )

        else -> throw IllegalArgumentException("Unknown command: $cmd")
    }
}

fun JsonObject.parseResponse(): WorkerResponse {
    val id = jsonPrimitiveLong(requireNotNull(this["id"]) { "missing id" })
    val error = this["error"]?.jsonPrimitive?.content
    return if (error != null) {
        WorkerResponse.Error(id = id, message = error)
    } else {
        WorkerResponse.Success(
            id = id,
            data = requireNotNull(this["data"]) { "missing data and error" }.jsonObject,
        )
    }
}

fun JsonObject.parseOpenResult(): OpenResult = OpenResult(
    databaseId = jsonPrimitiveInt(requireNotNull(this["databaseId"]) { "missing databaseId" }),
)

fun JsonObject.parsePrepareResult(): PrepareResult = PrepareResult(
    statementId = jsonPrimitiveInt(requireNotNull(this["statementId"]) { "missing statementId" }),
    parameterCount = jsonPrimitiveInt(requireNotNull(this["parameterCount"]) { "missing parameterCount" }),
    columnNames = requireNotNull(this["columnNames"]) {
        "missing columnNames"
    }.jsonArray.map { it.jsonPrimitive.content },
)

fun JsonObject.parseStepResult(): StepResult = StepResult(
    rows = requireNotNull(this["rows"]) { "missing rows" }.jsonArray.map { row ->
        row.jsonArray.map { cell -> cell.takeIf { it !is JsonNull } }
    },
    columnTypes = requireNotNull(this["columnTypes"]) { "missing columnTypes" }.jsonArray.map { jsonPrimitiveInt(it) },
)

// ── Version compat: jsonPrimitive.int / .long may not exist on all
// kotlinx.serialization.json versions. Parse from content instead.

private fun jsonPrimitiveInt(element: JsonElement): Int = element.jsonPrimitive.content.toInt()

private fun jsonPrimitiveLong(element: JsonElement): Long = element.jsonPrimitive.content.toLong()
