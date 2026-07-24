package mba.vm.onhit.ui.broadcast

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import mba.vm.onhit.BuildConfig
import mba.vm.onhit.Constant
import mba.vm.onhit.R
import java.io.File

class ResponseBroadcastReceiver : BroadcastReceiver() {
    var onStateReceived: ((String?) -> Unit)? = null

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Constant.BROADCAST_TAG_RECORDER_STATE_RESPONSE -> {
                val state = intent.getStringExtra("state")
                onStateReceived?.invoke(state)
            }

            Constant.BROADCAST_TAG_RECORDER_RESPONSE -> {
                intent.getByteArrayExtra("data")?.let { bytes ->
                    Toast.makeText(
                        context,
                        context.getString(R.string.toast_tag_trace_received, bytes.size),
                        Toast.LENGTH_SHORT
                    ).show()
                    val fileName =
                        "tag_trace.ohtt"
                    val tempFile = File(context.filesDir, fileName).apply {
                        writeBytes(bytes)
                    }
                    val contentUri = FileProvider.getUriForFile(
                        context,
                        "${BuildConfig.APPLICATION_ID}.fileprovider",
                        tempFile
                    )
                    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                        component = ComponentName(
                            context,
                            "${context.packageName}.ImportHandler"
                        )
                        setDataAndType(contentUri, "application/octet-stream")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        putExtra("is_internal", true)

                        if (context !is Activity) {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    }
                    context.startActivity(viewIntent)
                }
            }
        }
    }
}