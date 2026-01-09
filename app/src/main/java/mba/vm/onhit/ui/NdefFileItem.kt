package mba.vm.onhit.ui

import android.net.Uri

data class NdefFileItem(
    val name: String,
    val uri: Uri,
    val size: Long,
    val lastModified: Long,
    val isDirectory: Boolean
)