package mba.vm.onhit.ui.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import mba.vm.onhit.R
import mba.vm.onhit.ui.manager.TagEmulatorManager
import mba.vm.onhit.utils.FileUtils

/**
 * Emulates the tag configured for the clicked widget. The document is read on a background
 * thread because ACTION_OPEN_DOCUMENT may point at a cloud-backed provider; goAsync() keeps the
 * process alive until the PendingResult is finished.
 */
class WidgetClickReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        Thread {
            val config = WidgetConfigStore.load(appContext, appWidgetId)
            val bytes = config?.let { FileUtils.readBytesLimited(appContext, it.uri) }
            mainHandler.post {
                try {
                    if (config == null || bytes == null) {
                        Log.w(TAG, "widget $appWidgetId: tag file unavailable")
                        TagWidgetProvider.updateWidget(appContext, appWidgetId, unavailable = true)
                        Toast.makeText(appContext, R.string.widget_unavailable, Toast.LENGTH_LONG).show()
                    } else {
                        Log.i(TAG, "widget $appWidgetId: ${bytes.size} bytes, type=${config.tagType}")
                        TagEmulatorManager(appContext).sendEmulateBroadcast(bytes, config.tagType)
                        Log.i(TAG, "widget $appWidgetId: emulation broadcast sent")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "widget $appWidgetId: failed to emulate tag", e)
                    Toast.makeText(
                        appContext,
                        appContext.getString(R.string.toast_send_broadcast_failed, e.message),
                        Toast.LENGTH_SHORT
                    ).show()
                } finally {
                    pendingResult.finish()
                }
            }
        }.start()
    }

    private companion object {
        const val TAG = "onHitWidget"
        val mainHandler = Handler(Looper.getMainLooper())
    }
}
