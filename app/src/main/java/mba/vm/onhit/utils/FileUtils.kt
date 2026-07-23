package mba.vm.onhit.utils

import android.content.Context
import android.net.Uri
import android.nfc.NdefMessage
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import mba.vm.onhit.Constant.Companion.MAX_OF_BROADCAST_SIZE
import mba.vm.onhit.R
import mba.vm.onhit.core.recorder.trace.TagTraceCodec
import mba.vm.onhit.ui.model.FileData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileUtils {
    fun getFileDataList(context: Context, dir: DocumentFile, rootDir: DocumentFile?): List<FileData> {
        val list = mutableListOf<FileData>()
        
        if (rootDir != null && dir.uri != rootDir.uri) {
            list.add(FileData("..", true, dir.parentFile, isParent = true))
        }
        val items = dir.listFiles().map { file ->
            val isDir = file.isDirectory
            val bytes = if (!isDir) readBytesFromDocumentFile(context, file) else null
            FileData(
                name = file.name ?: "Unknown",
                isDirectory = isDir,
                documentFile = file,
                size = if (isDir) 0 else file.length(),
                lastModified = file.lastModified(),
                isNdef = !isDir && isNdefFile(bytes),
                isMfcData = !isDir && isMfcData(bytes),
                isTraceFile = !isDir && isTraceFile(bytes)
            )
        }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))

        list.addAll(items)
        return list
    }


    fun isTraceFile(bytes: ByteArray?): Boolean {
        val len = bytes?.size ?: return false
        if (len < 6) return false
        val first6 = bytes.copyOfRange(0, 6)
        return first6.contentEquals(TagTraceCodec.MAGIC_HEADER)
    }

    fun isMfcData(bytes: ByteArray?): Boolean {
        val len = bytes?.size ?: return false
        if (len != 1024 && len != 4096 && len != 320) return false
        val buffer = bytes.copyOfRange(48, 64)
        if (buffer.all { it == 0.toByte() }) return false
        return !(buffer[6] == 0.toByte() && buffer[7] == 0.toByte() && buffer[8] == 0.toByte())
    }

    fun isNdefFile(bytes: ByteArray?): Boolean {
        bytes?.let {
            if (bytes.size > MAX_OF_BROADCAST_SIZE) return false
            return try {
                NdefMessage(bytes)
                true
            } catch (_: Exception) {
                false
            }
        }
        return false
    }
    private fun readBytesFromDocumentFile(context: Context, file: DocumentFile): ByteArray? {
        return try {
            context.contentResolver.openInputStream(file.uri)?.use { input ->
                input.readBytes()
            }
        } catch (_: Exception) {
            null
        }
    }

    fun formatDetails(context: Context, item: FileData): String {
        if (item.isParent) return context.getString(R.string.label_parent_directory)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val date = dateFormat.format(Date(item.lastModified))
        return if (item.isDirectory) {
            "${context.getString(R.string.label_folder)} | $date"
        } else {
            val type = if (item.isNdef) "NDEF " else ""
            "${type}${item.size} ${context.getString(R.string.label_bytes)} | $date"
        }
    }

    fun getSimplifiedPath(context: Context, rootDir: DocumentFile?, currentDir: DocumentFile?): String {
        if (rootDir == null || currentDir == null) return context.getString(R.string.path_not_selected)
        
        val rootUri = rootDir.uri
        val currentUri = currentDir.uri

        val rootPath = Uri.decode(rootUri.toString())
        val currentPath = Uri.decode(currentUri.toString())
        
        if (rootPath == currentPath) return "/"
        
        return try {
            val relative = currentPath.substringAfter(rootPath).trim('/')
            "/$relative"
        } catch (_: Exception) {
            "/"
        }
    }

    fun findDirectoryByPath(rootDir: DocumentFile?, path: String): DocumentFile? {
        val root = rootDir ?: return null
        
        val relativePath = path.trim('/')
        if (relativePath.isEmpty()) return root
        
        val segments = relativePath.split('/').filter { it.isNotEmpty() }
        var current: DocumentFile = root
        
        for (segment in segments) {
            val next = current.findFile(segment)
            if (next != null && next.isDirectory) {
                current = next
            } else {
                return null
            }
        }
        return current
    }

    fun getFileName(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) result = cursor.getString(index)
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }
}
