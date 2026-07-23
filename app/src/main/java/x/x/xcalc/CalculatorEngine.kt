package x.x.xcalc

import java.math.BigDecimal
import java.math.RoundingMode

class CalculatorEngine {
    private companion object {
        const val MAX_INPUT_DIGITS = 15
        const val MAX_HISTORY = 100
    }

    var currentInput: String = "0"
        private set
    var storedValue: BigDecimal? = null
        private set
    var pendingOp: String? = null
        private set
    var resetInput: Boolean = false
        private set
    var lastOp: String? = null
        private set
    var lastRight: BigDecimal? = null
        private set
    private val _history = mutableListOf<String>()
    val history: List<String> get() = _history

    private fun formatNumber(value: BigDecimal): String {
        return value.stripTrailingZeros().toPlainString()
    }

    private fun parseInput(): BigDecimal? {
        return currentInput.toBigDecimalOrNull()
    }

    private fun toggleSign() {
        if (resetInput && pendingOp != null) {
            currentInput = "-0"
            resetInput = false
            return
        }
        // Toggle textually to preserve in-progress input like "5.0".
        currentInput = if (currentInput.startsWith("-")) {
            currentInput.substring(1)
        } else {
            "-$currentInput"
        }
        // Keep resetInput as-is: toggling a just-computed result must not
        // switch the next digit press into append mode.
    }

    fun resetAll() {
        currentInput = "0"
        storedValue = null
        pendingOp = null
        resetInput = false
        lastOp = null
        lastRight = null
        _history.clear()
    }

    fun applyEquals() {
        val op = pendingOp ?: lastOp ?: return
        val left = storedValue ?: parseInput() ?: return
        val right = if (pendingOp != null) {
            parseInput() ?: return
        } else {
            lastRight ?: return
        }
        val result = when (op) {
            "+" -> left + right
            "−" -> left - right
            "×" -> left * right
            "÷" -> if (right.compareTo(BigDecimal.ZERO) == 0) null else left.divide(right, 16, RoundingMode.HALF_UP)
            else -> right
        }
        val resultText = result?.let(::formatNumber) ?: "Error"
        _history.add("${formatNumber(left)} $op ${formatNumber(right)} = $resultText")
        if (_history.size > MAX_HISTORY) _history.removeAt(0)
        currentInput = resultText
        lastOp = op
        lastRight = right
        storedValue = null
        pendingOp = null
        resetInput = true
    }

    fun pressButton(label: String) {
        if (currentInput == "Error" && label !in listOf("AC", "C")) {
            resetAll()
        }
        when {
            label == "AC" -> resetAll()
            label == "C" -> {
                currentInput = "0"
                resetInput = false
            }
            label == "backspace" -> {
                if (resetInput) {
                    if (pendingOp == null) {
                        currentInput = "0"
                        resetInput = false
                    }
                } else {
                    currentInput = currentInput.dropLast(1)
                    if (currentInput.isEmpty() || currentInput == "-") {
                        currentInput = "0"
                    }
                }
            }
            label in listOf("+", "−", "×", "÷") -> {
                val current = parseInput() ?: BigDecimal.ZERO
                if (pendingOp != null && !resetInput) {
                    applyEquals()
                    storedValue = parseInput()
                    lastOp = null
                    lastRight = null
                } else if (storedValue == null) {
                    storedValue = current
                }
                pendingOp = label
                resetInput = true
            }
            label == "=" -> {
                applyEquals()
            }
            label == "+/-" -> toggleSign()
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
                } else if (currentInput == "-0") {
                    currentInput = "-$label"
                } else if (currentInput.count { it.isDigit() } < MAX_INPUT_DIGITS) {
                    currentInput += label
                }
                resetInput = false
            }
        }
    }
}
