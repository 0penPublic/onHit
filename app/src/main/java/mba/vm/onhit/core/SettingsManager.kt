package mba.vm.onhit.core

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.net.Uri
import androidx.core.content.edit
import mba.vm.onhit.Constant
import mba.vm.onhit.Constant.Companion.MEGABYTES_TO_BYTES
import mba.vm.onhit.utils.HexUtils
import java.security.SecureRandom

class SettingsManager(context: Context) {
    private val sp = context.getSharedPreferences(Constant.SHARED_PREFERENCES_NAME, MODE_PRIVATE)

    var chosenFolderUri: Uri?
        get() = sp.getString(Constant.SHARED_PREFERENCES_CHOSEN_FOLDER, null)?.let(Uri::parse)
        set(value) {
            sp.edit { putString(Constant.SHARED_PREFERENCES_CHOSEN_FOLDER, value?.toString()) }
        }

    var isFixedUid: Boolean
        get() = sp.getBoolean(Constant.PREF_FIXED_UID, false)
        set(value) {
            sp.edit { putBoolean(Constant.PREF_FIXED_UID, value) }
        }

    var fixedUidValue: String
        get() = sp.getString(Constant.PREF_FIXED_UID_VALUE, "") ?: ""
        set(value) {
            sp.edit { putString(Constant.PREF_FIXED_UID_VALUE, value) }
        }

    var randomUidLen: String
        get() = sp.getString(Constant.PREF_RANDOM_UID_LEN, "4") ?: "4"
        set(value) {
            sp.edit { putString(Constant.PREF_RANDOM_UID_LEN, value) }
        }

    fun getUid(): ByteArray {
        if (isFixedUid) {
            return HexUtils.decodeHex(fixedUidValue)
        }
        val len: Int = randomUidLen.toIntOrNull() ?: 4
        val actualLen = len.takeIf { it > 0 } ?: 4
        if (actualLen > MEGABYTES_TO_BYTES) throw IllegalArgumentException("UID length cannot exceed 1 MB")
        return ByteArray(actualLen).apply {
            SecureRandom().nextBytes(this)
        }
    }
}
