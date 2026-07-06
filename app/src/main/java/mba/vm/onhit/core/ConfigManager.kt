package mba.vm.onhit.core

import android.content.Context
import android.net.Uri
import mba.vm.onhit.Constant.Companion.MAX_OF_BROADCAST_SIZE
import mba.vm.onhit.Constant.Companion.PREF_FIXED_UID
import mba.vm.onhit.Constant.Companion.PREF_FIXED_UID_VALUE
import mba.vm.onhit.Constant.Companion.PREF_RANDOM_UID_LEN
import mba.vm.onhit.Constant.Companion.SHARED_PREFERENCES_CHOSEN_FOLDER
import mba.vm.onhit.Constant.Companion.SHARED_PREFERENCES_NAME
import mba.vm.onhit.utils.HexUtils
import java.security.SecureRandom
import mba.vm.onhit.Constant.Companion.PREF_BACKGROUND_URI
import androidx.core.content.edit
import androidx.core.net.toUri

object ConfigManager {
    fun getRootUri(context: Context): Uri? {
        val prefs = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
        return prefs.getString(SHARED_PREFERENCES_CHOSEN_FOLDER, null).let { if (it.isNullOrEmpty()) null else it.toUri() }
    }

    fun setRootUri(context: Context, uri: Uri) {
        context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit { putString(SHARED_PREFERENCES_CHOSEN_FOLDER, uri.toString()) }
    }

    fun isFixedUid(context: Context): Boolean {
        return context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_FIXED_UID, false)
    }

    fun setFixedUid(context: Context, fixed: Boolean) {
        context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit { putBoolean(PREF_FIXED_UID, fixed) }
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

    fun getUid(context: Context): ByteArray {
        if (isFixedUid(context)) {
            val hex = getFixedUidValue(context)
            val bytes = HexUtils.decodeHex(hex)
            if (bytes.isNotEmpty()) return bytes
        }
        
        val lenStr = getRandomUidLen(context)
        val len: Int = lenStr.toIntOrNull() ?: 0
        val actualLen = len.coerceIn(0, MAX_OF_BROADCAST_SIZE)
        
        return ByteArray(actualLen).apply {
            SecureRandom().nextBytes(this)
        }
    }
}
