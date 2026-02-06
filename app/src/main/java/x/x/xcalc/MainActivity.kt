package x.x.xcalc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
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
    val rows = listOf(
        listOf(
            CalcButton("AC", isEmphasis = true),
            CalcButton("DEL", isEmphasis = true),
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
            CalcButton("backspace", icon = Icons.Filled.Backspace, isEmphasis = true),
            CalcButton("=", isOperator = true, isEmphasis = true)
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        DisplayArea(
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
                        CalcButtonView(
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp),
                            button = button,
                            onClick = { /* TODO: connect logic */ }
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
private fun DisplayArea(modifier: Modifier = Modifier) {
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
                text = "0",
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
    onClick: () -> Unit
) {
    val colors = when {
        button.isEmphasis -> ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )

        button.isOperator -> ButtonDefaults.buttonColors(
            containerColor = Color(0xFFE9967A), // color operators
            contentColor = Color(0xFF000000)
        )

        else -> ButtonDefaults.buttonColors(
            containerColor = Color(0xFFFFD54F), // color digits
            contentColor = Color(0xFF3D2F00)
        )
    }

    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = colors,
        contentPadding = PaddingValues(0.dp)
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

@Preview(showBackground = true)
@Composable
fun CalculatorPreview() {
    XcalcTheme {
        CalculatorScreen()
    }
}
