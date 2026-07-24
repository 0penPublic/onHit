package mba.vm.onhit

import android.content.pm.PackageManager

class Constant {
    companion object {
        const val NFC_SERVICE_PACKAGE_NAME = "com.android.nfc"
        // Hide on Android API
        const val PACKAGE_MANAGER_FEATURE_NFC_ANY = "android.hardware.nfc.any"
        const val BROADCAST_TAG_EMULATOR_REQUEST = "${BuildConfig.APPLICATION_ID}.TAG_EMULATOR_REQUEST"
        const val BROADCAST_TOGGLE_TAG_RECORDER_REQUEST = "${BuildConfig.APPLICATION_ID}.TOGGLE_TAG_RECORDER_REQUEST"
        const val BROADCAST_TAG_RECORDER_STATE_REQUEST = "${BuildConfig.APPLICATION_ID}.BROADCAST_TAG_RECORDER_STATE_REQUEST"
        const val BROADCAST_TAG_RECORDER_STATE_RESPONSE = "${BuildConfig.APPLICATION_ID}.TAG_RECORDER_STATE_RESPONSE"
        const val BROADCAST_TAG_RECORDER_RESPONSE = "${BuildConfig.APPLICATION_ID}.TAG_RECORDER_RESPONSE"
        const val SHARED_PREFERENCES_NAME = BuildConfig.APPLICATION_ID
        const val SHARED_PREFERENCES_CHOSEN_FOLDER = "chosen_folder"
        const val PREF_FIXED_UID = "pref_fixed_uid"
        const val PREF_FIXED_UID_VALUE = "pref_fixed_uid_value"
        const val PREF_RANDOM_UID_LEN = "pref_random_uid_len"

        const val PREF_BACKGROUND_URI = "pref_background_uri"
        const val MAX_OF_BROADCAST_SIZE = 1048576
        const val GITHUB_URL = "https://github.com/0penPublic/onHit"
        const val TELEGRAM_URL = "https://t.me/on_hit"

        const val REQUEST_SELECT_BACKGROUND = 1002
        const val REQUEST_CROP_BACKGROUND = 1003
        const val MIFARE_CLASSICAL_BLOCK_SIZE = 16
        val PACKAGE_MANAGER_SYSTEM_NFC_FEATURES = setOf(PackageManager.FEATURE_NFC, PACKAGE_MANAGER_FEATURE_NFC_ANY)
    }
}