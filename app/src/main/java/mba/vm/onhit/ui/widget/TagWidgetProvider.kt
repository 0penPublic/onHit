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
import mba.vm.onhit.R
import mba.vm.onhit.hook.core.tag.TagType
import mba.vm.onhit.utils.LogUtils.logI
import java.io.File

/**
 * Desktop entry for tag emulation that does NOT start an Activity on click.
 *
 * Shortcuts are Activity intents by platform design; launching a transient activity from the
 * launcher makes HyperOS treat the following NFC chain (com.android.nfc/.NfcRootActivity) as an
 * app self-start during the HOME->HOME transition and abort it, so only the NFC sound is played.
 * A widget click is delivered as a broadcast PendingIntent from the host, so the NFC chain is
 * started from a clean context and the target app launches normally.
 *
 * The only exception is a widget whose document is no longer readable (restored to another
 * device, revoked permission): its click opens [WidgetConfigActivity] so the file can be picked
 * again. That path never emulates a tag, so it cannot hit the self-start guard.
 */
class TagWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateWidget(context, it) }
    }

    /**
     * Widget IDs are not stable across backup/restore: the launcher assigns new ones, so the
     * ID-keyed preferences and icon files must be copied over before the restored widgets read
     * them. Missing document access is handled by [updateWidget] (click opens reconfiguration).
     */
    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        oldWidgetIds.forEachIndexed { index, oldWidgetId ->
            val newWidgetId = newWidgetIds.getOrNull(index) ?: return@forEachIndexed
            if (oldWidgetId == newWidgetId) return@forEachIndexed
            val uri = prefs.getString(KEY_URI + oldWidgetId, null) ?: return@forEachIndexed
            editor.putString(KEY_URI + newWidgetId, uri)
                .putInt(KEY_TYPE + newWidgetId, prefs.getInt(KEY_TYPE + oldWidgetId, TagType.NDEF.value.toInt()))
                .putString(KEY_NAME + newWidgetId, prefs.getString(KEY_NAME + oldWidgetId, "") ?: "")
                .putString(
                    KEY_ICON + newWidgetId,
                    prefs.getString(KEY_ICON + oldWidgetId, null)
                        ?.let { migrateIcon(context, it, newWidgetId) }
                )
                .remove(KEY_URI + oldWidgetId)
                .remove(KEY_TYPE + oldWidgetId)
                .remove(KEY_NAME + oldWidgetId)
                .remove(KEY_ICON + oldWidgetId)
        }
        editor.apply()
        newWidgetIds.forEach { updateWidget(context, it) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { clearConfig(context, it) }
    }

    data class WidgetConfig(val uri: Uri, val type: TagType, val name: String, val iconPath: String?)

    companion object {
        const val ACTION_WIDGET_CLICK = "mba.vm.onhit.action.WIDGET_EMULATE"
        private const val PREFS = "onhit_widgets"
        private const val KEY_URI = "uri_"
        private const val KEY_TYPE = "type_"
        private const val KEY_NAME = "name_"
        private const val KEY_ICON = "icon_"

        fun updateWidget(context: Context, appWidgetId: Int) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val config = getConfig(context, appWidgetId)
            val readable = config != null && canRead(context, config.uri)
            logI("updateWidget id=$appWidgetId config=$config readable=$readable")
            val views = RemoteViews(context.packageName, R.layout.widget_tag)
            if (config != null) {
                views.setTextViewText(R.id.widget_label, config.name)
                val icon = config.iconPath
                    ?.let { path -> runCatching { BitmapFactory.decodeFile(path) }.getOrNull() }
                if (icon != null) {
                    views.setImageViewBitmap(R.id.widget_icon, icon)
                }
            }
            val needsConfigure = !readable
            if (needsConfigure) {
                views.setTextViewText(R.id.widget_label, context.getString(R.string.widget_tag_unavailable))
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val pendingIntent = if (needsConfigure) {
                // Recovery path: the saved document cannot be read, let the user pick it again.
                PendingIntent.getActivity(
                    context,
                    appWidgetId,
                    Intent(context, WidgetConfigActivity::class.java).apply {
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        putExtra(WidgetConfigActivity.EXTRA_RECONFIGURE, true)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    },
                    flags
                )
            } else {
                val clickIntent = Intent(context, WidgetClickReceiver::class.java).apply {
                    action = ACTION_WIDGET_CLICK
                    data = "onhit://widget/$appWidgetId".toUri()
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                }
                PendingIntent.getBroadcast(context, appWidgetId, clickIntent, flags)
            }
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            manager.updateAppWidget(appWidgetId, views)
        }

        fun saveConfig(
            context: Context,
            appWidgetId: Int,
            uri: Uri,
            type: TagType,
            name: String,
            iconPath: String?
        ) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_URI + appWidgetId, uri.toString())
                .putInt(KEY_TYPE + appWidgetId, type.value.toInt())
                .putString(KEY_NAME + appWidgetId, name)
                .putString(KEY_ICON + appWidgetId, iconPath)
                .apply()
        }

        fun getConfig(context: Context, appWidgetId: Int): WidgetConfig? {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val uri = prefs.getString(KEY_URI + appWidgetId, null) ?: return null
            val type = TagType.fromByte(prefs.getInt(KEY_TYPE + appWidgetId, TagType.NDEF.value.toInt()).toByte())
            val name = prefs.getString(KEY_NAME + appWidgetId, "") ?: ""
            val iconPath = prefs.getString(KEY_ICON + appWidgetId, null)
            return WidgetConfig(uri.toUri(), type, name, iconPath)
        }

        fun clearConfig(context: Context, appWidgetId: Int) {
            getConfig(context, appWidgetId)?.iconPath?.let { runCatching { File(it).delete() } }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove(KEY_URI + appWidgetId)
                .remove(KEY_TYPE + appWidgetId)
                .remove(KEY_NAME + appWidgetId)
                .remove(KEY_ICON + appWidgetId)
                .apply()
        }

        fun canRead(context: Context, uri: Uri): Boolean = runCatching {
            context.contentResolver.openInputStream(uri)?.use { }
        }.getOrNull() != null

        /**
         * Icons live in filesDir as widget_icon_<id>.png; rename/copy them to the restored ID so
         * the provider's iconPath stays valid. Failure only loses the custom icon.
         */
        private fun migrateIcon(context: Context, oldPath: String, newWidgetId: Int): String? {
            val source = File(oldPath)
            if (!source.exists()) return null
            val target = File(context.filesDir, "widget_icon_$newWidgetId.png")
            if (source.absolutePath == target.absolutePath) return oldPath
            return runCatching {
                source.copyTo(target, overwrite = true)
                source.delete()
                target.absolutePath
            }.getOrNull()
        }
    }
}
