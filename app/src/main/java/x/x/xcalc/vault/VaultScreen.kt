package x.x.xcalc.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class VaultState { PIN_SETUP, PIN_UNLOCK, FILE_LIST }

@Composable
fun VaultScreen(onBack: () -> Unit, onExternalView: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { VaultRepository.getInstance(context) }

    // PinManager construction (keystore master key + EncryptedSharedPreferences)
    // takes seconds on first use; build it off the main thread so the vault
    // appears immediately after the long-press gesture.
    var pinManager by remember { mutableStateOf<PinManager?>(null) }
    var state by remember { mutableStateOf<VaultState?>(null) }

    LaunchedEffect(Unit) {
        val pm = withContext(Dispatchers.IO) { PinManager(context) }
        pinManager = pm
        state = if (pm.hasPin) VaultState.PIN_UNLOCK else VaultState.PIN_SETUP
        // Sweep decrypted temp files left over from a previous process
        // (e.g. it was killed while an external viewer was open). Once per
        // process — see sweepTempOnce.
        withContext(Dispatchers.IO) {
            repository.sweepTempOnce()
        }
    }

    val pm = pinManager
    if (pm == null || state == null) {
        // Blank vault-colored placeholder while PinManager initializes.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        )
        return
    }

    when (state) {
        VaultState.PIN_SETUP -> {
            PinScreen(
                isSetup = true,
                pinManager = pm,
                onPinComplete = { pin ->
                    // PBKDF2 is CPU-heavy; keep it off the main thread.
                    withContext(Dispatchers.Default) { pm.setupPin(pin) }
                    state = VaultState.FILE_LIST
                    true
                },
                onBack = onBack
            )
        }
        VaultState.PIN_UNLOCK -> {
            PinScreen(
                isSetup = false,
                pinManager = pm,
                onPinComplete = { pin ->
                    // PBKDF2 is CPU-heavy; keep it off the main thread.
                    val ok = withContext(Dispatchers.Default) { pm.verifyPin(pin) }
                    if (ok) {
                        pm.clearFailures()
                        state = VaultState.FILE_LIST
                    }
                    ok
                },
                onBack = onBack
            )
        }
        VaultState.FILE_LIST -> {
            FileListScreen(
                repository = repository,
                onBack = onBack,
                onExternalView = onExternalView
            )
        }
    }
}
