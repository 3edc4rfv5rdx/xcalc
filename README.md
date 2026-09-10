# xcalc

An Android calculator with an encrypted file vault hidden behind it. Kotlin and Jetpack Compose,
no accounts and no cloud: the only thing it reaches the network for is its own GitHub release, to
say when a newer build is out.

## Highlights

- Compose UI (Material 3), calculation history and repeat-equals behaviour.
- A hidden vault with PIN protection; the PIN is typed on the calculator keypad itself, so there is
  no tell-tale PIN screen.
- Files and folders imported into the vault, renamed, moved, exported back out — folders
  recursively — and opened from it.
- AES-256-GCM encryption with the key in the Android Keystore, where it never leaves the hardware.
- An About dialog with the version, the build date, the GitHub page and an update check.

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

## What the PIN does and does not protect

The vault's files are encrypted with an AES-256-GCM key held in the Android Keystore, so copies
taken off the device — a backup, a flash dump — are undecryptable without that hardware. The PIN is
a UI gate only: it is not bound to the key, so code running as the app (root, a debug session) can
read the vault without it. That is a decision, not an oversight — binding the key to the PIN would
make a forgotten PIN mean total data loss. [NOTES.md](NOTES.md) has the reasoning.

## About and updates

The **(i)** button in the display's top left corner opens About — a long press on the display does
the same, but nothing said so. It shows the version, the build date, the GitHub page, the mailbox,
and a button that asks for a newer build there and then. Beyond that the app asks GitHub for the
newest release of this repository at launch, at most every six hours, and offers the split built
for the device's own ABI — arm64 for a phone, armeabi-v7a for a 32-bit box — out of the
`latest.json` that `23-ToUpdate.sh` uploads beside the APKs.

## Shared modules

Two folders beside this one are compiled into the app from source rather than pulled in as modules
or AARs — one copy of each serves every project here (the `sourceSets` block in
`app/build.gradle.kts` wires them):

- `../updater` — the update check, its configuration and its dialogs
- `../about` — the About dialog, drawn in code so the resource shrinker cannot drop its strings

## Build

Release-only workflow, driven by the numbered scripts at the repository root:

| Script | What it does |
|---|---|
| `./00-MakeAll.sh` | release, both installs and the `OUT/` link in one run |
| `./10-MakeRelease.sh` | signed release with ABI splits, raising the build number |
| `./11-EmulRELEASE.sh`, `./12-SamsRELEASE.sh` | install the release on the emulator / the phone |
| `./19-LinkOut.sh` | hard-link this build's APKs into `OUT/` |
| `./20-MakeTag.sh`, `./21-PushTag.sh` | the release tag and its push |
| `./22-RelUpload.sh` | the GitHub Release for that tag, with the APKs |
| `./23-ToUpdate.sh` | `latest.json` for that release, so the in-app updater can find it |
| `./99-CopyToAPKX.sh` | the arm64 APK under an `.apkx` name, for messengers that mangle `.apk` |

Release signing reads `~/.my-safe/key.properties`; the version and the build number live in
`build_number.txt`, and `10-MakeRelease.sh` raises the line itself when the changelog has an `N`
entry waiting under `Unreleased`.

Targets: `minSdk` 29, `targetSdk`/`compileSdk` 36. Application id `x.x.xcalc`; the release is split
into arm64-v8a, armeabi-v7a and x86_64.

## Note
This codebase was developed with the help of artificial intelligence tools.
