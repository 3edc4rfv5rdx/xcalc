package x.x.xcalc.vault

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

class VaultRepository(private val context: Context) {

    private val gson = Gson()
    private val vaultDir = File(context.filesDir, "vault").apply { mkdirs() }
    private val filesDir = File(vaultDir, "files").apply { mkdirs() }
    private val metadataFile = File(vaultDir, "metadata.enc")
    private val tempDir = File(context.cacheDir, "vault_temp").apply { mkdirs() }

    private companion object {
        private const val TAG = "VaultRepository"
    }

    private var metadataCache: MutableList<VaultFileMetadata>? = null

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
            val type = object : TypeToken<List<VaultFileMetadata>>() {}.type
            val list: List<VaultFileMetadata>? = gson.fromJson(json, type)
            (list ?: emptyList()).toMutableList()
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

    @Synchronized
    private fun saveMetadata() {
        val json = gson.toJson(metadataCache ?: return)
        val encrypted = CryptoManager.encryptBytes(json.toByteArray())
        // Write to a temp file first, then rename atomically so a crash
        // mid-write cannot corrupt the live metadata file.
        val tmpFile = File(vaultDir, "metadata.enc.tmp")
        tmpFile.writeBytes(encrypted)
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
        // Create a hidden marker file to persist the folder
        val marker = VaultFileMetadata(
            name = ".folder",
            relativePath = folderPath,
            mimeType = "inode/directory",
            size = 0
        )
        val list = mutableMetadata()
        list.add(marker)
        metadataCache = list
        saveMetadata()
        return folderPath
    }

    @Synchronized
    fun importFile(uri: Uri, targetFolder: String): VaultFileMetadata? {
        val resolver = context.contentResolver
        val displayName = getDisplayName(uri) ?: "unknown"
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
        val folderName = docFile.name ?: "imported"
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
                val folderName = child.name ?: continue
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

    @Synchronized
    fun reEncryptFromTemp(metadata: VaultFileMetadata, tempFile: File) {
        val encFile = File(filesDir, "${metadata.id}.enc")
        // Encrypt to a temp file first so a failure cannot destroy the
        // original ciphertext.
        val tmpEnc = File(filesDir, "${metadata.id}.enc.tmp")
        val originalSize: Long
        try {
            tempFile.inputStream().use { input ->
                val counting = CountingInputStream(input)
                tmpEnc.outputStream().use { output ->
                    CryptoManager.encrypt(counting, output)
                }
                originalSize = counting.bytesRead
            }
        } catch (e: Exception) {
            tmpEnc.delete()
            throw e
        }
        replaceFile(tmpEnc, encFile)
        val list = mutableMetadata()
        val idx = list.indexOfFirst { it.id == metadata.id }
        if (idx >= 0) {
            list[idx] = list[idx].copy(size = originalSize, dateAdded = System.currentTimeMillis())
            metadataCache = list
            saveMetadata()
        }
    }

    fun exportFile(metadata: VaultFileMetadata, destDir: File): File? {
        val encFile = File(filesDir, "${metadata.id}.enc")
        if (!encFile.exists()) return null
        destDir.mkdirs()
        val outFile = uniqueFile(destDir, metadata.name)
        encFile.inputStream().use { input ->
            outFile.outputStream().use { output ->
                CryptoManager.decrypt(input, output)
            }
        }
        return outFile
    }

    fun exportFileToTree(metadata: VaultFileMetadata, destDir: DocumentFile): Boolean {
        val encFile = File(filesDir, "${metadata.id}.enc")
        if (!encFile.exists() || !destDir.isDirectory) return false
        val outDoc = uniqueDocumentFile(destDir, metadata.name, metadata.mimeType) ?: return false
        return try {
            encFile.inputStream().use { input ->
                context.contentResolver.openOutputStream(outDoc.uri)?.use { output ->
                    CryptoManager.decrypt(input, output)
                } ?: return false
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export file ${metadata.id}", e)
            false
        }
    }

    fun exportAllToTree(treeUri: Uri): Int {
        val rootDir = DocumentFile.fromTreeUri(context, treeUri) ?: return 0
        val all = loadMetadata().filter { it.mimeType != "inode/directory" }
        var exportedCount = 0
        for (meta in all) {
            val subDir = ensureDocumentPath(rootDir, meta.relativePath) ?: continue
            if (exportFileToTree(meta, subDir)) {
                exportedCount++
            }
        }
        return exportedCount
    }

    @Synchronized
    fun deleteFile(metadata: VaultFileMetadata) {
        val encFile = File(filesDir, "${metadata.id}.enc")
        encFile.delete()
        val list = mutableMetadata()
        list.removeAll { it.id == metadata.id }
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
        if (newPath == oldPath) return false

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
    fun renameFolder(oldPath: String, newName: String) {
        val sanitized = sanitizeName(newName)
        if (sanitized.isEmpty()) return
        val parts = oldPath.split("/")
        val parentPath = parts.dropLast(1).joinToString("/")
        val newPath = if (parentPath.isEmpty()) sanitized else "$parentPath/$sanitized"

        remapFolderPaths(oldPath, newPath)
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

    fun clearTemp() {
        tempDir.listFiles()?.forEach { it.delete() }
    }

    fun deleteOriginal(uri: Uri) {
        try {
            DocumentFile.fromSingleUri(context, uri)?.delete()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete original file: $uri", e)
        }
    }

    fun deleteOriginalTree(uri: Uri) {
        try {
            DocumentFile.fromTreeUri(context, uri)?.delete()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete original tree: $uri", e)
        }
    }

    private fun uniqueFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val baseName = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "").let { if (it == name) "" else ".$it" }
        var counter = 1
        while (candidate.exists()) {
            candidate = File(dir, "${baseName} ($counter)$ext")
            counter++
        }
        return candidate
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

    fun getEncryptedFile(metadata: VaultFileMetadata): File {
        return File(filesDir, "${metadata.id}.enc")
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
