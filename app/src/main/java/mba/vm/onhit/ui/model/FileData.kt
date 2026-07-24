package mba.vm.onhit.ui.model

import androidx.documentfile.provider.DocumentFile

data class FileData(
    val name: String,
    val isDirectory: Boolean,
    val documentFile: DocumentFile?,
    val isParent: Boolean = false,
    val size: Long = 0,
    val lastModified: Long = 0,
    val isNdef: Boolean = false,
    val isMfcData: Boolean = false,
    val isTraceFile: Boolean = false
)
