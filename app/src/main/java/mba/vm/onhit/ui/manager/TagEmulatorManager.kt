package mba.vm.onhit.ui.manager

import android.app.Activity
import android.content.Intent
import android.os.Parcel
import android.widget.Toast
import mba.vm.onhit.Constant
import mba.vm.onhit.R
import mba.vm.onhit.hook.core.tag.TagType
import mba.vm.onhit.ui.config.ConfigManager
import mba.vm.onhit.model.FileData
import mba.vm.onhit.model.FileType

class TagEmulatorManager(private val activity: Activity) {

    fun onFileClick(fileData: FileData, isImportMode: Boolean): Boolean {
        if (fileData.isParent || fileData.isDirectory) return false
        
        if (!isImportMode) {
            when (fileData.type) {
                FileType.NDEF -> simulateTag(fileData, TagType.NDEF)
                FileType.MifareClassic -> simulateTag(fileData, TagType.MFC)
                FileType.TagTrace -> simulateTag(fileData, TagType.TRACE)
                else -> Toast.makeText(activity, R.string.toast_not_ndef_file, Toast.LENGTH_SHORT).show()
            }
            return true
        }
        return false
    }

    private fun simulateTag(fileData: FileData, tagType: TagType) {
        val file = fileData.documentFile ?: return
        try {
            activity.contentResolver.openInputStream(file.uri)?.use { input ->
                val bytes = input.readBytes()
                sendEmulateBroadcast(bytes, tagType)
            }
        } catch (e: Exception) {
            Toast.makeText(activity, activity.getString(R.string.toast_send_broadcast_failed, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    fun sendEmulateBroadcast(data: ByteArray, tagType: TagType) {
        val uid = ConfigManager.genUid(activity)
        val intent = Intent(Constant.BROADCAST_TAG_EMULATOR_REQUEST).apply {
            if (uid != null) {
                putExtra("uid", uid)
            }
            putExtra("data", data)
            putExtra("tagType", tagType.value)
        }
        val parcel = Parcel.obtain()
        try {
            intent.writeToParcel(parcel, 0)
            if (parcel.dataSize() > Constant.MAX_OF_BROADCAST_SIZE) {
                Toast.makeText(activity, R.string.toast_file_too_large, Toast.LENGTH_SHORT).show()
                return
            }
        } finally {
            parcel.recycle()
        }
        activity.sendBroadcast(intent)
    }
}