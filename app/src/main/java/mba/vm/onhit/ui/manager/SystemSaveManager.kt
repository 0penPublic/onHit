package mba.vm.onhit.ui.manager

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import mba.vm.onhit.R

class SystemSaveManager(private val activity: Activity) {
    private var pendingDataToSave: ByteArray? = null

    fun handleSaveResult(resultCode: Int, data: Intent?): Boolean {
        if (resultCode != Activity.RESULT_OK) {
            pendingDataToSave = null
            return false
        }
        
        val uri = data?.data ?: return false
        val bytes = pendingDataToSave ?: return false
        
        return try {
            activity.contentResolver.openOutputStream(uri, "rwt")?.use { output ->
                output.write(bytes)
            }
            Toast.makeText(activity, R.string.toast_save_success, Toast.LENGTH_SHORT).show()
            pendingDataToSave = null
            true
        } catch (e: Exception) {
            Toast.makeText(activity, activity.getString(R.string.toast_write_failed, e.message), Toast.LENGTH_SHORT).show()
            pendingDataToSave = null
            false
        }
    }
}