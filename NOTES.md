# Project Notes

## Summary
- We are building an Android calculator app in Kotlin.
- User is new to Kotlin.
- Android Studio is installed and has an emulator.
- UI: Compose calculator screen exists; buttons + display layout already in `MainActivity.kt`.
- Buttons: digits are yellow (`#FFD54F`), operators are salmon (`#E9967A`) with black text, emphasis uses theme primary.
- Backspace icon added; `=` moved to the right; button font is bold and large (33sp).
- Logic: basic operations implemented (+, −, ×, ÷, %, AC, C, backspace, decimals).
- Special gesture: dialog shows only if user taps backspace twice, then long-presses `=` for 5 seconds (normal `=` tap works immediately).
- Repeat equals: pressing `=` repeatedly repeats the last operation (e.g., `2+3==` -> `8`, `11`).
- Percent behavior: if there is a stored value, `%` uses it as base; otherwise divides current input by 100.

## Next Steps
- Clarify current project state (what already exists, if any code/screens are present).
- Decide MVP features: basic operations (+, -, *, /), clear, backspace, decimal, sign toggle.
- Set up UI layout and wire up basic logic.
- Run on emulator and iterate.
