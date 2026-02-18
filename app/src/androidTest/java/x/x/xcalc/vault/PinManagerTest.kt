package x.x.xcalc.vault

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PinManagerTest {

    private lateinit var pinManager: PinManager

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Clear pin prefs before each test
        context.getSharedPreferences("vault_pin_prefs", 0).edit().clear().apply()
        // Also delete the encrypted prefs file to ensure clean state
        val prefsDir = java.io.File(context.filesDir.parentFile, "shared_prefs")
        prefsDir.listFiles()?.filter { it.name.startsWith("vault_pin_prefs") }?.forEach { it.delete() }
        pinManager = PinManager(context)
    }

    @Test
    fun hasPinInitiallyFalse() {
        assertFalse(pinManager.hasPin)
    }

    @Test
    fun setupPinThenHasPin() {
        pinManager.setupPin("1234")
        assertTrue(pinManager.hasPin)
    }

    @Test
    fun verifyCorrectPin() {
        pinManager.setupPin("5678")
        assertTrue(pinManager.verifyPin("5678"))
    }

    @Test
    fun verifyWrongPin() {
        pinManager.setupPin("1234")
        assertFalse(pinManager.verifyPin("0000"))
        assertFalse(pinManager.verifyPin("1235"))
        assertFalse(pinManager.verifyPin("9999"))
    }

    @Test
    fun changePinInvalidatesOld() {
        pinManager.setupPin("1111")
        assertTrue(pinManager.verifyPin("1111"))
        pinManager.setupPin("2222")
        assertFalse(pinManager.verifyPin("1111"))
        assertTrue(pinManager.verifyPin("2222"))
    }
}
