# xcalc

xcalc is an Android app built with Kotlin and Jetpack Compose. It combines a clean calculator UI with a secure encrypted file vault.

## Highlights
- Compose-based UI (Material 3).
- Calculation history and repeat-equals behavior.
- Hidden Vault flow with PIN protection.
- File/folder import, organization, and export from Vault.
- Encryption backed by Android Keystore (`AES/GCM`).

## Helper Scripts
- `00-MakeRelease.sh`, `01-MakeDebug.sh`: increment build number and build APKs.
- `02-InstallDEBUG.sh`, `03-InstallToEmul.sh`, `04-InstallToSams.sh`: install APKs with `adb`.

## Note
This codebase was developed with the help of artificial intelligence tools.
