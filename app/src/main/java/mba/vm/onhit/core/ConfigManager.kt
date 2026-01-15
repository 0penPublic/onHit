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

object ConfigManager {
    fun getRootUri(context: Context): Uri? {
        val prefs = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
        return prefs.getString(SHARED_PREFERENCES_CHOSEN_FOLDER, null).let { if (it.isNullOrEmpty()) null else Uri.parse(it) }
    }

    fun setRootUri(context: Context, uri: Uri) {
        context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit().putString(SHARED_PREFERENCES_CHOSEN_FOLDER, uri.toString()).apply()
    }

    fun isFixedUid(context: Context): Boolean {
        return context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_FIXED_UID, false)
    }

    fun setFixedUid(context: Context, fixed: Boolean) {
        context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(PREF_FIXED_UID, fixed).apply()
    }

    fun getFixedUidValue(context: Context): String {
        return context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(PREF_FIXED_UID_VALUE, "") ?: ""
    }

    fun setFixedUidValue(context: Context, value: String) {
        context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit().putString(PREF_FIXED_UID_VALUE, value).apply()
    }

    fun getRandomUidLen(context: Context): String {
        return context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(PREF_RANDOM_UID_LEN, "4") ?: "4"
    }

    fun setRandomUidLen(context: Context, len: String) {
        context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit().putString(PREF_RANDOM_UID_LEN, len).apply()
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
