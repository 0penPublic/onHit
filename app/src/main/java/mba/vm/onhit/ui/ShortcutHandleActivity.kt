package mba.vm.onhit.ui

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.core.net.toUri
import mba.vm.onhit.R
import mba.vm.onhit.hook.core.tag.TagType

class ShortcutHandleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uriStr = intent.getStringExtra("file_uri")
        val tagTypeValue = intent.getIntExtra("tag_type", -1)

        if (uriStr != null && tagTypeValue != -1) {
            val uri = uriStr.toUri()
            try {
                val inputStream = contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    Toast.makeText(this, R.string.toast_file_not_found, Toast.LENGTH_SHORT).show()
                } else {
                    inputStream.use { input ->
                        val bytes = input.readBytes()
                        val tagType = TagType.fromByte(tagTypeValue.toByte())
                        mba.vm.onhit.ui.manager.TagEmulatorManager(this).sendEmulateBroadcast(bytes, tagType)
                    }
                }
            } catch (_: java.io.FileNotFoundException) {
                Toast.makeText(this, R.string.toast_file_not_found, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.toast_send_broadcast_failed, e.message), Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, R.string.toast_file_not_found, Toast.LENGTH_SHORT).show()
        }
        finishAndRemoveTask()
    }
}
