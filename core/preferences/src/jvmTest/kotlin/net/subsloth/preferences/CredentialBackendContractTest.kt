package net.subsloth.preferences

import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.io.TempDir
import java.io.File
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

abstract class CredentialBackendContractTest {

    abstract fun createBackend(tempDir: File): CredentialBackend

    @TempDir
    lateinit var tempDir: File

    private lateinit var backend: CredentialBackend

    @BeforeEach
    fun setUp() {
        backend = createBackend(tempDir)
    }

    @Test
    fun `save and load round trip`() {
        val data = "user@example.com\u0000secretPassword".toByteArray(Charsets.UTF_8)
        backend.save("credentials", data)
        val loaded = backend.load("credentials")
        assertTrue(loaded != null && data.contentEquals(loaded))
    }

    @Test
    fun `load returns null when no data`() {
        assertThat(backend.load("nonexistent")).isNull()
    }

    @Test
    fun `delete removes data`() {
        val data = "user@example.com\u0000secretPassword".toByteArray(Charsets.UTF_8)
        backend.save("credentials", data)
        backend.delete("credentials")
        assertThat(backend.load("credentials")).isNull()
    }

    @Test
    fun `overwrite replaces existing data`() {
        val data1 = "user1@example.com\u0000pass1".toByteArray(Charsets.UTF_8)
        val data2 = "user2@example.com\u0000pass2".toByteArray(Charsets.UTF_8)
        backend.save("credentials", data1)
        backend.save("credentials", data2)
        val loaded = backend.load("credentials")
        assertTrue(loaded != null && data2.contentEquals(loaded))
    }
}
