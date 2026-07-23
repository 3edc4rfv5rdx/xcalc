# Project Notes

## Security model (accepted risk)
- Vault files are encrypted with an AES-256-GCM key stored in Android Keystore; the key never leaves the hardware.
- The PIN is only a UI gate: it is NOT bound to the encryption key. Code running as the app (root, debug access) can decrypt the vault without the PIN.
- Decision (2026-07-23): risk accepted. Binding the key to the PIN would make a forgotten PIN equal to total data loss and require vault migration — not worth it for this app.
- What the current model does protect against: copying files off the device (backup, flash extraction) — undecryptable without the hardware key.

## Summary
- We are building an Android calculator app in Kotlin.
- User is new to Kotlin.
- Android Studio is installed and has an emulator.
- UI: Compose calculator screen exists; buttons + display layout already in `MainActivity.kt`.
- Buttons: digits are yellow (`#FFD54F`), operators are salmon (`#E9967A`) with black text, emphasis uses theme primary.
- Backspace icon added; `=` moved to the right; button font is bold and large (33sp).
- Logic: basic operations implemented (+, −, ×, ÷, +/-, AC, C, backspace, decimals).
- Special gesture: dialog shows only if user taps backspace twice, then long-presses `=` for 5 seconds (normal `=` tap works immediately).
- Repeat equals: pressing `=` repeatedly repeats the last operation (e.g., `2+3==` -> `8`, `11`).
- Percent key was replaced by the +/- sign toggle; there is no `%` operation.

## Next Steps
- Clarify current project state (what already exists, if any code/screens are present).
- Decide MVP features: basic operations (+, -, *, /), clear, backspace, decimal, sign toggle.
- Set up UI layout and wire up basic logic.
- Run on emulator and iterate.
