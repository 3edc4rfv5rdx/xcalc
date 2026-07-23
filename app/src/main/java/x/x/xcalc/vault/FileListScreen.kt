package x.x.xcalc.vault

import android.content.ClipData
import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import x.x.xcalc.R
import x.x.xcalc.ui.theme.FolderIconColor
import x.x.xcalc.ui.theme.VaultAccent
import x.x.xcalc.ui.theme.VaultAccentContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class ExportMode { SELECTED, ALL }

sealed class FileListItem {
    data class FolderItem(val name: String, val path: String) : FileListItem()
    data class FileItem(val metadata: VaultFileMetadata) : FileListItem()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FileListScreen(
    repository: VaultRepository,
    onBack: () -> Unit,
    onExternalActivity: () -> Unit
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
    var pendingExportMode by remember { mutableStateOf<ExportMode?>(null) }
    var renameTarget by remember { mutableStateOf<Any?>(null) } // VaultFileMetadata or String (folder path)

    // Decrypted temp files handed to external viewers; deleted on teardown.
    val viewedTemps = remember { mutableStateListOf<File>() }
    val menuContainerColor = VaultAccent
    val menuContentColor = VaultAccentContent

    // Runs on IO in a scope that survives leaving this screen: cleanup
    // happens during teardown and must not block the UI thread.
    val cleanupScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }

    fun deleteViewedTemps() {
        val toDelete = viewedTemps.toList()
        viewedTemps.clear()
        if (toDelete.isEmpty()) return
        cleanupScope.launch {
            toDelete.forEach { it.delete() }
        }
    }

    // Metadata reads hit disk + AES on first load; keep them off the UI thread.
    suspend fun refreshItems() {
        val folder = currentFolder
        val (folders, files) = withContext(Dispatchers.IO) {
            val files = repository.getFilesInFolder(folder)
                .filter { it.mimeType != "inode/directory" }
                .map { FileListItem.FileItem(it) }
            val folders = repository.getFolders(folder)
                .map {
                    val path = if (folder.isEmpty()) it else "$folder/$it"
                    FileListItem.FolderItem(it, path)
                }
            folders to files
        }
        items = folders + files
    }

    // Covers both the initial load and every folder navigation.
    LaunchedEffect(currentFolder) {
        refreshItems()
    }

