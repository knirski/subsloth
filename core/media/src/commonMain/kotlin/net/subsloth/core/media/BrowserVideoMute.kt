package net.subsloth.core.media

/**
 * Mutes or unmutes the browser's `<video>` element(s) for autoplay-policy
 * compliance on Wasm.
 *
 * Autoplay policies check the element's `muted` flag, not its volume, so
 * the muted retry in `PlayerBridgeSurface` must set it directly on the
 * DOM element (the player library does not expose a mute setter).
 *
 * Native platforms are no-ops: their players start immediately and never
 * hit the policy.
 */
internal expect fun setBrowserVideoMuted(muted: Boolean)
