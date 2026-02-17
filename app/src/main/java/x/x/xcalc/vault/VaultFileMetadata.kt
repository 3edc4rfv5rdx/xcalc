package x.x.xcalc.vault

import java.util.UUID

data class VaultFileMetadata(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val relativePath: String = "",
    val mimeType: String = "application/octet-stream",
    val size: Long = 0L,
    val dateAdded: Long = System.currentTimeMillis()
) {
    val fullPath: String
        get() = if (relativePath.isEmpty()) name else "$relativePath/$name"

    val isInFolder: Boolean
        get() = relativePath.isNotEmpty()
}
