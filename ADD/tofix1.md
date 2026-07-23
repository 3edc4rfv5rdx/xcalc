# Code audit findings — tofix1

Each item is a self-contained prompt for an LLM. Verify against current code before fixing.

## Critical — data loss / corruption

1. **FIXED — Non-atomic metadata save can destroy the vault index.**
   In `VaultRepository.saveMetadata()` (VaultRepository.kt) the encrypted metadata is written with `metadataFile.writeBytes(encrypted)` directly over the live file. A crash or kill mid-write corrupts `metadata.enc`, after which all vault files become orphaned `.enc` blobs with no names/paths. Fix: write to a temp file in the same directory, then atomically rename over `metadata.enc` (`File.renameTo`, fall back to copy+delete).

2. **FIXED — Corrupted/unreadable metadata is silently replaced by an empty list, then persisted.**
   In `VaultRepository.mutableMetadata()`, any exception during load (decrypt failure, JSON error) is caught and `metadataCache` becomes an empty mutable list. The next mutating operation (`createFolder`, `importFile`, etc.) calls `saveMetadata()` and overwrites `metadata.enc` with that empty list — permanently orphaning every existing vault file. Fix: on load failure, put the repository into a read-only/error state (or keep a `loadFailed` flag) and refuse to save until the cause is resolved; at minimum back up the unreadable `metadata.enc` before any overwrite.

3. **FIXED — `reEncryptFromTemp` overwrites the original ciphertext in place.**
   In `VaultRepository.reEncryptFromTemp()` the output stream writes directly to the existing `${id}.enc`. If encryption fails or the process dies mid-write, the original encrypted file is destroyed and the only remaining copy is the plaintext temp file (which gets deleted by the caller). Fix: encrypt to a temp file first, then atomically replace the original.

4. **FIXED — `loadMetadata()` leaks the live internal mutable list — concurrent modification risk.**
   `VaultRepository.loadMetadata()` returns `mutableMetadata()` which is the internal `metadataCache` itself. Callers iterate/filter it on the UI thread (`refreshItems`, `getFolders`, `exportAllToTree`) while imports mutate the same list on `Dispatchers.IO` → possible `ConcurrentModificationException` and stale reads. Fix: return a defensive copy (`toList()`) from `loadMetadata()` and keep all mutation behind the `@Synchronized` methods.

## High — security / plaintext leakage

5. **FIXED — Decrypted temp files can persist in cache indefinitely; `clearTemp()` is never called.**
   `VaultRepository.decryptToTemp()` writes plaintext into `cacheDir/vault_temp`. Cleanup relies solely on `persistViewedTemps()` running in `FileListScreen`'s `onDispose`. If the process is killed while an external viewer is open (common, since viewing launches another app), plaintext survives across restarts. `VaultRepository.clearTemp()` exists but has zero call sites. Fix: call `clearTemp()` when the vault opens (e.g., in `VaultScreen` init or on PIN unlock) to sweep leftovers from previous sessions.

6. **FIXED — Failed export leaves a partial plaintext file in the destination.**
   In `VaultRepository.exportFileToTree()`, if `CryptoManager.decrypt` throws (corrupt data, GCM tag mismatch), the method returns false but the already-created `outDoc` with partially written unauthenticated plaintext remains in the user's chosen folder. Fix: delete `outDoc` in the catch branch before returning false.

7. **FIXED — PIN rate-limiting exists only in Compose state and resets trivially.**
   In `PinScreen.kt`, `failCount` and `cooldownUntil` live in `remember { ... }`. Leaving the vault (back) and long-pressing back in resets the counter, so the 30-second cooldown after 3 failures is bypassable with zero cost. Fix: persist fail count and cooldown deadline in `PinManager` (EncryptedSharedPreferences) so they survive recomposition and process restart.

8. **FIXED — PIN hash comparison is not constant-time.**
   `PinManager.verifyPin()` compares hashes with `hash.toHex() == storedHash` (short-circuiting String equals). Low practical risk for a local PIN, but the fix is one line: use `MessageDigest.isEqual(hash, storedHash.fromHex())`.

## Medium — logic bugs

9. **FIXED — `toggleSign` reformats typed input and loses trailing decimal digits.**
   In `CalculatorEngine.toggleSign()`, a non-zero value goes through `formatNumber(toggled)` which does `stripTrailingZeros()`. Typing `5.0`, pressing `+/-`, then typing `5` yields `-55` instead of `-5.05` — the in-progress decimal entry is destroyed. Fix: toggle the sign textually on `currentInput` (add/remove leading `-`) instead of parse+reformat while the user is typing.

10. **FIXED — After `=`, pressing `+/-` then a digit appends to the result instead of starting a new number.**
    `toggleSign()` unconditionally sets `resetInput = false`. Sequence `5 + 3 = +/- 2` produces `-82` instead of starting fresh entry `2` (standard calculator behavior). Decide the intended semantics; if standard, preserve `resetInput` when toggling a just-computed result.

11. **FIXED — Long-pressing `=` to open the vault also fires the tap handler on release.**
    In `MainActivity.CalcButtonView`, the `=` button uses `detectTapGestures(onTap = ..., onPress = { delay(5000) → onLongPress() })`. Holding 5 s opens the vault, but releasing the finger then triggers `onTap` → `engine.pressButton("=")`, silently mutating calculator state behind the vault screen. Fix: track that the long-press fired (e.g., a flag set in the delayed job) and suppress the subsequent tap; or use `awaitEachGesture` with explicit consumption.

