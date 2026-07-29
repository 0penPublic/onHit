package mba.vm.onhit.model

import androidx.documentfile.provider.DocumentFile

data class FileData(
    val name: String,
    val type: FileType,
    val documentFile: DocumentFile?,
    val isParent: Boolean = false,
    val size: Long = 0,
    val lastModified: Long = 0
) {
    val isDirectory: Boolean get() = type == FileType.Folder
}
