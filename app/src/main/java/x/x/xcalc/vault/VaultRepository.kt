package x.x.xcalc.vault

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

// Production code must use getInstance(): all synchronization (metadata
// cache, @Synchronized methods, metadata.enc.tmp) is per-instance, so two
// instances over the same files can race and corrupt the vault index.
// The visible constructor exists for tests only.
class VaultRepository(private val context: Context) {

    companion object {
        private const val TAG = "VaultRepository"

        @Volatile
        private var instance: VaultRepository? = null

        fun getInstance(context: Context): VaultRepository {
            return instance ?: synchronized(this) {
                instance ?: VaultRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    private val gson = Gson()
    private val vaultDir = File(context.filesDir, "vault").apply { mkdirs() }
    private val filesDir = File(vaultDir, "files").apply { mkdirs() }
    private val metadataFile = File(vaultDir, "metadata.enc")
    private val tempDir = File(context.cacheDir, "vault_temp").apply { mkdirs() }

    private var metadataCache: MutableList<VaultFileMetadata>? = null

    // True only when the last metadata load parsed cleanly and sanitize
    // dropped nothing. Guards the orphan sweep: a degraded load must never
    // make real files look like orphans.
    private var lastLoadClean = false

    // Returns a defensive copy: the internal cache is mutated under
    // @Synchronized while callers may iterate on another thread.
    @Synchronized
    fun loadMetadata(): List<VaultFileMetadata> = mutableMetadata().toList()

    private fun mutableMetadata(): MutableList<VaultFileMetadata> {
        metadataCache?.let { return it }
        if (!metadataFile.exists()) {
            metadataCache = mutableListOf()
            return metadataCache!!
        }
        metadataCache = try {
            val encrypted = metadataFile.readBytes()
            val json = String(CryptoManager.decryptBytes(encrypted))
            val migrated = remapLegacyKeys(json) ?: json
            val type = object : TypeToken<List<VaultFileMetadata>>() {}.type
            val list: List<VaultFileMetadata>? = gson.fromJson(migrated, type)
            val parsed = list ?: emptyList()
            val sane = sanitizeLoaded(parsed)
            lastLoadClean = sane.size == parsed.size
            sane
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load metadata", e)
            // Preserve the unreadable file so a later save cannot destroy
            // the only copy of the vault index.
            val backup = File(vaultDir, "metadata.enc.corrupt-${System.currentTimeMillis()}")
            metadataFile.copyTo(backup, overwrite = true)
            mutableListOf()
        }
        return metadataCache!!
    }

    // Release builds shipped before the VaultFileMetadata proguard keep rule
    // wrote the index with R8-obfuscated field names (see that build's
    // mapping.txt: id->a, name->b, ...). Translate those keys to the real
    // field names once on load; returns null when the JSON is not legacy.
    private fun remapLegacyKeys(json: String): String? {
        return try {
            val array = JsonParser.parseString(json).asJsonArray
            if (array.isEmpty) return null
            val legacyToField = mapOf(
                "a" to "id", "b" to "name", "c" to "relativePath",
                "d" to "mimeType", "e" to "size", "f" to "dateAdded"
            )
            val result = JsonArray()
            for (element in array) {
                val obj = element.asJsonObject
                // Strictly legacy entries only: obfuscated id present, real id absent.
                if (!obj.has("a") || obj.has("id")) return null
                val mapped = JsonObject()
                for ((key, value) in obj.entrySet()) {
                    mapped.add(legacyToField[key] ?: key, value)
                }
                result.add(mapped)
            }
            result.toString()
        } catch (e: Exception) {
            null
        }
    }

    // Reflective Gson bypasses Kotlin null-safety: JSON entries missing keys
    // (schema drift, partial corruption) yield nulls in non-null fields and
    // crash far from the cause. Drop unrecoverable entries, repair the rest.
    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
    private fun sanitizeLoaded(list: List<VaultFileMetadata?>): MutableList<VaultFileMetadata> {
        val result = mutableListOf<VaultFileMetadata>()
        for (meta in list) {
            // Without an id the encrypted file is unreachable — skip.
            if (meta == null || meta.id == null) continue
            result.add(
                VaultFileMetadata(
                    id = meta.id,
                    name = meta.name ?: "unknown",
                    relativePath = meta.relativePath ?: "",
                    mimeType = meta.mimeType ?: "application/octet-stream",
                    size = meta.size,
                    dateAdded = meta.dateAdded
                )
            )
        }
        return result
    }

    @Synchronized
    private fun saveMetadata() {
        val json = gson.toJson(metadataCache ?: return)
        val encrypted = CryptoManager.encryptBytes(json.toByteArray())
        // Write to a temp file first, fsync it, then rename atomically:
        // a crash mid-write cannot corrupt the live metadata file, and a
        // power loss right after the rename cannot leave a truncated one
        // (worst case the old index survives).
        val tmpFile = File(vaultDir, "metadata.enc.tmp")
        FileOutputStream(tmpFile).use { out ->
            out.write(encrypted)
            out.fd.sync()
        }
        replaceFile(tmpFile, metadataFile)
    }

    // Atomically replace target with tmpFile so the target is never
    // left half-written.
    private fun replaceFile(tmpFile: File, target: File) {
        if (!tmpFile.renameTo(target)) {
            target.delete()
            if (!tmpFile.renameTo(target)) {
                throw IOException("Failed to replace ${target.name}")
            }
        }
    }

    fun getFilesInFolder(folderPath: String): List<VaultFileMetadata> {
        val all = loadMetadata()
        return all.filter { it.relativePath == folderPath }
    }

    fun getFolders(parentPath: String): List<String> {
        val all = loadMetadata()
        val folders = mutableSetOf<String>()
        val prefix = if (parentPath.isEmpty()) "" else "$parentPath/"
        for (meta in all) {
            val rp = meta.relativePath
            if (rp.startsWith(prefix) && rp != parentPath) {
                val remaining = if (prefix.isEmpty()) rp else rp.removePrefix(prefix)
                val firstSegment = remaining.split("/").first()
                if (firstSegment.isNotEmpty()) {
                    folders.add(firstSegment)
                }
            }
        }
        return folders.sorted()
    }

    fun getAllFolderPaths(): List<String> {
        val all = loadMetadata()
        val paths = mutableSetOf<String>()
        for (meta in all) {
            if (meta.relativePath.isNotEmpty()) {
                val parts = meta.relativePath.split("/")
                for (i in parts.indices) {
                    paths.add(parts.subList(0, i + 1).joinToString("/"))
                }
            }
        }
        // Also add explicitly created empty folders
        return paths.sorted()
    }

    @Synchronized
    fun createFolder(parentPath: String, name: String): String {
        val sanitized = sanitizeName(name)
        require(sanitized.isNotEmpty()) { "Invalid folder name" }
        val folderPath = if (parentPath.isEmpty()) sanitized else "$parentPath/$sanitized"
        val list = mutableMetadata()
        // Already exists (marker, contained file, or subfolder) — no-op.
        if (list.any { it.relativePath == folderPath || it.relativePath.startsWith("$folderPath/") }) {
            return folderPath
        }
        // Create a hidden marker file to persist the folder
        val marker = VaultFileMetadata(
            name = ".folder",
            relativePath = folderPath,
            mimeType = "inode/directory",
            size = 0
        )
        list.add(marker)
        metadataCache = list
        saveMetadata()
        return folderPath
    }

    @Synchronized
    fun importFile(uri: Uri, targetFolder: String): VaultFileMetadata? {
        val resolver = context.contentResolver
        val displayName = sanitizeName(getDisplayName(uri) ?: "unknown").ifEmpty { "unknown" }
        var mimeType = resolver.getType(uri) ?: "application/octet-stream"
        if (mimeType == "application/octet-stream") {
            val ext = displayName.substringAfterLast('.', "").lowercase()
            if (ext.isNotEmpty()) {
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.let { mimeType = it }
            }
        }

        val metadata = VaultFileMetadata(
            name = displayName,
            relativePath = targetFolder,
            mimeType = mimeType
        )

        val encFile = File(filesDir, "${metadata.id}.enc")
        var originalSize = 0L
        try {
            resolver.openInputStream(uri)?.use { input ->
                val counting = CountingInputStream(input)
                encFile.outputStream().use { output ->
                    CryptoManager.encrypt(counting, output)
                }
                originalSize = counting.bytesRead
            } ?: return null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import file", e)
            encFile.delete()
            return null
        }

        metadata.let {
            val updated = it.copy(size = originalSize)
            val list = mutableMetadata()
            list.add(updated)
            metadataCache = list
            saveMetadata()
            return updated
        }
    }

    fun importFolder(treeUri: Uri, targetFolder: String): List<VaultFileMetadata> {
        val docFile = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        // Sanitize once so the marker and the file paths always match.
        val folderName = sanitizeName(docFile.name ?: "imported").ifEmpty { "imported" }
        val subFolder = if (targetFolder.isEmpty()) folderName else "$targetFolder/$folderName"
        createFolder(targetFolder, folderName)
        val imported = mutableListOf<VaultFileMetadata>()
        importDocumentRecursive(docFile, subFolder, imported)
        return imported
    }

    private fun importDocumentRecursive(
        doc: DocumentFile,
        currentFolder: String,
        result: MutableList<VaultFileMetadata>
    ) {
        for (child in doc.listFiles()) {
            if (child.isDirectory) {
                // Skip directories whose name sanitizes to nothing instead of crashing.
                val folderName = sanitizeName(child.name ?: "")
                if (folderName.isEmpty()) continue
                val subFolder = if (currentFolder.isEmpty()) folderName else "$currentFolder/$folderName"
                createFolder(currentFolder, folderName)
                importDocumentRecursive(child, subFolder, result)
            } else {
                child.uri.let { uri ->
                    importFile(uri, currentFolder)?.let { result.add(it) }
                }
            }
        }
    }

    fun decryptToTemp(metadata: VaultFileMetadata): File? {
        val encFile = File(filesDir, "${metadata.id}.enc")
        if (!encFile.exists()) return null

        val ext = metadata.name.substringAfterLast('.', "").trim()
        val suffix = if (ext.isNotEmpty()) ".${ext.take(16)}" else ".bin"
        val tempFile = File.createTempFile("view_", suffix, tempDir)

        return try {
            encFile.inputStream().use { input ->
                tempFile.outputStream().use { output ->
                    CryptoManager.decrypt(input, output)
                }
            }
            tempFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt file ${metadata.id}", e)
            tempFile.delete()
            null
        }
    }

    fun exportFileToTree(metadata: VaultFileMetadata, destDir: DocumentFile): Boolean {
        val encFile = File(filesDir, "${metadata.id}.enc")
        if (!encFile.exists() || !destDir.isDirectory) return false
        val outDoc = uniqueDocumentFile(destDir, metadata.name, metadata.mimeType) ?: return false
        return try {
            encFile.inputStream().use { input ->
                val output = context.contentResolver.openOutputStream(outDoc.uri)
                if (output == null) {
                    outDoc.delete()
                    return false
                }
                output.use { CryptoManager.decrypt(input, it) }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export file ${metadata.id}", e)
            // Remove the partially written destination file.
            outDoc.delete()
            false
        }
    }

    // Export every file under folderPath into destDir, recreating the folder
    // itself and its subtree including empty folders. Returns exported count
    // and total file count.
    fun exportFolderToTree(folderPath: String, destDir: DocumentFile): Pair<Int, Int> {
        val parentPrefix = folderPath.substringBeforeLast('/', "")
        val inScope = loadMetadata().filter {
            it.relativePath == folderPath || it.relativePath.startsWith("$folderPath/")
        }
        fun relativeTo(meta: VaultFileMetadata): String =
            if (parentPrefix.isEmpty()) meta.relativePath
            else meta.relativePath.removePrefix("$parentPrefix/")
        // Folder markers first so empty folders are recreated too.
        for (meta in inScope) {
            if (meta.mimeType == "inode/directory") {
                ensureDocumentPath(destDir, relativeTo(meta))
            }
        }
        val files = inScope.filter { it.mimeType != "inode/directory" }
        var exportedCount = 0
        for (meta in files) {
            val subDir = ensureDocumentPath(destDir, relativeTo(meta)) ?: continue
            if (exportFileToTree(meta, subDir)) {
                exportedCount++
            }
        }
        return exportedCount to files.size
    }

    fun exportAllToTree(treeUri: Uri): Int {
        val rootDir = DocumentFile.fromTreeUri(context, treeUri) ?: return 0
        val all = loadMetadata()
        // Folder markers first so empty folders are recreated too.
        for (meta in all) {
            if (meta.mimeType == "inode/directory") {
                ensureDocumentPath(rootDir, meta.relativePath)
            }
        }
        var exportedCount = 0
        for (meta in all) {
            if (meta.mimeType == "inode/directory") continue
            val subDir = ensureDocumentPath(rootDir, meta.relativePath) ?: continue
            if (exportFileToTree(meta, subDir)) {
                exportedCount++
            }
        }
        return exportedCount
    }

    @Synchronized
    fun deleteFiles(files: List<VaultFileMetadata>) {
        if (files.isEmpty()) return
        val ids = files.map { it.id }.toSet()
        for (id in ids) {
            File(filesDir, "$id.enc").delete()
        }
        val list = mutableMetadata()
        list.removeAll { it.id in ids }
        metadataCache = list
        saveMetadata()
    }

    @Synchronized
    fun deleteFolder(folderPath: String) {
        val list = mutableMetadata()
        val toDelete = list.filter {
            it.relativePath == folderPath || it.relativePath.startsWith("$folderPath/")
        }
        for (meta in toDelete) {
            if (meta.mimeType != "inode/directory") {
                File(filesDir, "${meta.id}.enc").delete()
            }
        }
        list.removeAll(toDelete.toSet())
        metadataCache = list
        saveMetadata()
    }

    @Synchronized
    fun moveFiles(files: List<VaultFileMetadata>, targetFolder: String) {
        val list = mutableMetadata()
        for (meta in files) {
            val idx = list.indexOfFirst { it.id == meta.id }
            if (idx >= 0) {
                list[idx] = list[idx].copy(relativePath = targetFolder)
            }
        }
        metadataCache = list
        saveMetadata()
    }

    @Synchronized
    fun moveFolder(oldPath: String, targetParentPath: String): Boolean {
        if (oldPath.isEmpty()) return false
        if (targetParentPath == oldPath || targetParentPath.startsWith("$oldPath/")) return false

        val folderName = oldPath.substringAfterLast("/")
        val newPath = if (targetParentPath.isEmpty()) folderName else "$targetParentPath/$folderName"
        // Moving to the current parent is a harmless no-op, not a failure.
        if (newPath == oldPath) return true
        // Refuse to silently merge into an existing folder.
        if (folderExists(newPath)) return false

        remapFolderPaths(oldPath, newPath)
        return true
    }

    @Synchronized
    fun renameFile(metadata: VaultFileMetadata, newName: String) {
        val sanitized = sanitizeName(newName)
        if (sanitized.isEmpty()) return
        val list = mutableMetadata()
        val idx = list.indexOfFirst { it.id == metadata.id }
        if (idx >= 0) {
            list[idx] = list[idx].copy(name = sanitized)
            metadataCache = list
            saveMetadata()
        }
    }

    @Synchronized
    fun renameFolder(oldPath: String, newName: String): Boolean {
        val sanitized = sanitizeName(newName)
        if (sanitized.isEmpty()) return false
        val parts = oldPath.split("/")
        val parentPath = parts.dropLast(1).joinToString("/")
        val newPath = if (parentPath.isEmpty()) sanitized else "$parentPath/$sanitized"
        if (newPath == oldPath) return true
        // Refuse to silently merge into an existing folder.
        if (folderExists(newPath)) return false

        remapFolderPaths(oldPath, newPath)
        return true
    }

    private fun folderExists(path: String): Boolean {
        return mutableMetadata().any {
            it.relativePath == path || it.relativePath.startsWith("$path/")
        }
    }

    private fun remapFolderPaths(oldPath: String, newPath: String) {
        val list = mutableMetadata()
        for (i in list.indices) {
            val meta = list[i]
            when {
                meta.relativePath == oldPath -> list[i] = meta.copy(relativePath = newPath)
                meta.relativePath.startsWith("$oldPath/") ->
                    list[i] = meta.copy(relativePath = meta.relativePath.replaceFirst(oldPath, newPath))
            }
        }
        metadataCache = list
        saveMetadata()
    }

    private var tempSweepDone = false

    // Sweep temp files left over from a previous process (killed while an
    // external viewer was open). Runs once per process: sweeping on every
    // vault entry could delete a temp file an external viewer opened in
    // this session is still reading.
    @Synchronized
    fun sweepTempOnce() {
        if (tempSweepDone) return
        tempSweepDone = true
        tempDir.listFiles()?.forEach { it.delete() }
    }

    private var orphanSweepDone = false

    // Delete encrypted blobs no metadata entry references — leftovers of a
    // crash between writing the blob and saving the index. Runs once per
    // process and only after a fully clean index load: a failed or degraded
    // load must never make real files look like orphans.
    @Synchronized
    fun sweepOrphansOnce() {
        if (orphanSweepDone) return
        if (!metadataFile.exists()) return
        val ids = mutableMetadata().map { it.id }.toSet()
        if (!lastLoadClean) return
        orphanSweepDone = true
        filesDir.listFiles()?.forEach { file ->
            val id = file.name.removeSuffix(".enc")
            if (id != file.name && id !in ids) {
                file.delete()
            }
        }
    }

    private fun uniqueDocumentFile(dir: DocumentFile, name: String, mimeType: String): DocumentFile? {
        val baseName = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "").let { if (it == name) "" else ".$it" }
        var candidateName = name
        var counter = 1
        while (dir.findFile(candidateName) != null) {
            candidateName = "${baseName} ($counter)$ext"
            counter++
        }
        return dir.createFile(mimeType, candidateName)
    }

    private fun ensureDocumentPath(rootDir: DocumentFile, relativePath: String): DocumentFile? {
        var current = rootDir
        if (relativePath.isEmpty()) return current
        for (segment in relativePath.split("/")) {
            val existing = current.findFile(segment)
            current = when {
                existing?.isDirectory == true -> existing
                existing == null -> current.createDirectory(segment)
                else -> null
            } ?: return null
        }
        return current
    }

    private fun sanitizeName(name: String): String {
        return name.trim()
            .replace("/", "_")
            .replace("\u0000", "")
            .let { if (it == "." || it == "..") "" else it }
    }

    private fun getDisplayName(uri: Uri): String? {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && idx >= 0) {
                return cursor.getString(idx)
            }
        }
        return uri.lastPathSegment
    }
}

private class CountingInputStream(input: InputStream) : FilterInputStream(input) {
    var bytesRead: Long = 0L
        private set

    override fun read(): Int {
        val b = super.read()
        if (b >= 0) bytesRead++
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val n = super.read(b, off, len)
        if (n > 0) bytesRead += n
        return n
    }
}
