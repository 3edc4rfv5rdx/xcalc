# Code audit findings — tofix2

Each item is a self-contained prompt for an LLM. Verify against current code before fixing.

## High — security

1. **FIXED — Vault screens are visible in the recents screenshot and screen capture — no FLAG_SECURE.**
   The app is a disguised vault, but the activity window never sets `WindowManager.LayoutParams.FLAG_SECURE`. When the user switches apps while the PIN screen or file list is open, the system stores a screenshot of it for the recents switcher, and screen-recording/casting captures it too — defeating the calculator disguise. Fix: in `MainActivity` (or reactively when `showVault` becomes true), set `window.setFlags(FLAG_SECURE, FLAG_SECURE)` while the vault UI is shown (optionally clear it when back on the calculator, or keep it always-on for simplicity).

2. **FIXED — Vault stays unlocked while the app is in the background — no re-lock on lifecycle stop.**
   `VaultScreen` keeps `state = FILE_LIST` in Compose state; the activity is not recreated on home/recents (and `configChanges` suppresses recreation). If the user opens the vault and then presses Home, anyone who reopens the app from recents lands directly in the unlocked file list — no PIN asked. Fix: observe lifecycle (e.g. `LifecycleEventObserver` on `ON_STOP`) and reset the vault to `PIN_UNLOCK` (or close the vault entirely back to the calculator). Decide the exact policy: immediate lock on ON_STOP is simplest; a short grace period is an option but adds state.

3. **FIXED — `VaultRepository` is instantiated per vault entry while background persist uses the old instance — races on metadata and temp files.**
   `VaultScreen` does `remember { VaultRepository(context) }`, so each vault entry creates a new instance, while `FileListScreen.persistViewedTemps()` launches work on a `SupervisorJob` scope that survives leaving the screen and holds the *old* repository instance. Consequences: (a) `@Synchronized` locks are per-instance, so the old instance's `saveMetadata()` can interleave with the new instance's writes, and both use the same `metadata.enc.tmp` path — concurrent writes can corrupt the metadata save; (b) each instance has its own `metadataCache`, so the new instance can load metadata before the old instance's pending save lands, then a later save from the new instance silently discards the persist's `size`/`dateAdded` update; (c) `VaultScreen`'s `clearTemp()` on re-entry can delete a temp file that the still-running persist job is about to re-encrypt — if it deletes it before `reEncryptFromTemp` opens the file, the user's external edits are silently lost. Fix: make `VaultRepository` a process-wide singleton (object or companion-held instance keyed by app context) so all locks and the metadata cache are shared; make `clearTemp()` skip files currently tracked for persist (or run the sweep only once per process start, not on every vault open).

## Medium — logic bugs

4. **FIXED (decision: view-only; edit path removed) — The external-edit persist path can never trigger: view intent grants read-only access.**
   `FileListScreen`'s view action builds `Intent(ACTION_VIEW)` with only `FLAG_GRANT_READ_URI_PERMISSION`, so no external app can modify the temp file. Yet the code computes an initial CRC32, tracks `ViewedTemp`, and calls `reEncryptFromTemp` when the CRC changes — a re-encrypt pipeline for edits that are impossible. Decide the intent: if editing externally should work, add `FLAG_GRANT_WRITE_URI_PERMISSION` to the intent and ClipData grant; if the vault is deliberately view-only, remove the CRC tracking and `reEncryptFromTemp` path as dead code.

5. **FIXED (obsolete — CRC tracking removed with item 4) — CRC32 of the decrypted file is computed on the main thread when viewing.**
   In `FileListScreen`'s view action, `repository.decryptToTemp(meta)` runs under `withContext(Dispatchers.IO)`, but the subsequent `initialCrc = fileCrc32(tempFile)` runs in the main-thread part of the coroutine. For a large file (video) this reads the entire file on the UI thread — frozen UI / ANR. Fix: compute the CRC inside the same `Dispatchers.IO` block that decrypts (return the pair from `withContext`).

6. **FIXED — PBKDF2 (100 000 iterations) runs on the main thread during PIN verify/setup.**
   `PinManager.verifyPin()`/`setupPin()` are called synchronously from `PinScreen` button callbacks (`onPinComplete` in `VaultScreen`). PBKDF2WithHmacSHA256 at 100k iterations takes hundreds of milliseconds on slower devices — visible freeze on every PIN submit. `PinManager` construction (MasterKey + EncryptedSharedPreferences, keystore + disk I/O) also happens during first composition. Fix: run hashing/verification on `Dispatchers.Default`/`IO` (make the PIN completion flow async with a small loading state), and lazily initialize the prefs off the main thread.

