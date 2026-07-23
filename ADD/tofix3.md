# Code audit findings — tofix3

Each item is a self-contained prompt for an LLM. Verify against current code before fixing.

## Critical — data loss

1. **FIXED — R8 obfuscates `VaultFileMetadata` field names in the persisted metadata JSON — an app update can silently empty the vault index.**
   Release builds have `isMinifyEnabled = true`, Gson serializes `VaultFileMetadata` reflectively (`VaultRepository.saveMetadata`/`mutableMetadata`), and `proguard-rules.pro` contains no keep rules for the model class (no `@SerializedName` either). R8 renames the data-class fields, so `metadata.enc` is written with obfuscated JSON keys (e.g. `"a"`, `"b"`). Within one build this round-trips, but the next release may produce a different mapping: Gson then finds no matching keys, every field comes back null via Unsafe instantiation, `sanitizeLoaded()` drops all entries (null `id`), and the vault index silently becomes empty — no exception, so not even the `.corrupt` backup is made; the first mutating operation persists the empty list and every `.enc` file is orphaned. Fix: add `-keepclassmembers class x.x.xcalc.vault.VaultFileMetadata { <fields>; }` (or annotate every field with `@SerializedName` and keep the annotation). Also consider making `sanitizeLoaded` back up the original file when it drops a large fraction of entries, as a safety net against similar schema mismatches. Migration: metadata already written by existing release installs contains obfuscated keys — check the released build's `mapping.txt` and either migrate those keys on load or accept a one-time re-import; do not ship the keep rule silently assuming old data still loads.

## High — security

2. **FIXED — The one-shot external-viewer exemption leaves the vault unlocked indefinitely when the user leaves the viewer via Home.**
   In `MainActivity` the `ON_STOP` observer closes the vault unless `externalViewActive` is set, which is consumed by the stop caused by launching the viewer. But after that exempted stop the activity stays stopped: if the user presses Home (or switches apps) from inside the external viewer and returns to the launcher, no further `ON_STOP` ever fires, `showVault` remains true, and reopening xcalc hours later lands directly in the unlocked file list — no PIN. Fix: record the timestamp when the exempted stop happens and, on `ON_START`, close the vault if more than a short grace period (e.g. 60 s) has elapsed since then; alternatively re-lock on `ON_START` whenever the exemption was used and the temp-file viewer intent has finished. Keep the normal path (view file, return within seconds) working without re-asking the PIN.

## Medium — performance / logic

3. **FIXED — All metadata mutations and the first metadata load run on the main thread in `FileListScreen`.**
   `refreshItems()` (first call decrypts and parses the whole index), `createFolder`, `deleteFiles` + `deleteFolder` (which also delete `.enc` files from disk), `moveFiles`, `moveFolder`, `renameFile`, `renameFolder` are all invoked directly from Compose click handlers. Each mutation re-encrypts and rewrites the entire metadata file synchronously — disk I/O + AES + JSON on the UI thread, causing jank and potential ANR on large vaults (import/export already use `Dispatchers.IO` correctly). Fix: wrap these repository calls in `scope.launch { withContext(Dispatchers.IO) { ... } }` and refresh the list afterwards, mirroring the import path.

4. **Recovering from an "Error" result wipes the entire calculation history.**
   In `CalculatorEngine.pressButton`, when `currentInput == "Error"` any button except AC/C triggers `resetAll()`, which also does `_history.clear()`. So after a division by zero, pressing any digit silently erases the whole history — clearly broader than needed to recover from the error. Fix: reset only the computation state (currentInput, storedValue, pendingOp, resetInput, lastOp, lastRight) and keep `_history`; keep full clear only for AC.

5. **`saveMetadata` renames without fsync — power loss can still produce an empty/truncated index.**
   `VaultRepository.saveMetadata()` writes `metadata.enc.tmp` via `writeBytes` and renames it over the live file. `renameTo` is atomic for the directory entry, but the tmp file's data may still be in page cache; on power loss/battery pull after the rename but before writeback, ext4 can leave a zero-length or truncated `metadata.enc` — the vault index is lost (load fails, `.corrupt` backup of a truncated file, empty list). Fix: write via `FileOutputStream`, call `fd.sync()` before closing, then rename (optionally fsync the directory too). This is the standard atomic-replace pattern and costs one flush per metadata save.

## Low — robustness / cleanup

6. **Empty folders are skipped on export.**
   `exportFolderToTree` and `exportAllToTree` filter out `inode/directory` marker entries and only create directories on the path of exported files, so folders that contain no files (or whose subtree is empty) are silently absent from the exported tree. Fix: for marker entries under the exported scope, call `ensureDocumentPath` for their `relativePath` even though there is no file to write, so the exported structure matches the vault.

7. **`CryptoManager.getOrCreateKey()` is not synchronized — concurrent first use could generate the key twice.**
   Two threads hitting the keystore before the alias exists would each call `generateKey()`; the second generation replaces the first key, making anything encrypted moments earlier with the first key permanently undecryptable. Today the call sites are effectively serialized (repository methods are `@Synchronized`), so this is latent, but it is one `@Synchronized` away from safe. Fix: make `getOrCreateKey` (or the whole object's key access) synchronized.

8. **Orphaned `.enc` blobs are never cleaned up.**
   `importFile` writes `${id}.enc` and only then updates metadata; a crash or process kill between the two leaves an encrypted blob that no metadata entry references — invisible in the UI and kept forever (wasted space, "ghost" content on disk). Fix: on vault open (e.g. after successful metadata load in `VaultScreen`), list `files/` and delete `.enc` files whose id is not present in metadata. Guard carefully: run the sweep only when metadata loaded successfully and non-defensively (never after a load failure or when the index came back empty due to an error), otherwise a transient load problem would delete all real data.

9. **`androidx.security:security-crypto` is deprecated and pinned to an alpha.**
   The project depends on `security-crypto 1.1.0-alpha06`; Jetpack has deprecated the library (EncryptedSharedPreferences/MasterKey get no further development). It still works, but it is unmaintained alpha code sitting in the PIN path. Decide: either accept and pin knowingly, or replace `PinManager`'s storage with plain SharedPreferences holding values encrypted via the existing `CryptoManager` keystore key (the stored PIN hash is already PBKDF2 with salt; the extra encryption layer mainly hides the salt/fail-count). Low urgency, no user-visible impact today.

10. **Unused `jsr305` dependency.**
    `app/build.gradle.kts` declares `implementation("com.google.code.findbugs:jsr305:3.0.2")`, but no source file imports `javax.annotation`. If it was added to silence a transitive annotation warning, note that; otherwise remove the dependency.
