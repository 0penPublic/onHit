package mba.vm.onhit.ui.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import mba.vm.onhit.ui.manager.TagEmulatorManager
import mba.vm.onhit.utils.LogUtils.logE
import mba.vm.onhit.utils.LogUtils.logI

/**
 * Widget click handler: sends the emulation broadcast only — never starts an Activity.
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
        if (config == null) return
        try {
            context.contentResolver.openInputStream(config.uri)?.use { input ->
                val bytes = input.readBytes()
                logI("read ${bytes.size} bytes, sending emulation tagType=${config.type}")
                TagEmulatorManager(context).sendEmulateBroadcast(bytes, config.type)
                logI("emulation broadcast sent")
            } ?: logE("openInputStream returned null for ${config.uri}")
        } catch (e: Exception) {
            logE("failed to emulate from ${config.uri}: ${e.message}", e)
        }
    }
}
