# xcalc

xcalc is an Android app built with Kotlin and Jetpack Compose. It combines a clean calculator UI with a secure encrypted file vault.

## Highlights
- Compose-based UI (Material 3).
- Calculation history and repeat-equals behavior.
- Hidden Vault with PIN protection; the PIN is entered on the calculator keypad itself, with no tell-tale PIN screen.
- File/folder import, organization, and export from Vault.
- Encryption backed by Android Keystore (`AES/GCM`).

## Screenshots
<table>
  <tr>
    <td><img src="docs/images/screen1.jpg" width="240" alt="Calculator with history"></td>
    <td><img src="docs/images/screen2.jpg" width="240" alt="PIN entered on the calculator keypad"></td>
    <td><img src="docs/images/screen3.jpg" width="240" alt="Vault file list"></td>
  </tr>
  <tr>
    <td align="center">Calculator and history</td>
    <td align="center">PIN entry on the keypad</td>
    <td align="center">Vault contents</td>
  </tr>
</table>

## Entering the Vault
1. Tap the backspace key twice, then press and hold `=` for 5 seconds. A normal `=` tap still works as usual, so the gesture stays invisible during regular use.
2. The display value turns orange — that is the only hint that PIN mode is active. Type the PIN on the calculator keypad (digits are masked as zeros) and press `=` to submit.
3. On first launch there is no PIN yet: the display shows `Pin1`, then `Pin2` for confirmation, with real digits visible. `Ok` means the PIN was saved and the vault opens; `Error` means the two entries differ.
4. A wrong PIN shows `Error`; after 3 failures the display counts down a 30-second cooldown as a plain number.
5. Backspace on an empty display leaves PIN mode. Any other key silently returns to the plain calculator and is applied as a normal keypress.

## Helper Scripts
- `10-MakeRelease.sh`: build release APKs (bumps the build number).
- `11-EmulRELEASE.sh`, `12-SamsRELEASE.sh`: install release APKs with `adb`.
- `20-MakeTag.sh`, `21-PushTag.sh`, `22-RelUpload.sh`: tag a release and upload it.
- `99-CopyToAPKX.sh`: copy built APKs to the archive location.

## Note
This codebase was developed with the help of artificial intelligence tools.
