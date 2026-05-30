# KtorHttpDataSource Design

> **Goal:** Replace `media3-datasource-okhttp` with a custom Ktor-based `HttpDataSource` implementation, removing the only remaining OkHttp dependency.

## Architecture

New Android-library module `core:datasource-ktor` bridges Ktor's `HttpClient` into Media3's `HttpDataSource` interface via `runBlocking`. A single adapter class (`KtorHttpDataSource`) and its factory (`KtorHttpDataSource.Factory`) provide the same contract as `OkHttpDataSource` but backed by the CIO engine.

## Module

`core/datasource-ktor/` at namespace `net.subsloth.core.datasource`

**Dependencies:** `media3-exoplayer` (for `HttpDataSource`), `ktor-client-core`, `ktor-client-cio`

## Components

### `KtorHttpDataSource` (implements `HttpDataSource`)
- `open(DataSpec)` — builds Ktor request from URI, headers, byte-range, flags; executes via `runBlocking`; returns content length; throws `InvalidResponseCodeException` on HTTP errors
- `read(byte[], int, int)` — reads from `ByteReadChannel` via `runBlocking`
- `getUri()` / `close()` — standard lifecycle
- `getResponseHeaders()` — passthrough from Ktor response

### `KtorHttpDataSource.Factory` (implements `HttpDataSource.Factory`)
- Creates `KtorHttpDataSource` instances
- Owns an `HttpClient(CIO)` instance with configurable timeouts

## Integration

In `MediaPlaybackController.buildPlayer()`, configure `DefaultMediaSourceFactory.setDataSourceFactory(KtorHttpDataSource.Factory())`. This replaces the default OkHttp datasource.
