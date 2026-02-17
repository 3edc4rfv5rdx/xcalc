package x.x.xcalc.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

enum class PinMode { SETUP, CONFIRM, UNLOCK }

@Composable
fun PinScreen(
    isSetup: Boolean,
    onPinComplete: (String) -> Boolean,
    onBack: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var firstPin by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(if (isSetup) PinMode.SETUP else PinMode.UNLOCK) }
    var error by remember { mutableStateOf("") }
    var failCount by remember { mutableIntStateOf(0) }
    var cooldownUntil by remember { mutableLongStateOf(0L) }
    var cooldownRemaining by remember { mutableIntStateOf(0) }

    LaunchedEffect(cooldownUntil) {
        if (cooldownUntil > 0) {
            while (System.currentTimeMillis() < cooldownUntil) {
                cooldownRemaining = ((cooldownUntil - System.currentTimeMillis()) / 1000).toInt() + 1
                delay(1000)
            }
            cooldownRemaining = 0
            cooldownUntil = 0
        }
    }

    val title = when (mode) {
        PinMode.SETUP -> "Set PIN"
        PinMode.CONFIRM -> "Confirm PIN"
        PinMode.UNLOCK -> "Enter PIN"
    }

    fun onDigit(digit: String) {
        if (cooldownRemaining > 0) return
        if (pin.length < 8) {
            pin += digit
            error = ""
        }
    }

    fun onSubmit() {
        if (cooldownRemaining > 0) return
        if (pin.length < 4) {
            error = "Min 4 digits"
            return
        }
        when (mode) {
            PinMode.SETUP -> {
                firstPin = pin
                pin = ""
                mode = PinMode.CONFIRM
            }
            PinMode.CONFIRM -> {
                if (pin == firstPin) {
                    if (!onPinComplete(pin)) {
                        error = "Error"
                        pin = ""
                    }
                } else {
                    error = "PIN mismatch"
                    pin = ""
                    mode = PinMode.SETUP
                    firstPin = ""
                }
            }
            PinMode.UNLOCK -> {
                if (onPinComplete(pin)) {
                    // success
                } else {
                    failCount++
                    error = "Wrong PIN"
                    pin = ""
                    if (failCount >= 3) {
                        cooldownUntil = System.currentTimeMillis() + 30_000
                        failCount = 0
                        error = ""
                    }
                }
            }
        }
    }

    fun onBackspace() {
        if (cooldownRemaining > 0) return
        if (pin.isNotEmpty()) {
            pin = pin.dropLast(1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(24.dp))

        // PIN dots (up to 8)
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(8) { i ->
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(
                            if (i < pin.length) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (error.isNotEmpty()) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (cooldownRemaining > 0) {
            Text(
                text = "Wait ${cooldownRemaining}s",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Number pad (3 columns, calculator layout)
        val buttons = listOf(
            listOf("7", "8", "9"),
            listOf("4", "5", "6"),
            listOf("1", "2", "3"),
            listOf("0", "⌫", "=")
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            buttons.forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    row.forEach { label ->
                        PinButton(
                            label = label,
                            onClick = {
                                when (label) {
                                    "⌫" -> if (pin.isEmpty()) onBack() else onBackspace()
                                    "=" -> onSubmit()
                                    else -> onDigit(label)
                                }
                            },
                            isAction = label == "⌫" || label == "="
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PinButton(
    label: String,
    onClick: () -> Unit,
    isAction: Boolean = false
) {
    val containerColor = if (isAction) {
        MaterialTheme.colorScheme.primary
    } else {
        Color(0xFFFFD54F)
    }
    val contentColor = if (isAction) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        Color(0xFF3D2F00)
    }

    Surface(
        modifier = Modifier
            .size(72.dp)
            .clickable(onClick = onClick),
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            if (label == "⌫") {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "Backspace"
                )
            } else {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 33.sp
                    ),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
