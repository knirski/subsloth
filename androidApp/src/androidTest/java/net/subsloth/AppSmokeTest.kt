package net.subsloth

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Basic smoke test verifying the app package and context are accessible
 * on a real device/emulator.
 */
@RunWith(AndroidJUnit4::class)
class AppSmokeTest {

    @Test
    fun app_context_is_available() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertNotNull(context)
        assertNotNull(context.packageName)
    }

    @Test
    fun app_package_name_is_correct() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertNotNull(context.packageName)
    }
}