7. **FIXED (obsolete — reEncryptFromTemp removed with item 4) — `reEncryptFromTemp` resurrects a deleted file as an orphan ciphertext blob.**
   In `VaultRepository.reEncryptFromTemp()`, the new ciphertext is written and `replaceFile` recreates `${id}.enc` *before* checking that the metadata entry still exists. If the user views a file, edits it, then deletes it in the vault before the persist job runs, the deleted file's `.enc` is recreated on disk but is absent from metadata — an orphan that is never listed and never cleaned up (wasted space, and "deleted" content silently kept on disk). Fix: check `list.indexOfFirst { it.id == metadata.id }` first and skip re-encryption (just delete the temp) when the entry is gone.

## Low — cleanup / robustness

8. **FIXED — Moving a folder to its current location is reported as a failure.**
   `VaultRepository.moveFolder()` returns false when `newPath == oldPath` (moving a folder into its current parent), and `FileListScreen`'s move dialog counts that as a failed move, showing the misleading toast "Some folders could not be moved" for a harmless no-op. Fix: return true (treat as successful no-op) for `newPath == oldPath`, keep false only for real conflicts (target exists, moving into own subtree).

9. **FIXED — Export of a mixed selection silently drops folders.**
   In `FileListScreen`, the export action is shown when `selectedFiles().isNotEmpty()`, but if the selection also contains folders, `ExportMode.SELECTED` exports only the files — the folders and their contents are silently skipped with no indication. Fix: either export selected folders recursively (reuse `ensureDocumentPath` + per-file export under the folder's subtree) or exclude the export action / warn when folders are selected.

10. **FIXED — Long calculation results overflow the display.**
    The main value `Text` in `DisplayArea` (MainActivity.kt) has no `maxLines`, auto-sizing, or horizontal scrolling; a result like `999999999999999 × 999999999999999` (~30 digits, and division can produce even longer strings) wraps onto multiple lines and squeezes the history area. Fix: use a single-line auto-shrinking text (e.g. `maxLines = 1` with `TextOverflow`-aware font scaling, or `basicMarquee`/horizontal scroll) for the current value.

11. **FIXED — Gson can materialize `VaultFileMetadata` with nulls in non-null Kotlin fields.**
    `VaultRepository.mutableMetadata()` parses metadata with reflective Gson, which bypasses Kotlin null-safety and constructor defaults: a JSON entry missing `id`/`name`/`relativePath` (schema evolution in a future version, or a partially corrupted file) yields nulls in non-null fields and crashes far from the cause (e.g. in `fullPath` or filtering). Fix: after `fromJson`, validate each entry (drop or repair entries with null/blank `id` or `name`, null `relativePath` → ""), or add a post-parse mapping to a validated copy.

12. **FIXED — Dead code accumulating again.**
    `VaultRepository.deleteOriginal()` and `deleteOriginalTree()` have zero call sites; `VaultFileMetadata.fullPath` and `isInFolder` are unused; `MainActivity` imports `TextButton` without using it. Remove them (or wire `deleteOriginal` into the import flow if "move into vault" semantics are planned — decide, don't keep both states).

13. **FIXED — NOTES.md documents features the engine no longer has.**
    NOTES.md claims `%` (percent) is implemented with specific semantics ("if there is a stored value, % uses it as base; otherwise divides current input by 100"), but `CalculatorEngine.pressButton` has no `%` case and no button exists in the UI grid. Either re-implement percent per the documented semantics or update NOTES.md to drop the claim.

14. **FIXED — All vault/calculator UI strings are hardcoded in code instead of string resources.**
    Every user-facing string (Toasts like "Imported N file(s)", dialog titles "Delete"/"Rename"/"Move to", "Vault", PIN screen texts, etc.) is a hardcoded English literal; `strings.xml` contains only `app_name`. This violates the project convention of keeping UI strings in the base English resources file. Fix: extract user-facing strings to `res/values/strings.xml` (base English only; punctuation added in code where the convention requires).
