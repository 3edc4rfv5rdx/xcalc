# Changelog

> N=new feature, E=error fix, F=fine-tune, R=refactor, I=infrastructure, T=tag

## Unreleased
- R Move all hardcoded UI strings to the base English strings.xml
- I Update NOTES.md to drop stale percent feature claims
- R Remove unused deleteOriginal helpers, metadata computed properties and TextButton import
- E Validate vault metadata entries after JSON parse to survive missing fields
- F Shrink long calculator results to fit on one display line
- N Export selected folders recursively instead of silently skipping them
- E Treat moving a folder to its current parent as a no-op instead of a failure
- E Fix vault screen compile error after nullable state refactor
- E Run PIN hashing and PinManager init off the main thread so the vault opens right at the 5s gesture
- R Make vault viewing explicitly read-only and drop the dead external-edit re-encrypt path
- E Share one VaultRepository instance and sweep temp files once per process to fix metadata and temp races
- N Add back arrow button to the PIN screen
- E Close vault back to the calculator when the app goes to background
- E Hide vault screens from screenshots and recents preview with FLAG_SECURE
- I Drop debug build scripts, applicationId suffix and debug icon, test with release builds only
- I Drop stale 04-InstallToSams.sh entry from gitignore
- I Prefix changelog release headers with v to match tag names
- I Rework build scripts to myplayer scheme: decade numbering, stricter clean-tree checks, ABI split installs, auto changelog section, apkx copy
- I Document accepted vault security model in NOTES.md
- R Grant view URI permission via ClipData instead of deprecated query loop
- R Delete selected files in one batch with a single metadata save
- R Remove unused exportFile, getEncryptedFile and formatNumber(Double)
- F Cap calculator history at 100 entries and input at 15 digits
- E Keep calculator and vault state across rotation and theme changes
- E Handle system back in vault screens instead of closing the app
- E Persist viewed temp files on IO thread instead of blocking UI
- E Reject folder move or rename that would merge into an existing folder
- E Sanitize imported file names like renamed ones
- E Sanitize folder names once during tree import to keep paths consistent
- E Skip creating duplicate folder markers for existing paths
- E Suppress equals tap after long-press vault gesture fires
- E Start new entry when typing a digit after toggling a result sign
- E Toggle sign textually to preserve in-progress decimal input
- E Use constant-time comparison for PIN hash verification
- E Persist PIN fail count and cooldown so retry limit survives restarts
- E Delete partially written destination file when vault export fails
- E Sweep leftover decrypted temp files when opening the vault
- E Return metadata copy to prevent concurrent modification crashes
- E Re-encrypt edited vault files to a temp file to protect the original
- E Back up unreadable vault metadata before overwriting with empty index
- E Write vault metadata atomically via temp file and rename
- E Preserve edited vault temp files when leaving the vault screen
- E Switch vault export to SAF destination folders for modern Android
- E Use BigDecimal calculator math to avoid floating-point display artifacts
- E Fix vault export screen compile error after SAF refactor
- E Keep display unchanged on backspace before second operand input
- F Replace percent key with +/- sign toggle

## v0.3.20260401+74
- R Stream encryption instead of loading entire file into memory
- E Add error logging instead of silently swallowing exceptions
- E Add @Synchronized to VaultRepository to prevent race conditions
- R Deduplicate moveFolder/renameFolder via remapFolderPaths
- R Replace unsafe loadMetadata() casts with mutableMetadata()
- R Remove unused folderMarkers field
- E Use CRC32 for reliable temp file change detection
- R Replace renderTick hack with proper Compose state
- E Store original file size instead of encrypted size
- R Extract hardcoded colors to Color.kt constants
- F Cache SimpleDateFormat instance
- R Remove @Suppress workaround for backspaceTapCount
- E Add folder/file name validation against path traversal
- E Avoid overwriting files on export by adding numeric suffix
- I Switch from version.properties to build_number.txt
- I Add tag/push/release scripts (80/81/82)

## v0.3.20260401+71
- F Format numbers without scientific notation
- I Merge version and build number into single file

## v0.3.20260327+68
- F Set vault menu and FAB to #22B2D6 with black content
- F Tune vault menu colors for better contrast
- N Add adaptive launcher icons with custom blue background
- F Improve vault menu visibility with blue high-contrast colors

## v0.3.20260326+64
- E Fix calculator history scrolling and remove 8-entry cap
- F Increase history limit to 8 and fix UI refresh on AC
- I Add .debug applicationId suffix and debug install script
- N Add distinct debug launcher icon with red tint and DEBUG label
- N Replace default launcher icon with custom xcalc icon

## v0.3.20260325+59
- R Extract CalculatorEngine and add comprehensive test suite
- I Move build number increment to shell scripts
- N Add calculator operation history UI and improve display readability
- I Enable full release optimization and fix R8 annotations
- I Configure release signing and externalize version prefix
- E Harden vault crypto and metadata error handling
- E Fix vault folder move behavior and invalid move guards
- E Do not delete source files after vault import
- E Fix vault view temp-file lifecycle and open reliability

## v0.1.20260324+47
- N Calculator with basic arithmetic operations
- N Repeat-equals functionality
- N Hidden encrypted file vault with PIN protection
- N AES/GCM encryption via Android Keystore
- N File/folder import, organization, and export
