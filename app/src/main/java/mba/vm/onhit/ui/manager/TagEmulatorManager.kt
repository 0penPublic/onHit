package mba.vm.onhit.ui.manager

import android.app.Activity
import android.widget.Toast
import mba.vm.onhit.R
import mba.vm.onhit.service.Service
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
                sendEmulateRequest(bytes, tagType)
            }
        } catch (e: Exception) {
            Toast.makeText(activity, activity.getString(R.string.toast_emulate_failed, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    fun sendEmulateRequest(data: ByteArray, tagType: TagType) {
        val uid = ConfigManager.genUid(activity)
        Service.emulateTag(uid, data, tagType.value)
    }
}