package net.subsloth

import net.subsloth.core.data.session.ValidatingSessionState

/**
 * Compatibility alias keeping androidApp call sites (and instrumented
 * tests) unchanged after the implementation moved to `:core:data` as
 * [ValidatingSessionState] — the class is fully platform-neutral, so
 * desktop now shares it too.
 */
typealias AndroidSessionState = ValidatingSessionState
