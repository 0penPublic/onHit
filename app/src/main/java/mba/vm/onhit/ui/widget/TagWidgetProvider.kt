package mba.vm.onhit.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.RemoteViews
import androidx.core.net.toUri
import mba.vm.onhit.BuildConfig
import mba.vm.onhit.R

/**
 * 1x1 desktop entry for tag emulation. A click is a broadcast PendingIntent, never an Activity:
 * launching a transient Activity from the launcher makes HyperOS treat the following NFC
 * NfcRootActivity start as an app self-start and abort it, so only the NFC sound is played.
 */
class TagWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateWidget(context, it) }
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        WidgetConfigStore.migrate(context, oldWidgetIds, newWidgetIds)
        newWidgetIds.forEach { appWidgetId ->
            val config = WidgetConfigStore.load(context, appWidgetId)
            // URI grants are not part of a backup: a migrated widget whose document is no longer
            // granted cannot emulate until the user picks the file again.
            val needsConfiguration = config != null && !hasPersistedRead(context, config.uri)
            updateWidget(context, appWidgetId, unavailable = needsConfiguration)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetConfigStore.clear(context, it) }
    }

    companion object {
        const val ACTION_WIDGET_CLICK = "${BuildConfig.APPLICATION_ID}.action.WIDGET_EMULATE"

        private const val PENDING_INTENT_FLAGS =
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        fun updateWidget(context: Context, appWidgetId: Int, unavailable: Boolean = false) {
            val config = WidgetConfigStore.load(context, appWidgetId)
            val views = RemoteViews(context.packageName, R.layout.widget_tag)

            if (config != null) {
                views.setTextViewText(
                    R.id.widget_label,
                    config.name.ifBlank { context.getString(R.string.app_name) }
                )
                config.iconPath?.let { path ->
                    runCatching { BitmapFactory.decodeFile(path) }
                        .getOrNull()
                        ?.let { views.setImageViewBitmap(R.id.widget_icon, it) }
                }
            }

            val needsConfiguration = unavailable || config == null
            if (needsConfiguration) {
                views.setTextViewText(R.id.widget_label, context.getString(R.string.widget_unavailable_label))
            }
            views.setOnClickPendingIntent(
                R.id.widget_root,
                if (needsConfiguration) {
                    configurationIntent(context, appWidgetId)
                } else {
                    clickIntent(context, appWidgetId)
                }
            )
            AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views)
        }

        private fun clickIntent(context: Context, appWidgetId: Int): PendingIntent {
            val intent = Intent(context, WidgetClickReceiver::class.java).apply {
                action = ACTION_WIDGET_CLICK
                data = "onhit://widget/$appWidgetId".toUri()
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            return PendingIntent.getBroadcast(context, appWidgetId, intent, PENDING_INTENT_FLAGS)
        }

        private fun configurationIntent(context: Context, appWidgetId: Int): PendingIntent {
            val intent = Intent(context, WidgetConfigActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(WidgetConfigActivity.EXTRA_RECONFIGURE_UNAVAILABLE, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            return PendingIntent.getActivity(context, appWidgetId, intent, PENDING_INTENT_FLAGS)
        }

        private fun hasPersistedRead(context: Context, uri: Uri): Boolean =
            context.contentResolver.persistedUriPermissions.any {
                it.uri == uri && it.isReadPermission
            }
    }
}
