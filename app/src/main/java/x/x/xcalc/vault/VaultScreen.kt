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
fun VaultScreen(onBack: () -> Unit, onExternalActivity: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { VaultRepository.getInstance(context) }

    // PinManager is normally already warmed by the preload started on the
    // second backspace tap (see CalculatorScreen); keep the off-main-thread
    // fetch as a fallback so a cold start never blocks the UI.
    var pinManager by remember { mutableStateOf<PinManager?>(null) }
    var state by remember { mutableStateOf<VaultState?>(null) }

    LaunchedEffect(Unit) {
        val pm = withContext(Dispatchers.IO) { PinManager.getInstance(context) }
        pinManager = pm
        state = if (pm.hasPin) VaultState.PIN_UNLOCK else VaultState.PIN_SETUP
        // Sweep decrypted temp files left over from a previous process
        // (e.g. it was killed while an external viewer was open) and
        // orphaned encrypted blobs. Once per process each; the orphan sweep
        // also pre-warms the metadata cache off the main thread.
        withContext(Dispatchers.IO) {
            repository.sweepTempOnce()
            repository.sweepOrphansOnce()
        }
    }

    val pm = pinManager
    val st = state
    if (pm == null || st == null) {
        // Blank vault-colored placeholder while PinManager initializes.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        )
        return
    }

    when (st) {
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
                onExternalActivity = onExternalActivity
            )
        }
    }
}
