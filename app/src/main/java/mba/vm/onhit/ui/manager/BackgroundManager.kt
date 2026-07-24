package mba.vm.onhit.ui.manager

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.core.content.FileProvider
import mba.vm.onhit.Constant.Companion.REQUEST_CROP_BACKGROUND
import mba.vm.onhit.Constant.Companion.REQUEST_SELECT_BACKGROUND
import mba.vm.onhit.R
import mba.vm.onhit.databinding.ActivityMainBinding
import mba.vm.onhit.ui.config.ConfigManager
import java.io.File

class BackgroundManager(private val activity: Activity, private val binding: ActivityMainBinding) {

    private var pendingCroppedBackgroundUri: Uri? = null

    fun requestSelectBackground() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
        activity.startActivityForResult(intent, REQUEST_SELECT_BACKGROUND)
    }

    fun handleCropResult() {
        pendingCroppedBackgroundUri?.let { uri ->
            ConfigManager.setBackgroundUri(activity, uri)
            applyCustomBackground()
        }
    }

    fun startCropBackground(sourceUri: Uri) {
        val outputFile = File(activity.filesDir, "custom_background_cropped.jpg")

        if (!outputFile.exists()) {
            outputFile.createNewFile()
        }

        val outputUri = FileProvider.getUriForFile(
            activity,
            "${activity.packageName}.fileprovider",
            outputFile
        )

        pendingCroppedBackgroundUri = outputUri

        val cropIntent = Intent("com.android.camera.action.CROP").apply {
            setDataAndType(sourceUri, "image/*")

            putExtra("crop", "true")
            putExtra("aspectX", 9)
            putExtra("aspectY", 16)
            putExtra("scale", true)
            putExtra("return-data", false)

            putExtra(MediaStore.EXTRA_OUTPUT, outputUri)
            putExtra("outputFormat", Bitmap.CompressFormat.JPEG.toString())

            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

            clipData = ClipData.newRawUri(
                "cropped_background",
                outputUri
            )
        }

        val resInfoList = activity.packageManager.queryIntentActivities(
            cropIntent,
            android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
        )

        for (resolveInfo in resInfoList) {
            val packageName = resolveInfo.activityInfo.packageName

            activity.grantUriPermission(
                packageName,
                sourceUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )

            activity.grantUriPermission(
                packageName,
                outputUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }

        try {
            activity.startActivityForResult(cropIntent, REQUEST_CROP_BACKGROUND)
        } catch (_: Exception) {
            Toast.makeText(activity, R.string.unknown_error, Toast.LENGTH_SHORT).show()

            ConfigManager.setBackgroundUri(activity, sourceUri)
            applyCustomBackground()
        }
    }

    fun applyCustomBackground() {
        val uri = ConfigManager.getBackgroundUri(activity)

        if (uri == null) {
            binding.ivCustomBackground.setImageDrawable(null)
            binding.ivCustomBackground.visibility = View.GONE
            binding.vBackgroundScrim.visibility = View.GONE
            binding.topBar.setBackgroundColor(Color.TRANSPARENT)
            return
        }

        try {
            binding.ivCustomBackground.setImageDrawable(null)
            binding.ivCustomBackground.setImageURI(uri)
            binding.ivCustomBackground.visibility = View.VISIBLE
            binding.vBackgroundScrim.visibility = View.VISIBLE
            binding.topBar.setBackgroundColor(activity.getColor(R.color.custom_panel_background))
        } catch (_: Exception) {
            Toast.makeText(activity, R.string.unknown_error, Toast.LENGTH_SHORT).show()
            ConfigManager.setBackgroundUri(activity, null)
            applyCustomBackground()
        }
    }
}
