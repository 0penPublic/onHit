package mba.vm.onhit.ui.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import mba.vm.onhit.R
import mba.vm.onhit.ui.manager.TagEmulatorManager
import mba.vm.onhit.utils.LogUtils.logE
import mba.vm.onhit.utils.LogUtils.logI

/**
 * Widget click handler: sends the emulation broadcast only — never starts an Activity.
 *
 * If the saved document became unreadable (permission revoked, restored on another device), the
 * widget is switched to the reconfiguration PendingIntent and the user gets a toast; the next tap
 * opens [WidgetConfigActivity].
 */
class WidgetClickReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        logI("onReceive appWidgetId=$appWidgetId")
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

        val config = TagWidgetProvider.getConfig(context, appWidgetId)
        logI("config=$config")
        if (config == null) {
            markUnavailable(context, appWidgetId, "missing configuration")
            return
        }
        try {
            val input = context.contentResolver.openInputStream(config.uri)
            if (input == null) {
                markUnavailable(context, appWidgetId, "openInputStream returned null")
                return
            }
            input.use {
                val bytes = it.readBytes()
                logI("read ${bytes.size} bytes, sending emulation tagType=${config.type}")
                TagEmulatorManager(context).sendEmulateBroadcast(bytes, config.type)
                logI("emulation broadcast sent")
            }
        } catch (e: Exception) {
            markUnavailable(context, appWidgetId, e.message ?: e.javaClass.simpleName)
        }
    }

    private fun markUnavailable(context: Context, appWidgetId: Int, reason: String) {
        logE("document unavailable for widget $appWidgetId: $reason")
        // Rewrites the click PendingIntent to WidgetConfigActivity for the next tap.
        TagWidgetProvider.updateWidget(context, appWidgetId)
        Toast.makeText(context, R.string.widget_tag_unavailable, Toast.LENGTH_LONG).show()
    }
}
