package x.x.xcalc.vault

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class VaultRepositoryTest {

    private lateinit var repo: VaultRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Clean vault directory before each test
        val vaultDir = File(context.filesDir, "vault")
        vaultDir.deleteRecursively()
        repo = VaultRepository(context)
    }

    // --- loadMetadata ---

    @Test
    fun loadMetadataOnEmptyVault() {
        val meta = repo.loadMetadata()
        assertTrue(meta.isEmpty())
    }

    // --- createFolder / getFolders ---

    @Test
    fun createFolderAtRoot() {
        val path = repo.createFolder("", "Photos")
        assertEquals("Photos", path)
        val folders = repo.getFolders("")
        assertTrue(folders.contains("Photos"))
    }

    @Test
    fun createNestedFolder() {
        repo.createFolder("", "Documents")
        val path = repo.createFolder("Documents", "Work")
        assertEquals("Documents/Work", path)
        val subFolders = repo.getFolders("Documents")
        assertTrue(subFolders.contains("Work"))
    }

    @Test
    fun getFoldersEmpty() {
        val folders = repo.getFolders("")
        assertTrue(folders.isEmpty())
    }

    // --- getAllFolderPaths ---

    @Test
    fun getAllFolderPaths() {
        repo.createFolder("", "A")
        repo.createFolder("A", "B")
        repo.createFolder("A/B", "C")
        val paths = repo.getAllFolderPaths()
        assertTrue(paths.contains("A"))
        assertTrue(paths.contains("A/B"))
        assertTrue(paths.contains("A/B/C"))
    }

    // --- getFilesInFolder ---

    @Test
    fun getFilesInFolderEmpty() {
        repo.createFolder("", "Empty")
        val files = repo.getFilesInFolder("Empty")
        // Only the .folder marker should be present
        assertEquals(1, files.size)
        assertEquals(".folder", files[0].name)
    }

    // --- deleteFolder ---

    @Test
    fun deleteFolderRemovesNested() {
        repo.createFolder("", "Parent")
        repo.createFolder("Parent", "Child")
        repo.createFolder("Parent/Child", "Grand")
        repo.deleteFolder("Parent")
        val all = repo.loadMetadata()
        val parentEntries = all.filter {
            it.relativePath == "Parent" || it.relativePath.startsWith("Parent/")
        }
        assertTrue(parentEntries.isEmpty())
    }

    // --- moveFolder ---

    @Test
    fun moveFolderBasic() {
        repo.createFolder("", "Src")
        repo.createFolder("", "Dst")
        val result = repo.moveFolder("Src", "Dst")
        assertTrue(result)
        val paths = repo.getAllFolderPaths()
        assertTrue(paths.contains("Dst/Src"))
        assertFalse(paths.contains("Src"))
    }

    @Test
    fun moveFolderIntoItself() {
        repo.createFolder("", "Folder")
        val result = repo.moveFolder("Folder", "Folder")
        assertFalse(result)
    }

    @Test
    fun moveFolderIntoChild() {
        repo.createFolder("", "Parent")
        repo.createFolder("Parent", "Child")
        val result = repo.moveFolder("Parent", "Parent/Child")
        assertFalse(result)
    }

    @Test
    fun moveFolderEmptyPath() {
        val result = repo.moveFolder("", "somewhere")
        assertFalse(result)
    }

    // --- moveFiles ---

    @Test
    fun moveFilesBetweenFolders() {
        repo.createFolder("", "A")
        repo.createFolder("", "B")
        // Add a fake file entry to folder A
        val meta = repo.loadMetadata() as MutableList
        val file = VaultFileMetadata(name = "test.txt", relativePath = "A", mimeType = "text/plain")
        meta.add(file)
        // Force save by creating a new folder (triggers metadata save)
        repo.moveFiles(listOf(file), "B")
        val filesInB = repo.getFilesInFolder("B")
        assertTrue(filesInB.any { it.name == "test.txt" })
        val filesInA = repo.getFilesInFolder("A")
        assertFalse(filesInA.any { it.name == "test.txt" })
    }

    // --- renameFolder ---

    @Test
    fun renameFolderAtRoot() {
        repo.createFolder("", "OldName")
        repo.renameFolder("OldName", "NewName")
        val paths = repo.getAllFolderPaths()
        assertTrue(paths.contains("NewName"))
        assertFalse(paths.contains("OldName"))
    }

    @Test
    fun renameFolderUpdatesChildren() {
        repo.createFolder("", "Parent")
        repo.createFolder("Parent", "Child")
        repo.renameFolder("Parent", "Renamed")
        val paths = repo.getAllFolderPaths()
        assertTrue(paths.contains("Renamed"))
        assertTrue(paths.contains("Renamed/Child"))
        assertFalse(paths.contains("Parent"))
    }

    // --- renameFile ---

    @Test
    fun renameFile() {
        repo.createFolder("", "Docs")
        val meta = repo.loadMetadata() as MutableList
        val file = VaultFileMetadata(name = "old.txt", relativePath = "Docs", mimeType = "text/plain")
        meta.add(file)
        repo.renameFile(file, "new.txt")
        val files = repo.getFilesInFolder("Docs")
        assertTrue(files.any { it.name == "new.txt" })
        assertFalse(files.any { it.name == "old.txt" })
    }
}
