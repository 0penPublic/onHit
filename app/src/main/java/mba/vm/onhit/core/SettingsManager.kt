package mba.vm.onhit.core

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.net.Uri
import androidx.core.content.edit
import mba.vm.onhit.Constant
import mba.vm.onhit.utils.HexUtils
import kotlin.random.Random

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
        return if (isFixedUid) {
            HexUtils.decodeHex(fixedUidValue)
        } else {
            val len = randomUidLen.toIntOrNull() ?: 4
            Random.nextBytes(if (len > 0) len else 4)
        }
    }
}
