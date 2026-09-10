package net.subsloth.core.domain.policy

import net.subsloth.core.domain.LoginDefaults
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ApiBaseUrlPolicyTest {
    private val custom = "https://custom.example.com/api/"
    private val configured = "https://configured.example.com/api/"

    // ── Saved preference presence wins ────────────────────────────────────

    @Test
    fun `saved custom url wins over configured override`() {
        assertEquals(custom, ApiBaseUrlPolicy.resolve(stored = custom, configured = configured))
    }

    @Test
    fun `deliberately saved default url wins over configured override`() {
        assertEquals(
            LoginDefaults.DEFAULT_API_BASE_URL,
            ApiBaseUrlPolicy.resolve(stored = LoginDefaults.DEFAULT_API_BASE_URL, configured = configured),
        )
    }

    @Test
    fun `saved url wins when no configured override exists`() {
        assertEquals(custom, ApiBaseUrlPolicy.resolve(stored = custom, configured = null))
    }

    // ── Absent or blank preference falls back to configured ──────────────

    @Test
    fun `absent preference falls back to configured override`() {
        assertEquals(configured, ApiBaseUrlPolicy.resolve(stored = null, configured = configured))
    }

    @Test
    fun `blank stored url falls back to configured override`() {
        assertEquals(configured, ApiBaseUrlPolicy.resolve(stored = "", configured = configured))
    }

    @Test
    fun `whitespace-only stored url falls back to configured override`() {
        assertEquals(configured, ApiBaseUrlPolicy.resolve(stored = "   ", configured = configured))
    }

    // ── Fallback to the hard-coded default ───────────────────────────────

    @Test
    fun `no stored and no configured value falls back to default`() {
        assertEquals(LoginDefaults.DEFAULT_API_BASE_URL, ApiBaseUrlPolicy.resolve(stored = null, configured = null))
    }

    @Test
    fun `blank stored and blank configured value falls back to default`() {
        assertEquals(LoginDefaults.DEFAULT_API_BASE_URL, ApiBaseUrlPolicy.resolve(stored = "", configured = ""))
    }

    @Test
    fun `blank configured override is ignored when stored value exists`() {
        assertEquals(custom, ApiBaseUrlPolicy.resolve(stored = custom, configured = ""))
    }
}
