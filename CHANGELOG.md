# CHANGELOG
> Newest entries on top.
> N=new feature, E=error fix, F=fine-tune, R=refactor, I=infrastructure

## Unreleased
- R: The About screen is the shared module's now — one dialog for every project here, with the version, build date, GitHub page, mailbox and the update check on it
- N: The About dialog has an Update button, so a new build can be looked for at once instead of waiting out the updater's six-hour interval; it answers even when there is nothing newer
## v0.5.113 (2026-09-08)
- N: The armeabi-v7a APK goes into OUT/ and out with the release, so a 32-bit phone or TV box gets its own build instead of the universal one
- N: The About screen shows the build date on a line of its own, where the build number used to be — the version already ends in that number
- I: The version is major.minor.build — the date left it — so the tag is v0.5.113 and an artifact xcalc-0.5.113-arm64-v8a.apk, each number written once; 20-MakeTag.sh puts the build date after the tag in the CHANGELOG heading, for the reader
- N: The app checks its own GitHub release for a newer build on start and offers it, the same updater the other projects here use — one manifest names every ABI split, so the phone takes arm64 and an armeabi-v7a box takes v7a, wherever either of them is
- I: 23-ToUpdate.sh writes that manifest into the release, run by hand after 22-RelUpload.sh
- I: 00-MakeAll.sh runs the whole build in one go, and 19-LinkOut.sh puts the build into OUT/ under its own name — the steps the sibling projects already had
- E: The version line looks only at release tags, so a tag like "duplex" can no longer answer which line the last release went out on
- I: The version line moves by itself when a new feature is waiting in the changelog, as it does in the sibling projects
- I: An install step with nothing to install on exits 3, the code the whole set uses for "nothing to work on"
- E: An emulator install that failed makes the run fail, instead of being hidden by the pause after it
- E: The release push names the branch, and the tag and its changelog section are matched whole, so a build whose number is a prefix of another is no longer mistaken for it
- E: Every step that touches the project's files or its git runs from the project directory, so one started from elsewhere can no longer work on the wrong tree
- E: The release notes carry the letter legend again, not the "newest on top" line that now sits above it in the changelog
- E: 99-CopyToAPKX.sh runs from the project directory, so its sweep of stale .apkx links can no longer delete them in whatever directory it was called from
- I: One CHANGELOG legend across every project here — N/E/F/R/I, newest on top, the type letter always followed by a colon
- I: Every changelog entry carries the colon after its type letter
- I: Every artifact carries one name — xcalc-<version>-<build>-<abi>.apk, with -debug on the end of a debug build — and the tag it goes out under is v<version>-<build>.
- I: Ignore the local ADD/drawable.save icon backup and the icon-preview.png link
- F: Redraw the launcher icon flat in #013895 and white: a white-rimmed calculator with a display showing 000 and the operators + - x / split by a cross
- I: Document the vault entry gesture and PIN mode in README

