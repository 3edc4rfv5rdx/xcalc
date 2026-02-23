package x.x.xcalc

class CalculatorEngine {
    var currentInput: String = "0"
        private set
    var storedValue: Double? = null
        private set
    var pendingOp: String? = null
        private set
    var resetInput: Boolean = false
        private set
    var lastOp: String? = null
        private set
    var lastRight: Double? = null
        private set
    private val _history = mutableListOf<String>()
    val history: List<String> get() = _history

    fun formatNumber(value: Double): String {
        val asLong = value.toLong()
        return if (value == asLong.toDouble()) asLong.toString() else value.toString()
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
        val left = storedValue ?: currentInput.toDoubleOrNull() ?: return
        val right = if (pendingOp != null) {
            currentInput.toDoubleOrNull() ?: return
        } else {
            lastRight ?: return
        }
        val result = when (op) {
            "+" -> left + right
            "−" -> left - right
            "×" -> left * right
            "÷" -> if (right == 0.0) Double.NaN else left / right
            else -> right
        }
        val resultText = if (result.isNaN() || result.isInfinite()) {
            "Error"
        } else {
            formatNumber(result)
        }
        _history.add("${formatNumber(left)} $op ${formatNumber(right)} = $resultText")
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
}
