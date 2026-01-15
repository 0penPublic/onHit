package mba.vm.onhit.ui.model

import androidx.documentfile.provider.DocumentFile

data class FileData(
    val name: String,
    val isDirectory: Boolean,
    val documentFile: DocumentFile?,
    val isParent: Boolean = false,
    val size: Long = 0,
    val lastModified: Long = 0,
    val isNdef: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FileData) return false
        return name == other.name && 
               isDirectory == other.isDirectory && 
               size == other.size && 
               lastModified == other.lastModified &&
               isParent == other.isParent &&
               isNdef == other.isNdef
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + isDirectory.hashCode()
        result = 31 * result + size.hashCode()
        result = 31 * result + lastModified.hashCode()
        result = 31 * result + isParent.hashCode()
        result = 31 * result + isNdef.hashCode()
        return result
    }
}
