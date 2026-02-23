package x.x.xcalc.vault

import android.content.Intent
import android.os.Environment
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class FileListItem {
    data class FolderItem(val name: String, val path: String) : FileListItem()
    data class FileItem(val metadata: VaultFileMetadata) : FileListItem()
}

private data class ViewedTemp(
    val metadata: VaultFileMetadata,
    val tempFile: File,
    val initialHash: Long
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FileListScreen(
    repository: VaultRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentFolder by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<FileListItem>>(emptyList()) }
    val selected = remember { mutableStateListOf<String>() } // IDs or folder paths
    var showFabMenu by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showExportAllDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Any?>(null) } // VaultFileMetadata or String (folder path)

    // Temp file tracking for view action
    val viewedTemps = remember { mutableStateListOf<ViewedTemp>() }
    val menuContainerColor = Color(0xFF5FA8B8)
    val menuContentColor = Color.White

    fun refreshItems() {
        val files = repository.getFilesInFolder(currentFolder)
            .filter { it.mimeType != "inode/directory" }
            .map { FileListItem.FileItem(it) }
        val folders = repository.getFolders(currentFolder)
            .map {
                val path = if (currentFolder.isEmpty()) it else "$currentFolder/$it"
                FileListItem.FolderItem(it, path)
            }
        items = folders + files
    }

    DisposableEffect(currentFolder) {
        refreshItems()
        onDispose { }
    }

    // Persist edits and clean temp files once we leave this screen.
    DisposableEffect(Unit) {
        onDispose {
            viewedTemps.forEach { viewed ->
                if (!viewed.tempFile.exists()) return@forEach
                val newHash = viewed.tempFile.lastModified() + viewed.tempFile.length()
                if (newHash != viewed.initialHash) {
                    repository.reEncryptFromTemp(viewed.metadata, viewed.tempFile)
                }
                viewed.tempFile.delete()
            }
            viewedTemps.clear()
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val imported = withContext(Dispatchers.IO) {
                    uris.mapNotNull { repository.importFile(it, currentFolder) }
                }
                refreshItems()
                val failedCount = uris.size - imported.size
                val message = if (failedCount == 0) {
                    "Imported ${imported.size} file(s)"
                } else {
                    "Imported ${imported.size}, failed $failedCount"
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val imported = withContext(Dispatchers.IO) {
                    repository.importFolder(uri, currentFolder)
                }
                refreshItems()
                Toast.makeText(context, "Imported ${imported.size} file(s)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun selectedFiles(): List<VaultFileMetadata> =
        items.filterIsInstance<FileListItem.FileItem>()
            .filter { it.metadata.id in selected }
            .map { it.metadata }

    fun selectedFolders(): List<FileListItem.FolderItem> =
        items.filterIsInstance<FileListItem.FolderItem>()
            .filter { it.path in selected }

    fun handleBack() {
        when {
            selected.isNotEmpty() -> selected.clear()
            currentFolder.isNotEmpty() -> {
                val parts = currentFolder.split("/")
                currentFolder = parts.dropLast(1).joinToString("/")
                selected.clear()
                refreshItems()
            }
            else -> {
                repository.clearTemp()
                onBack()
            }
        }
    }

    Scaffold(
        topBar = {
            if (selected.isNotEmpty()) {
                // Action toolbar when items are selected
                TopAppBar(
                    title = { Text("${selected.size}") },
                    navigationIcon = {
                        IconButton(onClick = { selected.clear() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Clear selection")
                        }
                    },
                    actions = {
                        // View (single file only)
                        if (selected.size == 1 && selectedFiles().size == 1) {
                            IconButton(onClick = {
                                val meta = selectedFiles().first()
                                scope.launch {
                                    val tempFile = withContext(Dispatchers.IO) {
                                        repository.decryptToTemp(meta)
                                    }
                                    if (tempFile != null) {
                                        viewedTemps.add(
                                            ViewedTemp(
                                                metadata = meta,
                                                tempFile = tempFile,
                                                initialHash = tempFile.lastModified() + tempFile.length()
                                            )
                                        )
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            tempFile
                                        )
                                        val mime = resolveMimeType(meta.name, meta.mimeType)
                                        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, mime)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        val targets = context.packageManager
                                            .queryIntentActivities(viewIntent, 0)
                                        for (ri in targets) {
                                            context.grantUriPermission(
                                                ri.activityInfo.packageName,
                                                uri,
                                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                                            )
                                        }
                                        try {
                                            context.startActivity(viewIntent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "No app to open this file", Toast.LENGTH_SHORT).show()
                                        }
                                        selected.clear()
                                    } else {
                                        Toast.makeText(context, "Failed to open file", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }) {
                                Icon(Icons.Default.Visibility, "View")
                            }
                        }
                        // Export
                        if (selectedFiles().isNotEmpty()) {
                            IconButton(onClick = { showExportDialog = true }) {
                                Icon(Icons.Default.SaveAlt, "Export")
                            }
                        }
                        // Move
                        IconButton(onClick = { showMoveDialog = true }) {
                            Icon(Icons.AutoMirrored.Filled.DriveFileMove, "Move")
                        }
                        // Rename (single item only)
                        if (selected.size == 1) {
                            IconButton(onClick = {
                                renameTarget = if (selectedFiles().isNotEmpty()) selectedFiles().first()
                                else if (selectedFolders().isNotEmpty()) selectedFolders().first().path
                                else null
                                if (renameTarget != null) showRenameDialog = true
                            }) {
                                Icon(Icons.Default.Edit, "Rename")
                            }
                        }
                        // Delete
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, "Delete")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            if (currentFolder.isEmpty()) "Vault"
                            else currentFolder.split("/").last()
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { handleBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { showOverflowMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More")
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false },
                                containerColor = menuContainerColor
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Export all") },
                                    colors = MenuDefaults.itemColors(
                                        textColor = menuContentColor,
                                        leadingIconColor = menuContentColor,
                                        trailingIconColor = menuContentColor
                                    ),
                                    onClick = {
                                        showOverflowMenu = false
                                        showExportAllDialog = true
                                    }
                                )
                            }
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (selected.isEmpty()) {
                Box {
                    FloatingActionButton(
                        onClick = { showFabMenu = true },
                        containerColor = menuContainerColor,
                        contentColor = menuContentColor
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add")
                    }
                    DropdownMenu(
                        expanded = showFabMenu,
                        onDismissRequest = { showFabMenu = false },
                        containerColor = menuContainerColor
                    ) {
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Default.FileOpen, null) },
                            text = { Text("Add files") },
                            colors = MenuDefaults.itemColors(
                                textColor = menuContentColor,
                                leadingIconColor = menuContentColor,
                                trailingIconColor = menuContentColor
                            ),
                            onClick = {
                                showFabMenu = false
                                filePickerLauncher.launch(arrayOf("*/*"))
                            }
                        )
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Default.FolderOpen, null) },
                            text = { Text("Add folder") },
                            colors = MenuDefaults.itemColors(
                                textColor = menuContentColor,
                                leadingIconColor = menuContentColor,
                                trailingIconColor = menuContentColor
                            ),
                            onClick = {
                                showFabMenu = false
                                folderPickerLauncher.launch(null)
                            }
                        )
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Default.CreateNewFolder, null) },
                            text = { Text("New folder") },
                            colors = MenuDefaults.itemColors(
                                textColor = menuContentColor,
                                leadingIconColor = menuContentColor,
                                trailingIconColor = menuContentColor
                            ),
                            onClick = {
                                showFabMenu = false
                                showNewFolderDialog = true
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Empty",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(items, key = {
                    when (it) {
                        is FileListItem.FolderItem -> "folder:${it.path}"
                        is FileListItem.FileItem -> "file:${it.metadata.id}"
                    }
                }) { item ->
                    val isSelected = when (item) {
                        is FileListItem.FolderItem -> item.path in selected
                        is FileListItem.FileItem -> item.metadata.id in selected
                    }
                    val id = when (item) {
                        is FileListItem.FolderItem -> item.path
                        is FileListItem.FileItem -> item.metadata.id
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (selected.isNotEmpty()) {
                                        if (isSelected) selected.remove(id) else selected.add(id)
                                    } else {
                                        when (item) {
                                            is FileListItem.FolderItem -> {
                                                currentFolder = item.path
                                                selected.clear()
                                                refreshItems()
                                            }
                                            is FileListItem.FileItem -> {
                                                selected.add(id)
                                            }
                                        }
                                    }
                                },
                                onLongClick = {
                                    if (!isSelected) selected.add(id)
                                }
                            )
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when (item) {
                                is FileListItem.FolderItem -> Icons.Default.Folder
                                is FileListItem.FileItem -> Icons.AutoMirrored.Filled.InsertDriveFile
                            },
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = when (item) {
                                is FileListItem.FolderItem -> Color(0xFFFFB74D)
                                is FileListItem.FileItem -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when (item) {
                                    is FileListItem.FolderItem -> item.name
                                    is FileListItem.FileItem -> item.metadata.name
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (item is FileListItem.FileItem) {
                                Text(
                                    text = formatFileSize(item.metadata.size) + " · " +
                                            formatDate(item.metadata.dateAdded),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // New folder dialog
    if (showNewFolderDialog) {
        InputDialog(
            title = "New folder",
            placeholder = "Folder name",
            onConfirm = { name ->
                if (name.isNotBlank()) {
                    repository.createFolder(currentFolder, name.trim())
                    refreshItems()
                }
                showNewFolderDialog = false
            },
            onDismiss = { showNewFolderDialog = false }
        )
    }

    // Rename dialog
    if (showRenameDialog && renameTarget != null) {
        val currentName = when (val t = renameTarget) {
            is VaultFileMetadata -> t.name
            is String -> t.split("/").last()
            else -> ""
        }
        InputDialog(
            title = "Rename",
            placeholder = "New name",
            initialValue = currentName,
            onConfirm = { newName ->
                if (newName.isNotBlank()) {
                    when (val t = renameTarget) {
                        is VaultFileMetadata -> repository.renameFile(t, newName.trim())
                        is String -> repository.renameFolder(t, newName.trim())
                    }
                    refreshItems()
                    selected.clear()
                }
                showRenameDialog = false
                renameTarget = null
            },
            onDismiss = {
                showRenameDialog = false
                renameTarget = null
            }
        )
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete") },
            text = { Text("Delete ${selected.size} item(s)?") },
            confirmButton = {
                Button(onClick = {
                    for (f in selectedFiles()) repository.deleteFile(f)
                    for (f in selectedFolders()) repository.deleteFolder(f.path)
                    selected.clear()
                    refreshItems()
                    showDeleteDialog = false
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Move dialog
    if (showMoveDialog) {
        val allFolders = remember { listOf("(Root)") + repository.getAllFolderPaths() }
        var selectedFolder by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showMoveDialog = false },
            title = { Text("Move to") },
            text = {
                LazyColumn {
                    items(allFolders) { folder ->
                        val path = if (folder == "(Root)") "" else folder
                        val isCurrentTarget = path == selectedFolder
                        Text(
                            text = folder,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedFolder = path }
                                .background(
                                    if (isCurrentTarget) MaterialTheme.colorScheme.primaryContainer
                                    else Color.Transparent
                                )
                                .padding(12.dp),
                            fontWeight = if (isCurrentTarget) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    repository.moveFiles(selectedFiles(), selectedFolder)
                    var failedMoves = 0
                    for (folder in selectedFolders()) {
                        if (!repository.moveFolder(folder.path, selectedFolder)) {
                            failedMoves++
                        }
                    }
                    selected.clear()
                    refreshItems()
                    showMoveDialog = false
                    if (failedMoves > 0) {
                        Toast.makeText(context, "Some folders could not be moved", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Move") }
            },
            dismissButton = {
                TextButton(onClick = { showMoveDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Export selected files dialog
    if (showExportDialog) {
        val count = selectedFiles().size
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export") },
            text = { Text("Export $count file(s) to Documents/?") },
            confirmButton = {
                Button(onClick = {
                    showExportDialog = false
                    val filesToExport = selectedFiles()
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            val docsDir = Environment.getExternalStoragePublicDirectory(
                                Environment.DIRECTORY_DOCUMENTS
                            )
                            for (meta in filesToExport) {
                                repository.exportFile(meta, docsDir)
                            }
                        }
                        Toast.makeText(context, "Exported to Documents/", Toast.LENGTH_SHORT).show()
                        selected.clear()
                    }
                }) { Text("Export") }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Export all dialog
    if (showExportAllDialog) {
        AlertDialog(
            onDismissRequest = { showExportAllDialog = false },
            title = { Text("Export all") },
            text = { Text("Export all files to Documents/xcalc_export/?") },
            confirmButton = {
                Button(onClick = {
                    showExportAllDialog = false
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            repository.exportAll()
                        }
                        Toast.makeText(context, "Exported to Documents/xcalc_export/", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Export") }
            },
            dismissButton = {
                TextButton(onClick = { showExportAllDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun InputDialog(
    title: String,
    placeholder: String,
    initialValue: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(placeholder) },
                singleLine = true
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(text) }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun resolveMimeType(fileName: String, storedMime: String): String {
    if (storedMime != "application/octet-stream") return storedMime
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: storedMime
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
        else -> "${"%.1f".format(bytes / (1024.0 * 1024.0 * 1024.0))} GB"
    }
}

private fun formatDate(millis: Long): String {
    val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    return sdf.format(Date(millis))
}
