/**
 * Type declarations for the WebWorkerSQLiteDriver ↔ worker.js message protocol.
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
    columnTypes: number[];
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
