# Changelog

> N=new feature, E=error fix, F=fine-tune, R=refactor, I=infrastructure, T=tag

## Unreleased
- E Preserve edited vault temp files when leaving the vault screen

## 0.3.20260401+74
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

## 0.3.20260401+71
- F Format numbers without scientific notation
- I Merge version and build number into single file

## 0.3.20260327+68
- F Set vault menu and FAB to #22B2D6 with black content
- F Tune vault menu colors for better contrast
- N Add adaptive launcher icons with custom blue background
- F Improve vault menu visibility with blue high-contrast colors

## 0.3.20260326+64
- E Fix calculator history scrolling and remove 8-entry cap
- F Increase history limit to 8 and fix UI refresh on AC
- I Add .debug applicationId suffix and debug install script
- N Add distinct debug launcher icon with red tint and DEBUG label
- N Replace default launcher icon with custom xcalc icon

## 0.3.20260325+59
- R Extract CalculatorEngine and add comprehensive test suite
- I Move build number increment to shell scripts
- N Add calculator operation history UI and improve display readability
- I Enable full release optimization and fix R8 annotations
- I Configure release signing and externalize version prefix
- E Harden vault crypto and metadata error handling
- E Fix vault folder move behavior and invalid move guards
- E Do not delete source files after vault import
- E Fix vault view temp-file lifecycle and open reliability

## 0.1.20260324+47
- N Calculator with basic arithmetic operations
- N Repeat-equals functionality
- N Hidden encrypted file vault with PIN protection
- N AES/GCM encryption via Android Keystore
- N File/folder import, organization, and export
