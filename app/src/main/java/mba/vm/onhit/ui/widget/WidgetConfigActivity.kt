package mba.vm.onhit.ui.widget

import android.app.Activity
import android.app.Dialog
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.core.graphics.scale
import androidx.core.net.toUri
import androidx.core.os.BundleCompat
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
 * Activity. The activity is also launched with [EXTRA_RECONFIGURE] when a widget's document is no
 * longer readable (e.g. restored on another device).
 *
 * The progress is kept in the instance state: rotation or process death restores the picked file,
 * tag type, name and icon, and rebuilds the sheet instead of starting a second picker.
 */
class WidgetConfigActivity : Activity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var pickedUri: String? = null
    private var pickedType = TagType.NDEF
    private var iconBitmap: Bitmap? = null
    private var shortcutDialog: Dialog? = null
    private var reconfigure = false
    private var existingName: String? = null
    private var existingIconPath: String? = null

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
        reconfigure = intent?.getBooleanExtra(EXTRA_RECONFIGURE, false) == true
        if (reconfigure) {
            TagWidgetProvider.getConfig(this, appWidgetId)?.let {
                existingName = it.name
                existingIconPath = it.iconPath
            }
        }
        if (savedInstanceState != null) {
            restoreState(savedInstanceState)
        } else {
            pickFile()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_URI, pickedUri)
        outState.putByte(STATE_TYPE, pickedType.value)
        outState.putParcelable(STATE_ICON, iconBitmap)
        val editedName = shortcutDialog
            ?.findViewById<EditText>(R.id.et_shortcut_name)
            ?.text?.toString()
        outState.putString(STATE_NAME, editedName ?: existingName)
    }

    override fun onDestroy() {
        // A dialog dismissed by a configuration change must not finish the recreated activity.
        shortcutDialog?.setOnDismissListener(null)
        super.onDestroy()
    }

    private fun restoreState(state: Bundle) {
        pickedUri = state.getString(STATE_URI)
        pickedType = TagType.fromByte(state.getByte(STATE_TYPE, TagType.NDEF.value))
        iconBitmap = BundleCompat.getParcelable(state, STATE_ICON, Bitmap::class.java)
        val name = state.getString(STATE_NAME)
        pickedUri?.let { showNameSheet(name ?: defaultSheetName(it)) }
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
                showNameSheet(defaultSheetName(pickedUri!!))
            }

            REQUEST_PICK_ICON -> {
                val uri = data?.data
                if (resultCode == RESULT_OK && uri != null) {
                    val decoded = decodeIcon(uri)
                    if (decoded != null) {
                        iconBitmap = decoded
                        shortcutDialog
                            ?.findViewById<ImageView>(R.id.iv_shortcut_icon)
                            ?.setImageBitmap(decoded)
                    } else {
                        Toast.makeText(this, R.string.toast_icon_load_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun showNameSheet(name: String) {
        shortcutDialog?.setOnDismissListener(null)
        shortcutDialog?.dismiss()
        shortcutDialog = DialogHelper.showShortcutBottomSheet(
            this,
            name,
            title = getString(R.string.dialog_title_create_widget),
            nameHint = getString(R.string.hint_widget_name),
            onChangeIcon = { pickIcon() },
            onConfirm = { newName -> confirm(newName) }
        ).also { dialog ->
            dialog.setOnDismissListener {
                if (!isFinishing && !isChangingConfigurations) finish()
            }
        }
        val preview = iconBitmap ?: existingIconPath?.let { path ->
            runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
        }
        preview?.let {
            shortcutDialog?.findViewById<ImageView>(R.id.iv_shortcut_icon)?.setImageBitmap(it)
        }
    }

    private fun defaultSheetName(uri: String): String =
        existingName ?: uri.toUri().lastPathSegment?.substringAfterLast('/') ?: ""

    /**
     * Decode with inSampleSize so a full-resolution camera photo is never allocated: the bounds
     * pass only reads the header, the second pass decodes at >= [ICON_SIZE] px.
     */
    private fun decodeIcon(uri: Uri): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            null
        } else {
            val options = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, ICON_SIZE)
            }
            contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }?.scale(ICON_SIZE, ICON_SIZE)
        }
    } catch (_: OutOfMemoryError) {
        null
    } catch (_: Exception) {
        null
    }

    private fun calculateInSampleSize(width: Int, height: Int, target: Int): Int {
        var sampleSize = 1
        while (width / (sampleSize * 2) >= target && height / (sampleSize * 2) >= target) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun confirm(name: String) {
        val uri = pickedUri ?: return
        val iconPath = iconBitmap?.let { saveIcon(it) }
            ?: existingIconPath?.takeIf { File(it).exists() }
        TagWidgetProvider.saveConfig(this, appWidgetId, uri.toUri(), pickedType, name, iconPath)
        TagWidgetProvider.updateWidget(this, appWidgetId)
        if (!reconfigure) {
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        }
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
        const val EXTRA_RECONFIGURE = "mba.vm.onhit.extra.WIDGET_RECONFIGURE"

        private const val REQUEST_PICK_FILE = 1001
        private const val REQUEST_PICK_ICON = 1002
        private const val ICON_SIZE = 192

        private const val STATE_URI = "widget_state_uri"
        private const val STATE_TYPE = "widget_state_type"
        private const val STATE_ICON = "widget_state_icon"
        private const val STATE_NAME = "widget_state_name"
    }
}
