package mba.vm.onhit.ui.widget

import android.app.Activity
import android.app.Dialog
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.ImageView
import android.widget.Toast
import androidx.core.graphics.scale
import androidx.core.net.toUri
import mba.vm.onhit.R
import mba.vm.onhit.hook.core.tag.TagType
import mba.vm.onhit.ui.config.ConfigManager
import mba.vm.onhit.ui.dialog.DialogHelper
import mba.vm.onhit.utils.FileUtils
import java.io.File

/**
 * One-time configuration shown while the widget is being added: pick the tag file, then set the
 * display name and (optionally) a custom icon, exactly like the desktop-shortcut flow.
 *
 * This is part of the "add widget" flow; the click path (WidgetClickReceiver) never starts an
 * Activity.
 */
class WidgetConfigActivity : Activity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var pickedUri: String? = null
    private var pickedType = TagType.NDEF
    private var iconBitmap: Bitmap? = null
    private var shortcutDialog: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        pickFile()
    }

    private fun pickFile() {
        val pick = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            ConfigManager.getRootUri(this@WidgetConfigActivity)?.let {
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, it)
            }
        }
        startActivityForResult(pick, REQUEST_PICK_FILE)
    }

    private fun pickIcon() {
        val pick = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        startActivityForResult(pick, REQUEST_PICK_ICON)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_PICK_FILE -> {
                val uri = data?.data
                if (resultCode != RESULT_OK || uri == null) {
                    finish()
                    return
                }
                // Keep read access across reboots so the widget works without re-picking the file.
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {
                }
                val tagType = try {
                    contentResolver.openInputStream(uri)?.use {
                        val bytes = it.readBytes()
                        when {
                            FileUtils.isTraceFile(bytes) -> TagType.TRACE
                            FileUtils.isMfcData(bytes) -> TagType.MFC
                            FileUtils.isNdefFile(bytes) -> TagType.NDEF
                            else -> null
                        }
                    }
                } catch (_: Exception) {
                    null
                }
                if (tagType == null) {
                    Toast.makeText(this, R.string.toast_file_not_found, Toast.LENGTH_SHORT).show()
                    finish()
                    return
                }
                pickedUri = uri.toString()
                pickedType = tagType
                val defaultName = uri.lastPathSegment?.substringAfterLast('/') ?: ""
                shortcutDialog = DialogHelper.showShortcutBottomSheet(
                    this,
                    defaultName,
                    title = getString(R.string.dialog_title_create_widget),
                    nameHint = getString(R.string.hint_widget_name),
                    onChangeIcon = { pickIcon() },
                    onConfirm = { name -> confirm(name) }
                ).also { dialog ->
                    dialog.setOnDismissListener { if (!isFinishing) finish() }
                }
            }

            REQUEST_PICK_ICON -> {
                val uri = data?.data
                if (resultCode == RESULT_OK && uri != null) {
                    iconBitmap = try {
                        contentResolver.openInputStream(uri)?.use {
                            BitmapFactory.decodeStream(it)?.scale(192, 192)
                        }
                    } catch (_: Exception) {
                        null
                    }
                    shortcutDialog?.findViewById<ImageView>(R.id.iv_shortcut_icon)?.setImageBitmap(iconBitmap)
                }
            }
        }
    }

    private fun confirm(name: String) {
        val uri = pickedUri ?: return
        val iconPath = iconBitmap?.let { saveIcon(it) }
        TagWidgetProvider.saveConfig(this, appWidgetId, uri.toUri(), pickedType, name, iconPath)
        TagWidgetProvider.updateWidget(this, appWidgetId)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        finish()
    }

    private fun saveIcon(bitmap: Bitmap): String? = try {
        val file = File(filesDir, "widget_icon_$appWidgetId.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        file.absolutePath
    } catch (_: Exception) {
        null
    }

    companion object {
        private const val REQUEST_PICK_FILE = 1001
        private const val REQUEST_PICK_ICON = 1002
    }
}
