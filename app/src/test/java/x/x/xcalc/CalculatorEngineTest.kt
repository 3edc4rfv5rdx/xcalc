package x.x.xcalc

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CalculatorEngineTest {

    private lateinit var engine: CalculatorEngine

    @Before
    fun setUp() {
        engine = CalculatorEngine()
    }

    // --- Basic arithmetic ---

    @Test
    fun addition() {
        press("2", "+", "3", "=")
        assertEquals("5", engine.currentInput)
    }

    @Test
    fun subtraction() {
        press("9", "−", "4", "=")
        assertEquals("5", engine.currentInput)
    }

    @Test
    fun multiplication() {
        press("5", "×", "6", "=")
        assertEquals("30", engine.currentInput)
    }

    @Test
    fun division() {
        press("1", "0", "÷", "3", "=")
        val result = engine.currentInput.toDouble()
        assertEquals(3.3333, result, 0.001)
    }

    @Test
    fun divisionByZero() {
        press("5", "÷", "0", "=")
        assertEquals("Error", engine.currentInput)
    }

    // --- Chained operations ---

    @Test
    fun chainedAddition() {
        press("2", "+", "3", "=")
        assertEquals("5", engine.currentInput)
        press("+", "4", "=")
        assertEquals("9", engine.currentInput)
    }

    @Test
    fun chainedOperatorEvaluation() {
        // 2 + 3 × should evaluate 2+3=5 first, then prepare ×
        press("2", "+", "3", "×")
        assertEquals("5", engine.currentInput)
    }

    // --- Repeat equals ---

    @Test
    fun repeatEquals() {
        press("2", "+", "3", "=")
        assertEquals("5", engine.currentInput)
        press("=")
        assertEquals("8", engine.currentInput)
        press("=")
        assertEquals("11", engine.currentInput)
    }

    // --- Percent ---

    @Test
    fun percentWithBase() {
        // 200 + 10% should show 20 (10% of 200), then = gives 220
        press("2", "0", "0", "+")
        press("1", "0", "%")
        assertEquals("20", engine.currentInput)
        press("=")
        assertEquals("220", engine.currentInput)
    }

    @Test
    fun percentWithoutBase() {
        press("5", "0", "%")
        assertEquals("0.5", engine.currentInput)
    }

    // --- History ---

    @Test
    fun historyAddedOnEquals() {
        press("2", "+", "3", "=")
        assertEquals(1, engine.history.size)
        assertEquals("2 + 3 = 5", engine.history[0])
    }

    @Test
    fun historyMaxFourEntries() {
        repeat(5) {
            press("1", "+", "1", "=")
            // Reset for next independent calc
            press("AC")
        }
        // After 5 operations with AC between, history was cleared each time
        // Let's do it without AC
        engine.resetAll()
        press("1", "+", "1", "=") // 1
        press("AC")
        // Need to build up without clearing history
        engine.resetAll()
        // Actually, AC clears history. Let's chain equals instead:
        press("1", "+", "1", "=") // history: ["1 + 1 = 2"]
        press("=")                 // history: ["1 + 1 = 2", "2 + 1 = 3"]
        press("=")                 // history: [..., "3 + 1 = 4"]
        press("=")                 // history: [..., "4 + 1 = 5"]
        press("=")                 // 5th: should trim to 4
        assertEquals(4, engine.history.size)
    }

    @Test
    fun acClearsHistory() {
        press("1", "+", "1", "=")
        assertEquals(1, engine.history.size)
        press("AC")
        assertEquals(0, engine.history.size)
    }

    // --- C vs AC ---

    @Test
    fun cDoesNotClearHistory() {
        press("1", "+", "1", "=")
        assertEquals(1, engine.history.size)
        press("C")
        assertEquals(1, engine.history.size)
        assertEquals("0", engine.currentInput)
    }

    // --- Backspace ---

    @Test
    fun backspaceRemovesLastDigit() {
        press("1", "2", "3")
        press("backspace")
        assertEquals("12", engine.currentInput)
    }

    @Test
    fun backspaceToZero() {
        press("5")
        press("backspace")
        assertEquals("0", engine.currentInput)
    }

    @Test
    fun backspaceAfterEquals() {
        press("2", "+", "3", "=")
        press("backspace")
        assertEquals("0", engine.currentInput)
    }

    // --- formatNumber ---

    @Test
    fun formatNumberInteger() {
        assertEquals("5", engine.formatNumber(5.0))
    }

    @Test
    fun formatNumberDecimal() {
        assertEquals("5.5", engine.formatNumber(5.5))
    }

    @Test
    fun formatNumberNegative() {
        assertEquals("-3", engine.formatNumber(-3.0))
    }

    // --- Dot ---

    @Test
    fun dotNoDuplicate() {
        press("1", ".", "2", ".")
        assertEquals("1.2", engine.currentInput)
    }

    @Test
    fun dotAfterReset() {
        press("2", "+", "3", "=")
        press(".")
        assertEquals("0.", engine.currentInput)
    }

    // --- Digit input ---

    @Test
    fun multiDigitInput() {
        press("1", "2", "3")
        assertEquals("123", engine.currentInput)
    }

    @Test
    fun digitReplacesZero() {
        assertEquals("0", engine.currentInput)
        press("5")
        assertEquals("5", engine.currentInput)
    }

    @Test
    fun digitAfterOperator() {
        press("5", "+", "3")
        assertEquals("3", engine.currentInput)
    }

    // --- Error recovery ---

    @Test
    fun errorRecoveryOnDigit() {
        press("5", "÷", "0", "=")
        assertEquals("Error", engine.currentInput)
        press("3")
        assertEquals("3", engine.currentInput)
    }

    // --- Helper ---

    private fun press(vararg buttons: String) {
        buttons.forEach { engine.pressButton(it) }
    }
}
