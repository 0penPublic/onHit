package mba.vm.onhit.core

import android.content.Context
import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import androidx.documentfile.provider.DocumentFile
import mba.vm.onhit.R
import mba.vm.onhit.ui.NdefFileItem

class NdefFileManager(private val context: Context, private val settingsManager: SettingsManager) {

    fun getDirectory(currentPath: String): DocumentFile? {
        val rootUri = settingsManager.chosenFolderUri ?: return null
        val root = DocumentFile.fromTreeUri(context, rootUri) ?: return null
        
        if (currentPath == "/" || currentPath.isEmpty()) return root
        
        var current: DocumentFile = root
        val parts = currentPath.trim('/').split("/").filter { it.isNotEmpty() }
        for (part in parts) {
            val next = current.findFile(part)
            if (next != null && next.isDirectory) {
                current = next
            } else {
                return null
            }
        }
        return current
    }

    fun listFiles(currentPath: String): List<NdefFileItem>? {
        val dir = getDirectory(currentPath) ?: return null
        if (!dir.exists() || !dir.isDirectory) return null

        val fileList = dir.listFiles()
            .sortedWith(compareBy<DocumentFile> { !it.isDirectory }.thenBy { it.name?.lowercase() ?: "" })

        if (fileList.isEmpty() && currentPath == "/") {
            createDefaultTestFile(dir)
            return listFiles(currentPath)
        }

        return buildList {
            if (currentPath != "/") {
                add(NdefFileItem("..", Uri.EMPTY, 0L, 0L, true))
            }
            fileList.forEach { file ->
                add(NdefFileItem(
                    name = file.name ?: context.getString(android.R.string.unknownName),
                    uri = file.uri,
                    size = if (file.isDirectory) 0L else file.length(),
                    lastModified = file.lastModified(),
                    isDirectory = file.isDirectory
                ))
            }
        }
    }

    private fun createDefaultTestFile(dir: DocumentFile) {
        val file = dir.createFile("application/octet-stream", "onHit_TestNDEF.ndef") ?: return
        val message = NdefMessage(arrayOf(NdefRecord.createTextRecord("en", context.getString(R.string.ndef_text_example))))
        try {
            context.contentResolver.openOutputStream(file.uri)?.use { 
                it.write(message.toByteArray()) 
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun saveNdefFile(currentPath: String, fileName: String, ndefData: ByteArray): Boolean {
        val dir = getDirectory(currentPath) ?: return false
        val file = dir.createFile("application/octet-stream", fileName) ?: return false
        return try {
            context.contentResolver.openOutputStream(file.uri)?.use { outputStream ->
                outputStream.write(ndefData)
                true
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    fun createDirectory(currentPath: String, dirName: String): Boolean {
        val parentDir = getDirectory(currentPath) ?: return false
        return parentDir.createDirectory(dirName) != null
    }

    fun rename(uri: Uri, newName: String): Boolean {
        val file = DocumentFile.fromSingleUri(context, uri) ?: DocumentFile.fromTreeUri(context, uri)
        return file?.renameTo(newName) ?: false
    }

    fun deleteFile(uri: Uri): Boolean {
        val file = DocumentFile.fromSingleUri(context, uri) ?: DocumentFile.fromTreeUri(context, uri)
        return file?.delete() ?: false
    }

    fun readNdefMessage(uri: Uri): NdefMessage? {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            bytes?.let { NdefMessage(it) }
        } catch (_: Exception) {
            null
        }
    }
}
