package mba.vm.onhit.ui.widget

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri
import mba.vm.onhit.hook.core.tag.TagType
import java.io.File

data class WidgetConfig(
    val uri: Uri,
    val tagType: TagType,
    val name: String,
    val iconPath: String?
)

/**
 * Per-widget configuration. Widget IDs are assigned by the launcher, so both the preferences and
 * the icon file are keyed by ID; [migrate] moves them when the IDs change during a backup restore.
 */
object WidgetConfigStore {
    private const val PREFS = "onhit_widgets"
    private const val KEY_URI = "uri_"
    private const val KEY_TYPE = "type_"
    private const val KEY_NAME = "name_"

    private data class Snapshot(
        val oldId: Int,
        val newId: Int,
        val uri: String,
        val type: Int,
        val name: String,
        val icon: File?
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun iconFile(context: Context, appWidgetId: Int): File {
        val dir = File(context.filesDir, "widget_icons")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "widget_$appWidgetId.png")
    }

    fun load(context: Context, appWidgetId: Int): WidgetConfig? {
        val prefs = prefs(context)
        val uri = prefs.getString(KEY_URI + appWidgetId, null) ?: return null
        val icon = iconFile(context, appWidgetId)
        return WidgetConfig(
            uri = uri.toUri(),
            tagType = TagType.fromByte(
                prefs.getInt(KEY_TYPE + appWidgetId, TagType.NDEF.value.toInt()).toByte()
            ),
            name = prefs.getString(KEY_NAME + appWidgetId, "").orEmpty(),
            iconPath = icon.takeIf { it.isFile }?.absolutePath
        )
    }

    fun save(context: Context, appWidgetId: Int, uri: Uri, tagType: TagType, name: String) {
        prefs(context).edit {
            putString(KEY_URI + appWidgetId, uri.toString())
            putInt(KEY_TYPE + appWidgetId, tagType.value.toInt())
            putString(KEY_NAME + appWidgetId, name)
        }
    }

    fun clear(context: Context, appWidgetId: Int) {
        prefs(context).edit {
            remove(KEY_URI + appWidgetId)
            remove(KEY_TYPE + appWidgetId)
            remove(KEY_NAME + appWidgetId)
        }
        iconFile(context, appWidgetId).delete()
    }

    fun migrate(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        val prefs = prefs(context)
        val snapshots = oldWidgetIds.indices.mapNotNull { index ->
            val oldId = oldWidgetIds[index]
            val newId = newWidgetIds.getOrNull(index) ?: return@mapNotNull null
            val uri = prefs.getString(KEY_URI + oldId, null) ?: return@mapNotNull null
            Snapshot(
                oldId = oldId,
                newId = newId,
                uri = uri,
                type = prefs.getInt(KEY_TYPE + oldId, TagType.NDEF.value.toInt()),
                name = prefs.getString(KEY_NAME + oldId, "").orEmpty(),
                icon = iconFile(context, oldId).takeIf { it.isFile }
            )
        }

        // Copy icons to temporary files first: old and new IDs may form a permutation, and
        // writing widget_<newId>.png directly could overwrite an icon that has not been read yet.
        val tempIcons = snapshots.map { snapshot ->
            val icon = snapshot.icon
                ?.takeIf { snapshot.oldId != snapshot.newId }
                ?.let { source ->
                    val temp = File(context.cacheDir, "widget_migrate_${snapshot.oldId}.png")
                    runCatching { source.copyTo(temp, overwrite = true) }.getOrNull()
                }
            snapshot to icon
        }

        prefs.edit {
            oldWidgetIds.forEach { oldId ->
                remove(KEY_URI + oldId)
                remove(KEY_TYPE + oldId)
                remove(KEY_NAME + oldId)
            }
            snapshots.forEach { snapshot ->
                putString(KEY_URI + snapshot.newId, snapshot.uri)
                putInt(KEY_TYPE + snapshot.newId, snapshot.type)
                putString(KEY_NAME + snapshot.newId, snapshot.name)
            }
        }

        tempIcons.forEach { (snapshot, temp) ->
            if (snapshot.oldId == snapshot.newId) return@forEach
            val target = iconFile(context, snapshot.newId)
            if (temp == null) {
                target.delete()
            } else {
                runCatching { temp.copyTo(target, overwrite = true) }
                temp.delete()
            }
        }

        oldWidgetIds.forEach { oldId ->
            if (oldId !in newWidgetIds) iconFile(context, oldId).delete()
        }
    }
}
