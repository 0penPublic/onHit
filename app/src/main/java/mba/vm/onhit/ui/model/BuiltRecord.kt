package mba.vm.onhit.ui.model

import android.nfc.NdefRecord

data class BuiltRecord(
    val type: String,
    val value: String,
    val record: NdefRecord
)
