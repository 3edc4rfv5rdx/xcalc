package x.x.xcalc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import x.x.xcalc.ui.theme.XcalcTheme

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
    var currentInput by remember { mutableStateOf("0") }
    var storedValue by remember { mutableStateOf<Double?>(null) }
    var pendingOp by remember { mutableStateOf<String?>(null) }
    var resetInput by remember { mutableStateOf(false) }
    val showEqualsConfirm = remember { mutableStateOf(false) }

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

    fun formatNumber(value: Double): String {
        val asLong = value.toLong()
        return if (value == asLong.toDouble()) asLong.toString() else value.toString()
    }

    fun resetAll() {
        currentInput = "0"
        storedValue = null
        pendingOp = null
        resetInput = false
    }

    fun applyEquals() {
        val op = pendingOp ?: return
        val left = storedValue ?: return
        val right = currentInput.toDoubleOrNull() ?: return
        val result = when (op) {
            "+" -> left + right
            "−" -> left - right
            "×" -> left * right
            "÷" -> if (right == 0.0) Double.NaN else left / right
            else -> right
        }
        if (result.isNaN() || result.isInfinite()) {
            currentInput = "Error"
        } else {
            currentInput = formatNumber(result)
        }
        storedValue = null
        pendingOp = null
        resetInput = true
    }

    if (showEqualsConfirm.value) {
        AlertDialog(
            onDismissRequest = { showEqualsConfirm.value = false },
            title = { Text("Confirm") },
            text = { Text("Apply '=' ?") },
            confirmButton = {
                Button(onClick = {
                    showEqualsConfirm.value = false
                }) { Text("Ok") }
            },
            dismissButton = {
                Button(onClick = { showEqualsConfirm.value = false }) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        DisplayArea(
            value = currentInput,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(2f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    row.forEach { button ->
                        val onPress: () -> Unit = {
                            val label = button.label
                            if (currentInput == "Error" && label !in listOf("AC", "C")) {
                                resetAll()
                            }
                            when {
                                label == "AC" -> resetAll()
                                label == "C" -> {
                                    currentInput = "0"
                                    resetInput = false
                                }
                                button.icon != null -> {
                                    if (resetInput) {
                                        currentInput = "0"
                                        resetInput = false
                                    } else {
                                        currentInput = currentInput.dropLast(1)
                                        if (currentInput.isEmpty() || currentInput == "-") {
                                            currentInput = "0"
                                        }
                                    }
                                }
                                label in listOf("+", "−", "×", "÷") -> {
                                    val current = currentInput.toDoubleOrNull() ?: 0.0
                                    if (pendingOp != null && !resetInput) {
                                        applyEquals()
                                        storedValue = currentInput.toDoubleOrNull()
                                    } else if (storedValue == null) {
                                        storedValue = current
                                    }
                                    pendingOp = label
                                    resetInput = true
                                }
                                label == "=" -> applyEquals()
                                label == "%" -> {
                                    val current = currentInput.toDoubleOrNull() ?: 0.0
                                    val base = storedValue
                                    val percentValue = if (base != null) base * current / 100.0 else current / 100.0
                                    currentInput = formatNumber(percentValue)
                                    resetInput = false
                                }
                                label == "." -> {
                                    if (resetInput) {
                                        currentInput = "0."
                                        resetInput = false
                                    } else if (!currentInput.contains(".")) {
                                        currentInput += "."
                                    }
                                }
                                label.all { it.isDigit() } -> {
                                    if (resetInput || currentInput == "0") {
                                        currentInput = label
                                    } else {
                                        currentInput += label
                                    }
                                    resetInput = false
                                }
                            }
                        }
                        CalcButtonView(
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp),
                            button = button,
                            onClick = onPress,
                            onLongPress = if (button.label == "=") {
                                { showEqualsConfirm.value = true }
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
private fun DisplayArea(value: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(20.dp),
        contentAlignment = Alignment.BottomEnd
    ) {
        Column(horizontalAlignment = Alignment.End) {
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
    Surface(
        modifier = modifier.combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            role = Role.Button,
            onClick = onClick,
            onLongClick = onLongPress
        ),
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
