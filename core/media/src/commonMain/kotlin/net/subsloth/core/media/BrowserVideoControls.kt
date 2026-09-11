package net.subsloth.core.media

/**
 * Browser `<video>` element controls for autoplay-policy compliance on
 * Wasm.
 *
 * The player library creates the DOM element asynchronously after
 * `openUri`, so the first play command must wait for it. Autoplay
 * policies also check the element's `muted` flag (not its volume), so the
 * muted start must set it directly on the DOM element — the library
 * exposes no mute setter.
 *
 * Native platforms return the element-present default and no-op the mute:
 * their players start immediately and never hit the policy.
 */
internal expect fun hasBrowserVideoElement(): Boolean

internal expect fun setBrowserVideoMuted(muted: Boolean)