    // Clean temp files once we leave this screen.
    DisposableEffect(Unit) {
        onDispose {
            deleteViewedTemps()
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
                    context.getString(R.string.imported_files, imported.size)
                } else {
                    context.getString(R.string.imported_failed, imported.size, failedCount)
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
                Toast.makeText(context, context.getString(R.string.imported_files, imported.size), Toast.LENGTH_SHORT).show()
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

    val exportFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        val mode = pendingExportMode
        pendingExportMode = null
        if (uri == null || mode == null) return@rememberLauncherForActivityResult

        scope.launch {
            when (mode) {
                ExportMode.SELECTED -> {
                    val filesToExport = selectedFiles()
                    val foldersToExport = selectedFolders().map { it.path }
                    val (exportedCount, totalCount) = withContext(Dispatchers.IO) {
                        val targetDir = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
                            ?: return@withContext 0 to 0
                        var count = 0
                        var total = filesToExport.size
                        for (metadata in filesToExport) {
                            if (repository.exportFileToTree(metadata, targetDir)) {
                                count++
                            }
                        }
                        for (folderPath in foldersToExport) {
                            val (exported, totalInFolder) =
                                repository.exportFolderToTree(folderPath, targetDir)
                            count += exported
                            total += totalInFolder
                        }
                        count to total
                    }
                    val failedCount = totalCount - exportedCount
                    val message = if (failedCount == 0) {
                        context.getString(R.string.exported_files, exportedCount)
                    } else {
                        context.getString(R.string.exported_failed, exportedCount, failedCount)
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    if (exportedCount > 0) {
                        selected.clear()
                    }
                }
                ExportMode.ALL -> {
                    val totalFiles = withContext(Dispatchers.IO) {
                        repository.loadMetadata().count { it.mimeType != "inode/directory" }
                    }
                    val exportedCount = withContext(Dispatchers.IO) {
                        repository.exportAllToTree(uri)
                    }
                    val failedCount = totalFiles - exportedCount
                    val message = if (failedCount == 0) {
                        context.getString(R.string.exported_files, exportedCount)
                    } else {
                        context.getString(R.string.exported_failed, exportedCount, failedCount)
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun handleBack() {
        when {
            selected.isNotEmpty() -> selected.clear()
            currentFolder.isNotEmpty() -> {
                val parts = currentFolder.split("/")
                currentFolder = parts.dropLast(1).joinToString("/")
                selected.clear()
            }
            else -> {
                deleteViewedTemps()
                onBack()
            }
        }
    }

    // System back mirrors the toolbar arrow: clear selection, go up, leave.
    BackHandler { handleBack() }

    Scaffold(
        topBar = {
            if (selected.isNotEmpty()) {
                // Action toolbar when items are selected
                TopAppBar(
                    title = { Text("${selected.size}") },
                    navigationIcon = {
                        IconButton(onClick = { selected.clear() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.clear_selection))
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
                                        viewedTemps.add(tempFile)
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            tempFile
                                        )
                                        val mime = resolveMimeType(meta.name, meta.mimeType)
                                        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, mime)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            // ClipData propagates the URI grant to
                                            // whichever activity handles the intent.
                                            clipData = ClipData.newRawUri(null, uri)
                                        }
                                        try {
                                            context.startActivity(viewIntent)
                                            // The viewer covers us and fires ON_STOP;
                                            // exempt that one stop from vault relock.
                                            onExternalActivity()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, context.getString(R.string.no_app_to_open), Toast.LENGTH_SHORT).show()
                                        }
                                        selected.clear()
                                    } else {
                                        Toast.makeText(context, context.getString(R.string.open_failed), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }) {
                                Icon(Icons.Default.Visibility, stringResource(R.string.view))
                            }
                        }
                        // Export (files and folders)
                        IconButton(onClick = { showExportDialog = true }) {
                            Icon(Icons.Default.SaveAlt, stringResource(R.string.export))
                        }
                        // Move
                        IconButton(onClick = { showMoveDialog = true }) {
                            Icon(Icons.AutoMirrored.Filled.DriveFileMove, stringResource(R.string.move))
                        }
                        // Rename (single item only)
                        if (selected.size == 1) {
                            IconButton(onClick = {
                                renameTarget = if (selectedFiles().isNotEmpty()) selectedFiles().first()
                                else if (selectedFolders().isNotEmpty()) selectedFolders().first().path
                                else null
                                if (renameTarget != null) showRenameDialog = true
                            }) {
                                Icon(Icons.Default.Edit, stringResource(R.string.rename))
                            }
                        }
                        // Delete
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, stringResource(R.string.delete))
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
                            if (currentFolder.isEmpty()) stringResource(R.string.vault_title)
                            else currentFolder.split("/").last()
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { handleBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                        }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { showOverflowMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more))
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false },
                                containerColor = menuContainerColor
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.export_all)) },
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
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add))
                    }
                    DropdownMenu(
                        expanded = showFabMenu,
                        onDismissRequest = { showFabMenu = false },
                        containerColor = menuContainerColor
                    ) {
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Default.FileOpen, null) },
                            text = { Text(stringResource(R.string.add_files)) },
                            colors = MenuDefaults.itemColors(
                                textColor = menuContentColor,
                                leadingIconColor = menuContentColor,
                                trailingIconColor = menuContentColor
                            ),
                            onClick = {
                                showFabMenu = false
                                // The picker covers us and fires ON_STOP;
                                // exempt that one stop from vault relock.
                                onExternalActivity()
                                filePickerLauncher.launch(arrayOf("*/*"))
                            }
                        )
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Default.FolderOpen, null) },
                            text = { Text(stringResource(R.string.add_folder)) },
                            colors = MenuDefaults.itemColors(
                                textColor = menuContentColor,
                                leadingIconColor = menuContentColor,
                                trailingIconColor = menuContentColor
                            ),
                            onClick = {
                                showFabMenu = false
                                // Exempt the picker's ON_STOP from vault relock.
                                onExternalActivity()
                                folderPickerLauncher.launch(null)
                            }
                        )
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Default.CreateNewFolder, null) },
                            text = { Text(stringResource(R.string.new_folder)) },
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
                    stringResource(R.string.empty),
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
                                is FileListItem.FolderItem -> FolderIconColor
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
            title = stringResource(R.string.new_folder),
            placeholder = stringResource(R.string.folder_name),
            onConfirm = { name ->
                if (name.isNotBlank()) {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            repository.createFolder(currentFolder, name.trim())
                        }
                        refreshItems()
                    }
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
            title = stringResource(R.string.rename),
            placeholder = stringResource(R.string.new_name),
            initialValue = currentName,
            onConfirm = { newName ->
                if (newName.isNotBlank()) {
                    val target = renameTarget
                    scope.launch {
                        val renamed = withContext(Dispatchers.IO) {
                            when (target) {
                                is VaultFileMetadata -> {
                                    repository.renameFile(target, newName.trim())
                                    true
                                }
                                is String -> repository.renameFolder(target, newName.trim())
                                else -> true
                            }
                        }
                        if (!renamed) {
                            Toast.makeText(context, context.getString(R.string.folder_exists), Toast.LENGTH_SHORT).show()
                        }
                        refreshItems()
                        selected.clear()
                    }
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
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(stringResource(R.string.delete_items, selected.size) + "?") },
            confirmButton = {
                Button(onClick = {
                    val files = selectedFiles()
                    val folders = selectedFolders()
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            repository.deleteFiles(files)
                            for (f in folders) repository.deleteFolder(f.path)
                        }
                        selected.clear()
                        refreshItems()
                    }
                    showDeleteDialog = false
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // Move dialog
    if (showMoveDialog) {
        val rootLabel = stringResource(R.string.root_folder)
        val allFolders = remember { listOf(rootLabel) + repository.getAllFolderPaths() }
        var selectedFolder by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showMoveDialog = false },
            title = { Text(stringResource(R.string.move_to)) },
            text = {
                LazyColumn {
                    items(allFolders) { folder ->
                        val path = if (folder == rootLabel) "" else folder
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
                    val files = selectedFiles()
                    val folders = selectedFolders()
                    val target = selectedFolder
                    scope.launch {
                        val failedMoves = withContext(Dispatchers.IO) {
                            repository.moveFiles(files, target)
                            folders.count { !repository.moveFolder(it.path, target) }
                        }
                        selected.clear()
                        refreshItems()
                        if (failedMoves > 0) {
                            Toast.makeText(context, context.getString(R.string.folders_not_moved), Toast.LENGTH_SHORT).show()
                        }
                    }
                    showMoveDialog = false
                }) { Text(stringResource(R.string.move)) }
            },
            dismissButton = {
                TextButton(onClick = { showMoveDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // Export selected files/folders dialog
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text(stringResource(R.string.export)) },
            text = { Text(stringResource(R.string.choose_destination_selected, selected.size) + ".") },
            confirmButton = {
                Button(onClick = {
                    showExportDialog = false
                    pendingExportMode = ExportMode.SELECTED
                    // Exempt the picker's ON_STOP from vault relock.
                    onExternalActivity()
                    exportFolderLauncher.launch(null)
                }) { Text(stringResource(R.string.export)) }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // Export all dialog
    if (showExportAllDialog) {
        AlertDialog(
            onDismissRequest = { showExportAllDialog = false },
            title = { Text(stringResource(R.string.export_all)) },
            text = { Text(stringResource(R.string.choose_destination_all) + ".") },
            confirmButton = {
                Button(onClick = {
                    showExportAllDialog = false
                    pendingExportMode = ExportMode.ALL
                    // Exempt the picker's ON_STOP from vault relock.
                    onExternalActivity()
                    exportFolderLauncher.launch(null)
                }) { Text(stringResource(R.string.export)) }
            },
            dismissButton = {
                TextButton(onClick = { showExportAllDialog = false }) { Text(stringResource(R.string.cancel)) }
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
            Button(onClick = { onConfirm(text) }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
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

private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

private fun formatDate(millis: Long): String {
    return dateFormat.format(Date(millis))
}
