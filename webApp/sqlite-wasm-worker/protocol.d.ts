/**
 * Type declarations for the WebWorkerSQLiteDriver ↔ worker.js message protocol.
 *
 * **Important:** Our custom driver ([SubSlothSqliteDriver]) uses a modified
 * protocol where `columnTypes` in the `step` response is a **row-major 2D
 * array** (`Array<Array<number>>`), unlike the upstream `WebWorkerSQLiteDriver`
 * which expects a flat `Array<number>` (first row only).
 *
 * The worker handles four commands exchanged as JSON messages via
 * `Worker.postMessage()` / `Worker.onmessage`.
 *
 * Every message is an envelope:
 *   Request:  { id: number, data: RequestPayload }
 *   Success:  { id: number, data: ResponsePayload }
 *   Error:    { id: number, error: string }
 *
 * These declarations mirror the Kotlin types in
 * core/database/src/jvmTest/kotlin/net/subsloth/database/WorkerProtocol.kt.
 * If the protocol changes, both sides must be updated in lockstep.
 */

// ── Request payloads (cmd discriminator) ────────────────────────────────

interface OpenRequestPayload {
    cmd: "open";
    fileName: string;
}

interface PrepareRequestPayload {
    cmd: "prepare";
    databaseId: number;
    sql: string;
}

interface StepRequestPayload {
    cmd: "step";
    statementId: number;
    bindings: Array<string | number | null>;
}

interface CloseRequestPayload {
    cmd: "close";
    statementId?: number;
    databaseId?: number;
}

type RequestPayload =
    | OpenRequestPayload
    | PrepareRequestPayload
    | StepRequestPayload
    | CloseRequestPayload;

// ── Response payloads ───────────────────────────────────────────────────

interface OpenResponsePayload {
    databaseId: number;
}

interface PrepareResponsePayload {
    statementId: number;
    parameterCount: number;
    columnNames: string[];
}

interface StepResponsePayload {
    rows: Array<Array<string | number | null>>;
    /**
     * Row-major 2D array of SQLite column types.
     * `columnTypes[rowIdx][colIdx]` — each per-cell type, so nullable columns
     * correctly report `SQLITE_NULL` (5) for null cells even when other rows
     * in the same column have a non-null type.
     */
    columnTypes: Array<Array<number>>;
}

type ResponsePayload =
    | OpenResponsePayload
    | PrepareResponsePayload
    | StepResponsePayload;

// ── Envelope types ──────────────────────────────────────────────────────

interface RequestEnvelope {
    id: number;
    data: RequestPayload;
}

interface SuccessEnvelope {
    id: number;
    data: ResponsePayload;
}

interface ErrorEnvelope {
    id: number;
    error: string;
}

type ResponseEnvelope = SuccessEnvelope | ErrorEnvelope;

// ── Message event (onmessage in worker) ─────────────────────────────────

interface WorkerMessageEvent extends MessageEvent {
    data: RequestEnvelope;
}
