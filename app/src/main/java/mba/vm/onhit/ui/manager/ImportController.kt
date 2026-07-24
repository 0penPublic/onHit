package mba.vm.onhit.ui.manager

import android.app.Activity
import android.net.Uri
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import mba.vm.onhit.R
import mba.vm.onhit.databinding.ActivityMainBinding
import mba.vm.onhit.helper.DialogHelper
import mba.vm.onhit.utils.FileUtils
import java.util.Locale

class ImportController(
    private val activity: Activity,
    private val binding: ActivityMainBinding,
    private val onReset: () -> Unit
) {
    private var pendingImportUri: Uri? = null
    private var isInternalImport = false

    fun isImportMode() = pendingImportUri != null

    fun importFile(uri: Uri, isInternal: Boolean = false) {
        pendingImportUri = uri
        isInternalImport = isInternal
        val fileName = FileUtils.getFileName(activity, uri) ?: String.format(Locale.getDefault(), "imported_%d.ndef", System.currentTimeMillis())
        binding.tvAppTitle.text = activity.getString(R.string.title_save_to, fileName)
        binding.fabSettings.setImageResource(R.drawable.baseline_save_24)
        binding.btnSearch.visibility = android.view.View.GONE
    }

    fun performImportSave(currentDir: DocumentFile?) {
        val uri = pendingImportUri ?: return
        if (currentDir == null) {
            Toast.makeText(activity, R.string.path_not_selected, Toast.LENGTH_SHORT).show()
            return
        }
        val fileName = FileUtils.getFileName(activity, uri) ?: String.format(Locale.getDefault(), "imported_%d.ndef", System.currentTimeMillis())
        try {
            activity.contentResolver.openInputStream(uri)?.use { input ->
                val data = input.readBytes()
                DialogHelper.showInputBottomSheet(activity, activity.getString(R.string.dialog_title_save_ndef), fileName) { name ->
                    val file = currentDir.createFile("application/octet-stream", name)
                    file?.uri?.let { destUri ->
                        activity.contentResolver.openOutputStream(destUri)?.use { it.write(data) }
                    }
                    Toast.makeText(activity, activity.getString(R.string.toast_import_success, fileName), Toast.LENGTH_SHORT).show()
                    
                    if (isInternalImport) {
                        resetImportMode()
                    } else {
                        activity.finishAndRemoveTask()
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(activity, activity.getString(R.string.toast_import_failed, e.message), Toast.LENGTH_SHORT).show()
            if (!isInternalImport) activity.finishAndRemoveTask() else resetImportMode()
        }
    }

    fun resetImportMode() {
        pendingImportUri = null
        isInternalImport = false
        binding.tvAppTitle.text = activity.getString(R.string.app_name)
        binding.fabSettings.setImageResource(R.drawable.baseline_settings_24)
        binding.btnSearch.visibility = android.view.View.VISIBLE
        onReset()
    }
}
