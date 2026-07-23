package x.x.xcalc.vault

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// PIN entry lives on the calculator screen itself (see CalculatorScreen);
// by the time this composes the user is already authenticated.
@Composable
fun VaultScreen(onBack: () -> Unit, onExternalActivity: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { VaultRepository.getInstance(context) }

    LaunchedEffect(Unit) {
        // Sweep decrypted temp files left over from a previous process
        // (e.g. it was killed while an external viewer was open) and
        // orphaned encrypted blobs. Once per process each; the orphan sweep
        // also pre-warms the metadata cache off the main thread.
        withContext(Dispatchers.IO) {
            repository.sweepTempOnce()
            repository.sweepOrphansOnce()
        }
    }

    FileListScreen(
        repository = repository,
        onBack = onBack,
        onExternalActivity = onExternalActivity
    )
}
