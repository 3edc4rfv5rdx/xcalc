package x.x.xcalc

import android.os.Bundle
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import x.x.xcalc.BuildConfig
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
    var renderTick by remember { mutableIntStateOf(0) }
    val showVault = remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var backspaceTapCount by remember { mutableIntStateOf(0) }

    // Read engine state (renderTick forces recomposition)
    @Suppress("UNUSED_EXPRESSION")
    renderTick
    val currentInput = engine.currentInput
    val history = engine.history

    val rows = listOf(
        listOf(
            CalcButton("AC", isEmphasis = true),
            CalcButton("C", isEmphasis = true),
            CalcButton("%", isOperator = true),
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
                Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) {
                    Text("OK")
                }
            }
        )
    }

    if (showVault.value) {
        VaultScreen(onBack = {
            showVault.value = false
            @Suppress("ASSIGNED_VALUE_IS_NEVER_READ")
            backspaceTapCount = 0
        })
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
                            renderTick++
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
    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { onLongPress() })
            }
            .padding(20.dp),
        contentAlignment = Alignment.BottomEnd
    ) {
        Column(horizontalAlignment = Alignment.End) {
            history.forEach { entry ->
                Text(
                    text = entry,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 28.sp),
                    textAlign = TextAlign.End,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (history.isNotEmpty()) {
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
            containerColor = Color(0xFFE9967A) // color operators
            contentColor = Color(0xFF000000)
        }
        else -> {
            containerColor = Color(0xFFFFD54F) // color digits
            contentColor = Color(0xFF3D2F00)
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val scope = rememberCoroutineScope()
    val pressModifier = if (onLongPress != null) {
        Modifier.pointerInput(onLongPress, onClick) {
            detectTapGestures(
                onTap = { onClick() },
                onPress = {
                    val job = scope.launch {
                        delay(5000)
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
