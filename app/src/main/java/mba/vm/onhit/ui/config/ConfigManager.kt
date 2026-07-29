package mba.vm.onhit.ui.config

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri
import mba.vm.onhit.Constant.Companion.MAX_OF_BROADCAST_SIZE
import mba.vm.onhit.Constant.Companion.PREF_BACKGROUND_URI
import mba.vm.onhit.Constant.Companion.PREF_FIXED_UID_VALUE
import mba.vm.onhit.Constant.Companion.PREF_RANDOM_UID_LEN
import mba.vm.onhit.Constant.Companion.PREF_UID_MODE
import mba.vm.onhit.Constant.Companion.SHARED_PREFERENCES_CHOSEN_FOLDER
import mba.vm.onhit.Constant.Companion.SHARED_PREFERENCES_NAME
import mba.vm.onhit.utils.HexUtils
import java.security.SecureRandom

object ConfigManager {
    fun getRootUri(context: Context): Uri? {
        val prefs = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
        return prefs.getString(SHARED_PREFERENCES_CHOSEN_FOLDER, null).let { if (it.isNullOrEmpty()) null else it.toUri() }
    }

    fun setRootUri(context: Context, uri: Uri) {
        context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit { putString(SHARED_PREFERENCES_CHOSEN_FOLDER, uri.toString()) }
    }

    const val UID_MODE_FILE = 0
    const val UID_MODE_LEN = 1
    const val UID_MODE_FIXED = 2

    fun getUidMode(context: Context): Int {
        return context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getInt(PREF_UID_MODE, UID_MODE_LEN)
    }

    fun setUidMode(context: Context, mode: Int) {
        context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit { putInt(PREF_UID_MODE, mode) }
    }

    fun getFixedUidValue(context: Context): String {
        return context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(PREF_FIXED_UID_VALUE, "") ?: ""
    }

    fun setFixedUidValue(context: Context, value: String) {
        context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit { putString(PREF_FIXED_UID_VALUE, value) }
    }

    fun getRandomUidLen(context: Context): String {
        return context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(PREF_RANDOM_UID_LEN, "4") ?: "4"
    }


    fun setRandomUidLen(context: Context, len: String) {
        context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit { putString(PREF_RANDOM_UID_LEN, len) }
    }


    fun getBackgroundUri(context: Context): Uri? {
        val value = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(PREF_BACKGROUND_URI, null)
        return if (value.isNullOrEmpty()) null else value.toUri()
    }

    fun setBackgroundUri(context: Context, uri: Uri?) {
        context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit {
                if (uri == null) remove(PREF_BACKGROUND_URI)
                else putString(PREF_BACKGROUND_URI, uri.toString())
            }
    }

    fun genUid(context: Context): ByteArray? {
        val mode = getUidMode(context)
        return when (mode) {
            UID_MODE_FILE -> null
            UID_MODE_FIXED -> {
                val hex = getFixedUidValue(context)
                HexUtils.decodeHex(hex)
            }
            else -> {
                val lenStr = getRandomUidLen(context)
                val len: Int = lenStr.toIntOrNull() ?: 4
                val actualLen = len.coerceIn(0, MAX_OF_BROADCAST_SIZE)
                ByteArray(actualLen).apply {
                    SecureRandom().nextBytes(this)
                }
            }
        }
    }
}
