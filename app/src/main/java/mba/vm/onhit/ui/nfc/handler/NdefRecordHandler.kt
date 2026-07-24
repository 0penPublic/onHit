package mba.vm.onhit.ui.nfc.handler

import android.content.Context
import android.nfc.NdefRecord
import mba.vm.onhit.ui.model.BuiltRecord

abstract class NdefRecordHandler(protected val context: Context) {
    abstract fun canHandle(record: NdefRecord): Boolean
    abstract fun canHandle(type: String): Boolean
    abstract fun parse(record: NdefRecord): BuiltRecord?
    abstract fun build(value: String, lang: String?, payload: ByteArray?, existingRecord: NdefRecord? = null): NdefRecord
}