## v0.4.20260805+101
- I: Add app screenshots to README from docs/images
- F: Show Ok or Error for 2s after first-time PIN setup: Ok opens the vault, Error returns to the calculator
- F: Label the two first-time PIN setup entries as Pin1 and Pin2 on the display
- I: Update NOTES.md and README for calculator-keypad PIN entry and current helper scripts
- N: Enter the PIN on the calculator keypad itself instead of a separate PIN screen: orange display digits hint the mode, zeros mask the PIN, setup shows real digits, Error and countdown stay calculator-styled
- E: Run instrumentation tests against isolated vault and PIN storage so they cannot wipe real data
- E: Fix repository tests that mutated the defensive metadata copy instead of the vault index
- R: Delete selected folders in one batch with a single index save
- E: Write the PIN fail counter synchronously so force-killing the app cannot bypass the cooldown
- E: Deduplicate corrupt index backups and survive backup failures instead of crashing the vault open
- E: Report failed files in the folder import toast instead of silently dropping them
- E: Fix trailing dot in export collision names for files without an extension
- E: Load the Move dialog folder list off the main thread so it cannot freeze behind a running import
- E: Fsync imported encrypted blobs before saving the index so power loss cannot leave unreadable entries
- E: Keep the vault open behind system file pickers during import and export instead of relocking to the calculator
- I: Resolve tofix3 audit leftovers: keep security-crypto as accepted, jsr305 confirmed needed for R8
- E: Sweep orphaned encrypted blobs on vault open after a clean index load
- E: Synchronize keystore key creation so concurrent first use cannot generate it twice
- E: Recreate empty folders when exporting a folder or the whole vault
- E: Fsync the vault index temp file before the atomic rename so power loss cannot truncate it
- E: Keep the calculation history when recovering from an Error result
- E: Run vault metadata reads and mutations off the main thread in the file list
- E: Relock the vault when returning more than a minute after an external viewer was opened
- E: Keep VaultFileMetadata field names from R8 and migrate legacy obfuscated metadata keys so updates cannot empty the vault index
- E: Preload PinManager on the second backspace tap so the vault opens right at the 5s "=" hold
- R: Move all hardcoded UI strings to the base English strings.xml
- I: Update NOTES.md to drop stale percent feature claims
- R: Remove unused deleteOriginal helpers, metadata computed properties and TextButton import
- E: Validate vault metadata entries after JSON parse to survive missing fields
- F: Shrink long calculator results to fit on one display line
- N: Export selected folders recursively instead of silently skipping them
- E: Treat moving a folder to its current parent as a no-op instead of a failure
- E: Fix vault screen compile error after nullable state refactor
- E: Run PIN hashing and PinManager init off the main thread so the vault opens right at the 5s gesture
- R: Make vault viewing explicitly read-only and drop the dead external-edit re-encrypt path
- E: Share one VaultRepository instance and sweep temp files once per process to fix metadata and temp races
- N: Add back arrow button to the PIN screen
- E: Close vault back to the calculator when the app goes to background
- E: Hide vault screens from screenshots and recents preview with FLAG_SECURE
- I: Drop debug build scripts, applicationId suffix and debug icon, test with release builds only
- I: Drop stale 04-InstallToSams.sh entry from gitignore
- I: Prefix changelog release headers with v to match tag names
- I: Rework build scripts to myplayer scheme: decade numbering, stricter clean-tree checks, ABI split installs, auto changelog section, apkx copy
- I: Document accepted vault security model in NOTES.md
- R: Grant view URI permission via ClipData instead of deprecated query loop
- R: Delete selected files in one batch with a single metadata save
- R: Remove unused exportFile, getEncryptedFile and formatNumber(Double)
- F: Cap calculator history at 100 entries and input at 15 digits
- E: Keep calculator and vault state across rotation and theme changes
- E: Handle system back in vault screens instead of closing the app
- E: Persist viewed temp files on IO thread instead of blocking UI
- E: Reject folder move or rename that would merge into an existing folder
- E: Sanitize imported file names like renamed ones
- E: Sanitize folder names once during tree import to keep paths consistent
- E: Skip creating duplicate folder markers for existing paths
- E: Suppress equals tap after long-press vault gesture fires
- E: Start new entry when typing a digit after toggling a result sign
- E: Toggle sign textually to preserve in-progress decimal input
- E: Use constant-time comparison for PIN hash verification
- E: Persist PIN fail count and cooldown so retry limit survives restarts
- E: Delete partially written destination file when vault export fails
- E: Sweep leftover decrypted temp files when opening the vault
- E: Return metadata copy to prevent concurrent modification crashes
- E: Re-encrypt edited vault files to a temp file to protect the original
- E: Back up unreadable vault metadata before overwriting with empty index
- E: Write vault metadata atomically via temp file and rename
- E: Preserve edited vault temp files when leaving the vault screen
- E: Switch vault export to SAF destination folders for modern Android
- E: Use BigDecimal calculator math to avoid floating-point display artifacts
- E: Fix vault export screen compile error after SAF refactor
- E: Keep display unchanged on backspace before second operand input
- F: Replace percent key with +/- sign toggle

## v0.3.20260401+74
- R: Stream encryption instead of loading entire file into memory
- E: Add error logging instead of silently swallowing exceptions
- E: Add @Synchronized to VaultRepository to prevent race conditions
- R: Deduplicate moveFolder/renameFolder via remapFolderPaths
- R: Replace unsafe loadMetadata() casts with mutableMetadata()
- R: Remove unused folderMarkers field
- E: Use CRC32 for reliable temp file change detection
- R: Replace renderTick hack with proper Compose state
- E: Store original file size instead of encrypted size
- R: Extract hardcoded colors to Color.kt constants
- F: Cache SimpleDateFormat instance
- R: Remove @Suppress workaround for backspaceTapCount
- E: Add folder/file name validation against path traversal
- E: Avoid overwriting files on export by adding numeric suffix
- I: Switch from version.properties to build_number.txt
- I: Add tag/push/release scripts (80/81/82)

## v0.3.20260401+71
- F: Format numbers without scientific notation
- I: Merge version and build number into single file

## v0.3.20260327+68
- F: Set vault menu and FAB to #22B2D6 with black content
- F: Tune vault menu colors for better contrast
- N: Add adaptive launcher icons with custom blue background
- F: Improve vault menu visibility with blue high-contrast colors

## v0.3.20260326+64
- E: Fix calculator history scrolling and remove 8-entry cap
- F: Increase history limit to 8 and fix UI refresh on AC
- I: Add .debug applicationId suffix and debug install script
- N: Add distinct debug launcher icon with red tint and DEBUG label
- N: Replace default launcher icon with custom xcalc icon

## v0.3.20260325+59
- R: Extract CalculatorEngine and add comprehensive test suite
- I: Move build number increment to shell scripts
- N: Add calculator operation history UI and improve display readability
- I: Enable full release optimization and fix R8 annotations
- I: Configure release signing and externalize version prefix
- E: Harden vault crypto and metadata error handling
- E: Fix vault folder move behavior and invalid move guards
- E: Do not delete source files after vault import
- E: Fix vault view temp-file lifecycle and open reliability

## v0.1.20260324+47
- N: Calculator with basic arithmetic operations
- N: Repeat-equals functionality
- N: Hidden encrypted file vault with PIN protection
- N: AES/GCM encryption via Android Keystore
- N: File/folder import, organization, and export
