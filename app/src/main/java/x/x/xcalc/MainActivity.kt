package x.x.xcalc

import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import x.x.xcalc.BuildConfig
import x.x.xcalc.ui.theme.DigitButton
import x.x.xcalc.ui.theme.DigitButtonContent
import x.x.xcalc.ui.theme.OperatorButton
import x.x.xcalc.ui.theme.OperatorButtonContent
import x.x.xcalc.ui.theme.PinModeDisplay
import x.x.xcalc.ui.theme.XcalcTheme
import x.x.xcalc.vault.PinManager
import x.x.xcalc.vault.VaultScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            XcalcTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CalculatorScreen()
                }
            }
        }
    }
}

// Coming back from an external activity (viewer, SAF picker) later than
// this relocks the vault.
private const val EXTERNAL_ACTIVITY_RELOCK_MS = 60_000L

private const val PIN_MIN_LENGTH = 4
private const val PIN_MAX_LENGTH = 8

// How long the first-time setup verdict ("Ok"/"Error") stays on the display.
private const val PIN_VERDICT_MS = 2_000L

// PIN entry happens on the calculator keypad itself — no separate screen
// that would give the vault away. The only mode hint is the display color.
private enum class PinStage { UNLOCK, SETUP, CONFIRM }

private data class CalcButton(
    val label: String,
    val icon: ImageVector? = null,
    val isOperator: Boolean = false,
    val isEmphasis: Boolean = false
)

