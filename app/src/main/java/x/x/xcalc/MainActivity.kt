package x.x.xcalc

import android.os.Bundle
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import x.x.xcalc.ui.theme.XcalcTheme
import x.x.xcalc.vault.VaultScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

private data class CalcButton(
    val label: String,
    val icon: ImageVector? = null,
    val isOperator: Boolean = false,
    val isEmphasis: Boolean = false
)

@Composable
fun CalculatorScreen() {
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
            title = { Text("xcalc") },
            text = {
                Text(
                    "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    fontSize = 22.sp
                )
            },
            confirmButton = {
                Button(onClick = { showAbout = false }) {
                    Text("OK")
                }
            }
        )
    }

    if (showVault.value) {
        // Block screenshots and the recents preview while the vault is
        // visible; the calculator itself stays capturable to keep the disguise.
        val activity = LocalContext.current as? ComponentActivity
        DisposableEffect(Unit) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            onDispose {
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
        // Close the vault whenever the app is backgrounded so reopening from
        // recents lands on the calculator. A stop caused by launching an
        // external viewer from the vault is exempted once, or the viewer
        // would lose its temp file and the user their place.
        val externalViewActive = remember { mutableStateOf(false) }
        DisposableEffect(activity) {
            if (activity == null) return@DisposableEffect onDispose { }
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    if (externalViewActive.value) {
                        externalViewActive.value = false
                    } else {
                        showVault.value = false
                        backspaceTapCount = 0
                    }
                }
            }
            activity.lifecycle.addObserver(observer)
            onDispose { activity.lifecycle.removeObserver(observer) }
        }
        VaultScreen(
            onBack = {
                showVault.value = false
                backspaceTapCount = 0
            },
            onExternalView = { externalViewActive.value = true }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Bottom
    ) {
        DisplayArea(
            value = currentInput,
            history = history,
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
                            if (button.icon != null) {
                                backspaceTapCount = (backspaceTapCount + 1).coerceAtMost(2)
                            } else if (button.label != "backspace") {
                                backspaceTapCount = 0
                            }
                            engine.pressButton(button.label)
                            syncState()
                        }
                        CalcButtonView(
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp),
                            button = button,
                            onClick = onPress,
                            onLongPress = if (button.label == "=") {
                                {
                                    if (backspaceTapCount >= 2) {
                                        showVault.value = true
                                        backspaceTapCount = 0
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
            Text(
                text = value,
                style = MaterialTheme.typography.displayLarge,
                textAlign = TextAlign.End,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    contentDescription = button.label
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
