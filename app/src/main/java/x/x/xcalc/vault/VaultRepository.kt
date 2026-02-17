package x.x.xcalc.vault

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

class VaultRepository(private val context: Context) {

    private val gson = Gson()
    private val vaultDir = File(context.filesDir, "vault").apply { mkdirs() }
    private val filesDir = File(vaultDir, "files").apply { mkdirs() }
    private val metadataFile = File(vaultDir, "metadata.enc")
    private val tempDir = File(context.cacheDir, "vault_temp").apply { mkdirs() }

    private var metadataCache: MutableList<VaultFileMetadata>? = null

    fun loadMetadata(): List<VaultFileMetadata> {
        metadataCache?.let { return it }
        if (!metadataFile.exists()) {
            metadataCache = mutableListOf()
            return metadataCache!!
        }
        val encrypted = metadataFile.readBytes()
        val json = String(CryptoManager.decryptBytes(encrypted))
        val type = object : TypeToken<List<VaultFileMetadata>>() {}.type
        val list: List<VaultFileMetadata> = gson.fromJson(json, type)
        metadataCache = list.toMutableList()
        return metadataCache!!
    }

    private fun saveMetadata() {
        val json = gson.toJson(metadataCache ?: return)
        val encrypted = CryptoManager.encryptBytes(json.toByteArray())
        metadataFile.writeBytes(encrypted)
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

    private val folderMarkers = mutableSetOf<String>()

    fun createFolder(parentPath: String, name: String): String {
        val folderPath = if (parentPath.isEmpty()) name else "$parentPath/$name"
        folderMarkers.add(folderPath)
        // Create a hidden marker file to persist the folder
        val marker = VaultFileMetadata(
            name = ".folder",
            relativePath = folderPath,
            mimeType = "inode/directory",
            size = 0
        )
        val list = loadMetadata() as MutableList
        list.add(marker)
        metadataCache = list
        saveMetadata()
        return folderPath
    }

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
        resolver.openInputStream(uri)?.use { input ->
            encFile.outputStream().use { output ->
                CryptoManager.encrypt(input, output)
            }
        } ?: return null

        metadata.let {
            val updated = it.copy(size = encFile.length())
            val list = loadMetadata() as MutableList
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
        } catch (_: Exception) {
            tempFile.delete()
            null
        }
    }

    fun reEncryptFromTemp(metadata: VaultFileMetadata, tempFile: File) {
        val encFile = File(filesDir, "${metadata.id}.enc")
        tempFile.inputStream().use { input ->
            encFile.outputStream().use { output ->
                CryptoManager.encrypt(input, output)
            }
        }
        val list = loadMetadata() as MutableList
        val idx = list.indexOfFirst { it.id == metadata.id }
        if (idx >= 0) {
            list[idx] = list[idx].copy(size = encFile.length(), dateAdded = System.currentTimeMillis())
            metadataCache = list
            saveMetadata()
        }
    }

    fun exportFile(metadata: VaultFileMetadata, destDir: File): File? {
        val encFile = File(filesDir, "${metadata.id}.enc")
        if (!encFile.exists()) return null
        destDir.mkdirs()
        val outFile = File(destDir, metadata.name)
        encFile.inputStream().use { input ->
            outFile.outputStream().use { output ->
                CryptoManager.decrypt(input, output)
            }
        }
        return outFile
    }

    fun exportAll(): File {
        val exportDir = File(
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS),
            "xcalc_export"
        )
        exportDir.mkdirs()
        val all = loadMetadata().filter { it.mimeType != "inode/directory" }
        for (meta in all) {
            val subDir = if (meta.relativePath.isEmpty()) exportDir else File(exportDir, meta.relativePath).apply { mkdirs() }
            exportFile(meta, subDir)
        }
        return exportDir
    }

    fun deleteFile(metadata: VaultFileMetadata) {
        val encFile = File(filesDir, "${metadata.id}.enc")
        encFile.delete()
        val list = loadMetadata() as MutableList
        list.removeAll { it.id == metadata.id }
        metadataCache = list
        saveMetadata()
    }

    fun deleteFolder(folderPath: String) {
        val list = loadMetadata() as MutableList
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

    fun moveFiles(files: List<VaultFileMetadata>, targetFolder: String) {
        val list = loadMetadata() as MutableList
        for (meta in files) {
            val idx = list.indexOfFirst { it.id == meta.id }
            if (idx >= 0) {
                list[idx] = list[idx].copy(relativePath = targetFolder)
            }
        }
        metadataCache = list
        saveMetadata()
    }

    fun renameFile(metadata: VaultFileMetadata, newName: String) {
        val list = loadMetadata() as MutableList
        val idx = list.indexOfFirst { it.id == metadata.id }
        if (idx >= 0) {
            list[idx] = list[idx].copy(name = newName)
            metadataCache = list
            saveMetadata()
        }
    }

    fun renameFolder(oldPath: String, newName: String) {
        val parts = oldPath.split("/")
        val parentPath = parts.dropLast(1).joinToString("/")
        val newPath = if (parentPath.isEmpty()) newName else "$parentPath/$newName"

        val list = loadMetadata() as MutableList
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
        } catch (_: Exception) { }
    }

    fun deleteOriginalTree(uri: Uri) {
        try {
            DocumentFile.fromTreeUri(context, uri)?.delete()
        } catch (_: Exception) { }
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