@Composable
fun CalculatorScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine = remember { CalculatorEngine() }
    val showVault = remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var backspaceTapCount by remember { mutableIntStateOf(0) }

    var currentInput by remember { mutableStateOf(engine.currentInput) }
    var history by remember { mutableStateOf(engine.history.toList()) }

    fun syncState() {
        currentInput = engine.currentInput
        history = engine.history.toList()
    }

    // PIN entry state: the calculator keypad doubles as the PIN pad.
    var pinMode by remember { mutableStateOf(false) }
    var pinStage by remember { mutableStateOf<PinStage?>(null) }
    var pinBuffer by remember { mutableStateOf("") }
    var pinFirst by remember { mutableStateOf("") }
    // Blocks input while the PBKDF2 hash runs in the background.
    var pinBusy by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf(false) }
    // Shows "Ok" after a successful first-time setup.
    var pinOk by remember { mutableStateOf(false) }
    var pinCooldownUntil by remember { mutableLongStateOf(0L) }
    var pinCooldownRemaining by remember { mutableIntStateOf(0) }

    fun exitPinMode() {
        pinMode = false
        pinStage = null
        pinBuffer = ""
        pinFirst = ""
        pinBusy = false
        pinError = false
        pinOk = false
        pinCooldownUntil = 0
        pinCooldownRemaining = 0
    }

    LaunchedEffect(pinCooldownUntil) {
        if (pinCooldownUntil > 0) {
            while (System.currentTimeMillis() < pinCooldownUntil) {
                pinCooldownRemaining =
                    ((pinCooldownUntil - System.currentTimeMillis()) / 1000).toInt() + 1
                delay(1000)
            }
            pinCooldownRemaining = 0
            pinCooldownUntil = 0
        }
    }

    fun submitPin() {
        val stage = pinStage ?: return
        if (pinBuffer.length < PIN_MIN_LENGTH) {
            pinError = true
            pinBuffer = ""
            return
        }
        when (stage) {
            PinStage.SETUP -> {
                pinFirst = pinBuffer
                pinBuffer = ""
                pinStage = PinStage.CONFIRM
            }
            PinStage.CONFIRM -> {
                // The setup verdict shows for 2 seconds; "Ok" then opens the
                // vault directly, "Error" drops back to the plain calculator.
                if (pinBuffer == pinFirst) {
                    val pin = pinBuffer
                    scope.launch {
                        pinBusy = true
                        // PBKDF2 is CPU-heavy; keep it off the main thread.
                        withContext(Dispatchers.Default) {
                            PinManager.getInstance(context).setupPin(pin)
                        }
                        pinOk = true
                        delay(PIN_VERDICT_MS)
                        exitPinMode()
                        showVault.value = true
                    }
                } else {
                    pinError = true
                    pinBuffer = ""
                    scope.launch {
                        pinBusy = true
                        delay(PIN_VERDICT_MS)
                        exitPinMode()
                    }
                }
            }
            PinStage.UNLOCK -> {
                val pin = pinBuffer
                scope.launch {
                    pinBusy = true
                    // Instance is already cached from entering PIN mode.
                    val pm = PinManager.getInstance(context)
                    // PBKDF2 is CPU-heavy; keep it off the main thread.
                    val ok = withContext(Dispatchers.Default) { pm.verifyPin(pin) }
                    if (ok) {
                        withContext(Dispatchers.IO) { pm.clearFailures() }
                        exitPinMode()
                        showVault.value = true
                    } else {
                        // Synchronous prefs write — keep it off the main thread.
                        withContext(Dispatchers.IO) { pm.registerFailedAttempt() }
                        pinBuffer = ""
                        pinBusy = false
                        pinCooldownUntil = pm.cooldownUntil
                        // Cooldown countdown replaces the error display.
                        pinError = System.currentTimeMillis() >= pinCooldownUntil
                    }
                }
            }
        }
    }

    fun pressPinKey(button: CalcButton) {
        if (pinBusy) return
        when {
            button.icon != null -> {
                // Backspace: delete one PIN digit, leave on an empty buffer.
                if (pinBuffer.isEmpty()) exitPinMode()
                else pinBuffer = pinBuffer.dropLast(1)
            }
            button.label == "=" -> {
                if (pinCooldownRemaining == 0) submitPin()
            }
            button.label.all { it.isDigit() } -> {
                if (pinCooldownRemaining == 0 && pinBuffer.length < PIN_MAX_LENGTH) {
                    pinError = false
                    pinBuffer += button.label
                }
            }
            else -> {
                // Any other key drops back to the plain calculator and
                // applies normally, keeping the disguise.
                exitPinMode()
                engine.pressButton(button.label)
                syncState()
            }
        }
    }

    val rows = listOf(
        listOf(
            CalcButton("AC", isEmphasis = true),
            CalcButton("C", isEmphasis = true),
            CalcButton("+/-", isOperator = true),
            CalcButton("÷", isOperator = true)
        ),
        listOf(
            CalcButton("7"),
            CalcButton("8"),
            CalcButton("9"),
            CalcButton("×", isOperator = true)
        ),
        listOf(
            CalcButton("4"),
            CalcButton("5"),
            CalcButton("6"),
            CalcButton("−", isOperator = true)
        ),
        listOf(
            CalcButton("1"),
            CalcButton("2"),
            CalcButton("3"),
            CalcButton("+", isOperator = true)
        ),
        listOf(
            CalcButton("0"),
            CalcButton("."),
            CalcButton("backspace", icon = Icons.AutoMirrored.Filled.Backspace, isEmphasis = true),
            CalcButton("=", isOperator = true, isEmphasis = true)
        )
    )

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text(stringResource(R.string.app_name)) },
            text = {
                Text(
                    "${stringResource(R.string.version)} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    fontSize = 22.sp
                )
            },
            confirmButton = {
                Button(onClick = { showAbout = false }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }

    if (showVault.value || pinMode) {
        // Block screenshots and the recents preview while the vault or PIN
        // entry is visible; the plain calculator stays capturable to keep
        // the disguise.
        val activity = LocalContext.current as? ComponentActivity
        DisposableEffect(Unit) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            onDispose {
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
        // Close the vault (and abandon PIN entry) whenever the app is
        // backgrounded so reopening from recents lands on the calculator.
        // A stop caused by launching an external activity from the vault
        // (viewer, SAF picker) is exempted once, or the viewer would lose
        // its temp file, and pickers their pending import/export.
        val externalActivityActive = remember { mutableStateOf(false) }
        // Set at the exempted stop: lifecycle alone cannot tell "came back
        // from the external activity" apart from "left it via Home and
        // reopened the app later", so on ON_START relock unless the return
        // was quick.
        val externalActivityStopTime = remember { mutableLongStateOf(0L) }
        DisposableEffect(activity) {
            if (activity == null) return@DisposableEffect onDispose { }
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> {
                        if (externalActivityActive.value) {
                            externalActivityActive.value = false
                            externalActivityStopTime.longValue = SystemClock.elapsedRealtime()
                        } else {
                            showVault.value = false
                            exitPinMode()
                            backspaceTapCount = 0
                        }
                    }
                    Lifecycle.Event.ON_START -> {
                        if (externalActivityStopTime.longValue > 0) {
                            val elapsed = SystemClock.elapsedRealtime() - externalActivityStopTime.longValue
                            externalActivityStopTime.longValue = 0
                            if (elapsed > EXTERNAL_ACTIVITY_RELOCK_MS) {
                                showVault.value = false
                                exitPinMode()
                                backspaceTapCount = 0
                            }
                        }
                    }
                    else -> {}
                }
            }
            activity.lifecycle.addObserver(observer)
            onDispose { activity.lifecycle.removeObserver(observer) }
        }
        if (showVault.value) {
            VaultScreen(
                onBack = {
                    showVault.value = false
                    backspaceTapCount = 0
                },
                onExternalActivity = { externalActivityActive.value = true }
            )
            return
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Bottom
    ) {
        // In PIN mode the display shows the cooldown countdown, "Error", the
        // real digits during first-time setup (a one-off private flow;
        // "Pin1"/"Pin2" label the two setup entries), or one zero per typed
        // digit on unlock. The orange value color is the only hint that the
        // calculator is in PIN mode.
        val pinDisplayValue = when {
            pinCooldownRemaining > 0 -> pinCooldownRemaining.toString()
            pinError -> "Error"
            pinOk -> "Ok"
            pinStage == PinStage.SETUP -> pinBuffer.ifEmpty { "Pin1" }
            pinStage == PinStage.CONFIRM -> pinBuffer.ifEmpty { "Pin2" }
            else -> "0".repeat(pinBuffer.length).ifEmpty { "0" }
        }
        DisplayArea(
            value = if (pinMode) pinDisplayValue else currentInput,
            history = history,
            valueColor = if (pinMode) PinModeDisplay
            else MaterialTheme.colorScheme.onSurfaceVariant,
            onLongPress = { showAbout = true },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    row.forEach { button ->
                        val onPress: () -> Unit = {
                            if (pinMode) {
                                pressPinKey(button)
                            } else {
                                if (button.icon != null) {
                                    backspaceTapCount = (backspaceTapCount + 1).coerceAtMost(2)
                                    if (backspaceTapCount == 2) {
                                        // Warm the slow PinManager init during
                                        // the upcoming "=" hold so PIN entry
                                        // starts right when the long-press fires.
                                        scope.launch(Dispatchers.IO) {
                                            PinManager.getInstance(context)
                                        }
                                    }
                                } else if (button.label != "backspace") {
                                    backspaceTapCount = 0
                                }
                                engine.pressButton(button.label)
                                syncState()
                            }
                        }
                        CalcButtonView(
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp),
                            button = button,
                            onClick = onPress,
                            onLongPress = if (button.label == "=") {
                                {
                                    if (!pinMode && backspaceTapCount >= 2) {
                                        backspaceTapCount = 0
                                        pinMode = true
                                        scope.launch {
                                            val pm = withContext(Dispatchers.IO) {
                                                PinManager.getInstance(context)
                                            }
                                            pinStage =
                                                if (pm.hasPin) PinStage.UNLOCK else PinStage.SETUP
                                            pinCooldownUntil = pm.cooldownUntil
                                        }
                                    }
                                }
                            } else null
                        )
                    }

                    if (row.size == 3) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DisplayArea(
    value: String,
    history: List<String>,
    valueColor: Color,
    onLongPress: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val historyScrollState = rememberScrollState()
    LaunchedEffect(history.size) {
        historyScrollState.scrollTo(historyScrollState.maxValue)
    }

    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { onLongPress() })
            }
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Bottom
        ) {
            if (history.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(historyScrollState),
                    horizontalAlignment = Alignment.End
                ) {
                    history.forEach { entry ->
                        Text(
                            text = entry,
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 28.sp),
                            textAlign = TextAlign.End,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            // Long results shrink to stay on one line instead of wrapping.
            val baseStyle = MaterialTheme.typography.displayLarge
            var fontScale by remember(value) { mutableFloatStateOf(1f) }
            Text(
                text = value,
                style = baseStyle,
                fontSize = baseStyle.fontSize * fontScale,
                textAlign = TextAlign.End,
                color = valueColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                onTextLayout = { result ->
                    if (result.didOverflowWidth && fontScale > 0.35f) {
                        fontScale *= 0.9f
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun CalcButtonView(
    modifier: Modifier,
    button: CalcButton,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null
) {
    val containerColor: Color
    val contentColor: Color
    when {
        button.isEmphasis -> {
            containerColor = MaterialTheme.colorScheme.primary
            contentColor = MaterialTheme.colorScheme.onPrimary
        }
        button.isOperator -> {
            containerColor = OperatorButton
            contentColor = OperatorButtonContent
        }
        else -> {
            containerColor = DigitButton
            contentColor = DigitButtonContent
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val scope = rememberCoroutineScope()
    val pressModifier = if (onLongPress != null) {
        Modifier.pointerInput(onLongPress, onClick) {
            // Suppress the tap that fires on release after the long-press
            // action has already run.
            var longPressFired = false
            detectTapGestures(
                onTap = { if (!longPressFired) onClick() },
                onPress = {
                    longPressFired = false
                    val job = scope.launch {
                        delay(5000)
                        longPressFired = true
                        onLongPress()
                    }
                    tryAwaitRelease()
                    job.cancel()
                }
            )
        }
    } else {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            role = Role.Button,
            onClick = onClick
        )
    }

    Surface(
        modifier = modifier.then(pressModifier),
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (button.icon != null) {
                Icon(
                    imageVector = button.icon,
                    contentDescription = stringResource(R.string.backspace)
                )
            } else {
                Text(
                    text = button.label,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 33.sp
                    )
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CalculatorPreview() {
    XcalcTheme {
        CalculatorScreen()
    }
}
