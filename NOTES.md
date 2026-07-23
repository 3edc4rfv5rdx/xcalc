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
- Special gesture: tap backspace twice, then long-press `=` for 5 seconds to enter PIN mode (normal `=` tap works immediately).
- PIN entry happens on the calculator itself — no separate PIN screen. The display value turns orange (the only mode hint), unlock digits are masked as zeros, first-time setup/confirm shows real digits (empty display labeled `Pin1`/`Pin2`), `=` submits, backspace on empty input exits, any other key silently returns to the normal calculator and applies as usual.
- PIN errors stay calculator-styled: wrong PIN shows `Error`; after 3 failures the display counts down a 30 s cooldown as a plain number.
- First-time setup verdict shows for 2 s: `Ok` (PIN saved) then opens the vault directly, `Error` (mismatch) drops back to the plain calculator.
- Repeat equals: pressing `=` repeatedly repeats the last operation (e.g., `2+3==` -> `8`, `11`).
- Percent key was replaced by the +/- sign toggle; there is no `%` operation.
