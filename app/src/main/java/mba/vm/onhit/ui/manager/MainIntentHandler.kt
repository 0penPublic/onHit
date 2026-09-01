package mba.vm.onhit.ui.manager

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.IntentCompat
import mba.vm.onhit.R
import mba.vm.onhit.ui.controller.ImportController
import mba.vm.onhit.utils.FileUtils
import kotlin.system.exitProcess

class MainIntentHandler(
    private val activity: Activity,
    private val importController: ImportController,
    private val tagEmulatorManager: TagEmulatorManager
) {

    fun handleIntent(intent: Intent?) {
        val className = intent?.component?.className ?: return
        val appId = activity.packageName
        when (className) {
            "$appId.ImportHandler" -> {
                val isInternal = intent.getBooleanExtra("is_internal", false)
                val uri = if (intent.action == Intent.ACTION_SEND) {
                    IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    intent.data
                }
                uri?.let { importController.importFile(it, isInternal) }
            }
            "$appId.BroadcastHandler" -> {
                val uri = if (intent.action == Intent.ACTION_SEND) {
                    IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    intent.data
                }
                uri?.let { handleBroadcastIntent(it) }
            }
        }
    }

    private fun handleBroadcastIntent(uri: Uri) {
        try {
            val inputStream = activity.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Toast.makeText(activity, R.string.toast_file_not_found, Toast.LENGTH_SHORT).show()
            } else {
                inputStream.use { input ->
                    val bytes = input.readBytes()
                    val detectedType = FileUtils.detectTagType(bytes)
                    tagEmulatorManager.sendEmulateBroadcast(bytes, detectedType)
                }
            }
        } catch (_: java.io.FileNotFoundException) {
            Toast.makeText(activity, R.string.toast_file_not_found, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(activity, activity.getString(R.string.toast_send_broadcast_failed, e.message), Toast.LENGTH_SHORT).show()
        } finally {
            activity.finishAndRemoveTask()
            exitProcess(0)
        }
    }
}