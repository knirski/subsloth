package net.subsloth.preferences

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import net.subsloth.core.model.error.DecodeError
import net.subsloth.core.model.error.Outcome
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.File

class CredentialsStoreAdapterTest {

    private val store = CredentialStore()
    private val adapter = CredentialsStoreAdapter(store)

    @AfterEach
    fun cleanup() = runBlocking {
        store.clear()
        // Remove credential files left by the test
        val dir = File(System.getProperty("user.home"), ".subsloth")
        listOf("credentials.ks", "credentials.dat", ".mid").forEach { name ->
            File(dir, name).delete()
        }
    }

    @Test
    fun `save and read round-trip`() = runTest {
        adapter.save("alice", "secret")
        val result = adapter.read()
        when (result) {
            is Outcome.Success -> {
                assertThat(result.value).isNotNull()
                assertThat(result.value!!.login).isEqualTo("alice")
                assertThat(result.value!!.password).isEqualTo("secret")
            }
            is Outcome.Failure -> throw AssertionError("Expected success but got ${result.error}")
        }
    }

    @Test
    fun `read returns null when nothing stored`() = runTest {
        val result = adapter.read()
        when (result) {
            is Outcome.Success -> assertThat(result.value).isNull()
            is Outcome.Failure -> throw AssertionError("Expected success but got ${result.error}")
        }
    }

    @Test
    fun `overwrite replaces existing credentials`() = runTest {
        adapter.save("old", "creds")
        adapter.save("new", "creds")
        val result = adapter.read()
        when (result) {
            is Outcome.Success -> {
                assertThat(result.value!!.login).isEqualTo("new")
            }
            is Outcome.Failure -> throw AssertionError("Expected success but got ${result.error}")
        }
    }

    @Test
    fun `clear removes stored credentials`() = runTest {
        adapter.save("user", "pass")
        adapter.clear()
        val result = adapter.read()
        when (result) {
            is Outcome.Success -> assertThat(result.value).isNull()
            is Outcome.Failure -> throw AssertionError("Expected success but got ${result.error}")
        }
    }

    @Test
    fun `save then read returns correct credentials`() = runTest {
        adapter.save("multiline", "pass with spaces and !@#\$%^")
        val result = adapter.read()
        when (result) {
            is Outcome.Success -> {
                assertThat(result.value!!.login).isEqualTo("multiline")
                assertThat(result.value!!.password).isEqualTo("pass with spaces and !@#\$%^")
            }
            is Outcome.Failure -> throw AssertionError("Expected success but got ${result.error}")
        }
    }
}
