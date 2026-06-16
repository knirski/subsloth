package net.subsloth.buildlogic.detekt

import dev.detekt.test.TestConfig
import dev.detekt.test.lint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NoForceUnwrapTest {
    @Test
    fun `reports force unwrap in production source set`() {
        val findings = NoForceUnwrap(TestConfig()).lint(
            """
            fun main() {
                val s: String? = "x"
                println(s!!.length)
            }
            """.trimIndent(),
        )
        assertEquals(1, findings.size, "expected one !!-finding, got: $findings")
    }

    @Test
    fun `does not report safe calls`() {
        val findings = NoForceUnwrap(TestConfig()).lint(
            """
            fun main() {
                val s: String? = "x"
                println(s?.length)
            }
            """.trimIndent(),
        )
        assertTrue(findings.isEmpty(), "expected no findings, got: $findings")
    }

    @Test
    fun `reports multiple force unwraps`() {
        val findings = NoForceUnwrap(TestConfig()).lint(
            """
            fun main() {
                val a: String? = "a"
                val b: String? = "b"
                println(a!!.length + b!!.length)
            }
            """.trimIndent(),
        )
        assertEquals(2, findings.size, "expected two findings, got: $findings")
    }
}
