package x.x.xcalc.vault

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

enum class VaultState { PIN_SETUP, PIN_UNLOCK, FILE_LIST }

@Composable
fun VaultScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val pinManager = remember { PinManager(context) }
    val repository = remember { VaultRepository(context) }

    var state by remember {
        mutableStateOf(
            if (pinManager.hasPin) VaultState.PIN_UNLOCK else VaultState.PIN_SETUP
        )
    }

    when (state) {
        VaultState.PIN_SETUP -> {
            PinScreen(
                isSetup = true,
                onPinComplete = { pin ->
                    pinManager.setupPin(pin)
                    state = VaultState.FILE_LIST
                    true
                },
                onBack = onBack
            )
        }
        VaultState.PIN_UNLOCK -> {
            PinScreen(
                isSetup = false,
                onPinComplete = { pin ->
                    val ok = pinManager.verifyPin(pin)
                    if (ok) state = VaultState.FILE_LIST
                    ok
                },
                onBack = onBack
            )
        }
        VaultState.FILE_LIST -> {
            FileListScreen(
                repository = repository,
                onBack = onBack
            )
        }
    }
}
