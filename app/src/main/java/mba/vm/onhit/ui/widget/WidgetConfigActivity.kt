package mba.vm.onhit.ui.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.scale
import androidx.core.net.toUri
import mba.vm.onhit.R
import mba.vm.onhit.hook.core.tag.TagType
import mba.vm.onhit.ui.config.ConfigManager
import mba.vm.onhit.utils.FileUtils
import java.io.File

/**
 * Configuration shown while a widget is added, or when a widget is reconfigured. This Activity is
 * not part of the click path. It deliberately does not auto-launch the document picker, so an
 * Activity recreation can never start a second picker; the user taps the file row instead.
 */
class WidgetConfigActivity : Activity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var pickedUri: Uri? = null
    private var pickedType: TagType? = null
    private var existingIconPath: String? = null
    private var newIconPath: String? = null

    private lateinit var fileText: TextView
    private lateinit var nameInput: EditText
    private lateinit var iconPreview: ImageView
    private lateinit var okButton: Button

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

        setContentView(R.layout.activity_widget_config)
        configureWindow()
        bindViews()

        val existing = WidgetConfigStore.load(this, appWidgetId)
        existingIconPath = existing?.iconPath
        val unavailable = intent?.getBooleanExtra(EXTRA_RECONFIGURE_UNAVAILABLE, false) == true
        if (savedInstanceState != null) {
            pickedUri = savedInstanceState.getString(STATE_URI)?.toUri()
            val savedType = savedInstanceState.getInt(STATE_TYPE, -1)
            pickedType = if (savedType == -1) null else TagType.fromByte(savedType.toByte())
            newIconPath = savedInstanceState.getString(STATE_ICON)
        } else if (!unavailable) {
            pickedUri = existing?.uri
            pickedType = existing?.tagType
        }

        findViewById<TextView>(R.id.tv_widget_title).text = getString(
            if (existing != null) R.string.dialog_title_edit_widget else R.string.dialog_title_create_widget
        )
        nameInput.setText(existing?.name.orEmpty())
        updateUi()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_URI, pickedUri?.toString())
        outState.putInt(STATE_TYPE, pickedType?.value?.toInt() ?: -1)
        outState.putString(STATE_ICON, newIconPath)
    }

    override fun onDestroy() {
        // Keep the temporary icon when the Activity is recreated; delete it on cancel/finish.
        if (isFinishing && !isChangingConfigurations) {
            newIconPath?.let { File(it).delete() }
        }
        super.onDestroy()
    }

    private fun configureWindow() {
        window.setGravity(Gravity.BOTTOM)
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        window.setBackgroundDrawableResource(android.R.color.transparent)
    }

    private fun bindViews() {
        fileText = findViewById(R.id.tv_widget_file)
        nameInput = findViewById(R.id.et_widget_name)
        iconPreview = findViewById(R.id.iv_widget_icon)
        okButton = findViewById(R.id.btn_ok)

        findViewById<android.view.View>(R.id.row_pick_file).setOnClickListener { pickTagFile() }
        iconPreview.setOnClickListener { pickIcon() }
        findViewById<Button>(R.id.btn_cancel).setOnClickListener { finish() }
        okButton.setOnClickListener { confirm() }
    }

    private fun updateUi() {
        val uri = pickedUri
        val type = pickedType
        if (uri != null && type != null) {
            val fileName = runCatching { FileUtils.getFileName(this, uri) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: uri.lastPathSegment.orEmpty()
            fileText.text = getString(R.string.widget_file_summary, fileName, type.name)
        } else {
            fileText.text = getString(R.string.widget_select_file)
        }
        okButton.isEnabled = uri != null && type != null

        val preview = (newIconPath ?: existingIconPath)?.let { path ->
            runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
        }
        if (preview != null) {
            iconPreview.setImageBitmap(preview)
        } else {
            iconPreview.setImageResource(R.mipmap.ic_launcher)
        }
    }

    private fun pickTagFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            ConfigManager.getRootUri(this@WidgetConfigActivity)?.let { root ->
                runCatching {
                    if (DocumentsContract.isTreeUri(root)) {
                        val documentId = DocumentsContract.getTreeDocumentId(root)
                        putExtra(
                            DocumentsContract.EXTRA_INITIAL_URI,
                            DocumentsContract.buildDocumentUriUsingTree(root, documentId)
                        )
                    }
                }
            }
        }
        startActivityForResult(intent, REQUEST_PICK_TAG_FILE)
    }

    private fun pickIcon() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        startActivityForResult(intent, REQUEST_PICK_ICON)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_PICK_TAG_FILE -> {
                val uri = data?.data
                if (resultCode != RESULT_OK || uri == null) return
                val bytes = FileUtils.readBytesLimited(this, uri)
                val tagType = FileUtils.detectTagTypeOrNull(bytes)
                if (tagType == null) {
                    Toast.makeText(this, R.string.toast_unsupported_tag_file, Toast.LENGTH_SHORT).show()
                    return
                }
                // Persist read access across reboots; the widget has no Activity to re-request it.
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {
                }
                pickedUri = uri
                pickedType = tagType
                if (nameInput.text.isBlank()) {
                    nameInput.setText(
                        runCatching { FileUtils.getFileName(this, uri) }.getOrNull().orEmpty()
                    )
                }
                updateUi()
            }

            REQUEST_PICK_ICON -> {
                val uri = data?.data
                if (resultCode != RESULT_OK || uri == null) return
                val bitmap = decodeIcon(uri)
                val tempPath = bitmap?.let { saveTempIcon(it) }
                if (tempPath == null) {
                    Toast.makeText(this, R.string.toast_icon_load_failed, Toast.LENGTH_SHORT).show()
                    return
                }
                newIconPath = tempPath
                updateUi()
            }
        }
    }

    private fun confirm() {
        val uri = pickedUri ?: return
        val tagType = pickedType ?: return
        val name = nameInput.text.toString().trim()
        if (name.isBlank()) {
            Toast.makeText(this, R.string.toast_widget_name_required, Toast.LENGTH_SHORT).show()
            return
        }

        persistNewIcon()
        WidgetConfigStore.save(this, appWidgetId, uri, tagType, name)
        TagWidgetProvider.updateWidget(this, appWidgetId)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        finish()
    }

    private fun persistNewIcon() {
        val temp = newIconPath ?: return
        val target = WidgetConfigStore.iconFile(this, appWidgetId)
        if (runCatching { File(temp).copyTo(target, overwrite = true) }.isSuccess) {
            File(temp).delete()
            newIconPath = null
        } else {
            Log.w(TAG, "failed to persist widget icon")
        }
    }

    private fun saveTempIcon(bitmap: Bitmap): String? = runCatching {
        val file = File(cacheDir, "widget_icon_tmp_$appWidgetId.png")
        val written = file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (written) file.absolutePath else null
    }.getOrNull()

    private fun decodeIcon(uri: Uri): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            null
        } else {
            val options = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, ICON_SIZE)
            }
            contentResolver.openInputStream(uri)
                ?.use { BitmapFactory.decodeStream(it, null, options) }
                ?.let(::squareScaled)
        }
    } catch (_: OutOfMemoryError) {
        null
    } catch (_: Exception) {
        null
    }

    private fun squareScaled(source: Bitmap): Bitmap {
        val side = minOf(source.width, source.height)
        val square = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
        if (square !== source) source.recycle()
        val scaled = square.scale(ICON_SIZE, ICON_SIZE)
        if (scaled !== square) square.recycle()
        return scaled
    }

    private fun calculateInSampleSize(width: Int, height: Int, target: Int): Int {
        var sampleSize = 1
        while (minOf(width, height) / (sampleSize * 2) >= target) {
            sampleSize *= 2
        }
        // Extreme aspect ratios (panoramas) can still decode into a huge bitmap; cap the long
        // side as well so a pathological image cannot exhaust the heap before cropping.
        while (maxOf(width, height) / sampleSize > MAX_DECODE_DIMENSION) {
            sampleSize *= 2
        }
        return sampleSize
    }

    companion object {
        const val EXTRA_RECONFIGURE_UNAVAILABLE =
            "mba.vm.onhit.extra.WIDGET_RECONFIGURE_UNAVAILABLE"

        private const val TAG = "onHitWidget"
        private const val REQUEST_PICK_TAG_FILE = 1001
        private const val REQUEST_PICK_ICON = 1002
        private const val ICON_SIZE = 192
        private const val MAX_DECODE_DIMENSION = 2048

        private const val STATE_URI = "widget_state_uri"
        private const val STATE_TYPE = "widget_state_type"
        private const val STATE_ICON = "widget_state_icon"
    }
}