12. **FIXED — `createFolder` never checks existence — duplicate `.folder` markers accumulate.**
    `VaultRepository.createFolder()` always appends a new marker entry. Creating a folder whose name already exists (or re-importing the same folder tree — `importFolder`/`importDocumentRecursive` call `createFolder` for every directory) adds duplicate metadata rows forever. UI dedups via a set, but metadata grows unboundedly and semantics are unclean. Fix: return early if a marker (or any entry) with that `relativePath` already exists.

13. **FIXED — `importFolder` builds paths from the unsanitized name while `createFolder` sanitizes it.**
    In `VaultRepository.importFolder()` and `importDocumentRecursive()`, `subFolder` is composed from the raw `folderName`, but the marker is created with `sanitizeName(folderName)`. A directory named with `/` or leading/trailing spaces places files under a path that differs from the marker's path. Also, a name that sanitizes to empty (`.`/`..`) makes `createFolder`'s `require()` throw, crashing the import coroutine. Fix: sanitize once up front, use the same sanitized value for both the marker and the path, and skip (not crash on) unsanitizable names.

14. **FIXED — Imported file names are not sanitized.**
    `VaultRepository.importFile()` stores `displayName` (or `uri.lastPathSegment`) as-is, while `renameFile` applies `sanitizeName`. A name containing `/` breaks `fullPath` semantics and makes export path/`createFile` behave incorrectly. Fix: run the display name through `sanitizeName()` on import (fallback to "unknown" if empty).

15. **FIXED — Folder move/rename does not handle path collisions — silent merge.**
    `VaultRepository.moveFolder()` and `renameFolder()` remap paths without checking whether the target path already exists. Moving/renaming folder `A` where `parent/A` already exists silently merges the contents of two folders (and leaves duplicate `.folder` markers), which is irreversible. Fix: detect the collision and reject (return false) or auto-rename, and surface it in the UI.

16. **FIXED — `persistViewedTemps` does file CRC32 + full re-encryption on the main thread.**
    In `FileListScreen.kt`, `persistViewedTemps()` is called from `onDispose` and `handleBack()` on the UI thread; for a large edited file this means synchronous hashing plus encryption → frozen UI / ANR. Fix: run the persist work on `Dispatchers.IO` (e.g., via a non-cancellable scope since it runs during teardown).

17. **FIXED — No system back handling in the vault — hardware/gesture back exits the whole app.**
    `FileListScreen`'s `handleBack()` (clear selection → up one folder → leave vault) is wired only to the toolbar arrow. A system back press bypasses this navigation entirely. Fix: add `BackHandler { handleBack() }` in `FileListScreen` (and a `BackHandler { onBack() }` in `PinScreen`).

18. **FIXED — All calculator and vault UI state is lost on configuration change.**
    `MainActivity.CalculatorScreen` holds `CalculatorEngine`, history, and `showVault` in plain `remember`; `FileListScreen` similarly holds `currentFolder` and `viewedTemps`. Rotating the device resets the calculator mid-computation and kicks the user out of the vault (also disposing `viewedTemps`, which deletes temp files an external viewer may still be reading). Fix: persist engine state with `rememberSaveable` (or a ViewModel); decide deliberately whether vault lock-on-rotate is intended and handle `viewedTemps` across recreation.

## Low — cleanup / robustness

19. **FIXED — Unbounded growth: calculator history and PIN-less input length.**
    `CalculatorEngine._history` grows without limit, and there is no cap on `currentInput` length (digits can be typed until the display overflows and BigDecimal ops get huge). Fix: cap history (e.g., last 100 entries) and limit input length (e.g., 15 significant digits).

20. **FIXED — Dead code in `VaultRepository` and `CalculatorEngine`.**
    `VaultRepository.exportFile(metadata, destDir: File)`, `getEncryptedFile()`, and `clearTemp()` have no production call sites (`clearTemp` should gain one per item 5, otherwise remove). `CalculatorEngine.formatNumber(Double)` is public but only used by tests, which duplicate the private BigDecimal version. Remove or consolidate.

21. **`deleteFile` in a loop saves metadata N times.**
    In `FileListScreen`'s delete dialog, each selected file triggers a separate `repository.deleteFile()` → full encrypt+write of metadata per file. Add a batch `deleteFiles(list)` that saves once.

22. **Deprecated APIs.**
    `queryIntentActivities(intent, 0)` (FileListScreen view action) is deprecated; the manual grant loop can be replaced by attaching a `ClipData` to the intent with `FLAG_GRANT_READ_URI_PERMISSION`. `EncryptedSharedPreferences`/`MasterKey` (PinManager) are deprecated in androidx.security-crypto 1.1 — note for future migration, low priority for a single-user app.

23. **Design note: vault encryption key is not bound to the PIN.**
    `CryptoManager` uses a Keystore key with no user-auth binding; the PIN in `PinManager` is purely a UI gate. Anyone who can execute code as the app (root, debuggable build, extracted device) can decrypt the vault without the PIN. If this matters, derive a content key from the PIN (PBKDF2 already present) and wrap it with the Keystore key; otherwise document the accepted risk.
