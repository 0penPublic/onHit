package mba.vm.onhit.ui.nfc.handler

import android.content.Context
import android.nfc.NdefRecord
import mba.vm.onhit.R
import mba.vm.onhit.ui.model.BuiltRecord

class AarRecordHandler(context: Context) : NdefRecordHandler(context) {
    override fun canHandle(record: NdefRecord): Boolean {
        val typeStr = String(record.type, Charsets.US_ASCII)
        return record.tnf == NdefRecord.TNF_EXTERNAL_TYPE && typeStr == "android.com:pkg"
    }

    override fun canHandle(type: String): Boolean {
        return type == context.getString(R.string.build_ndef_type_aar)
    }

    override fun parse(record: NdefRecord): BuiltRecord {
        return BuiltRecord(
            type = context.getString(R.string.build_ndef_type_aar),
            value = String(record.payload, Charsets.UTF_8),
            record = record
        )
    }

    override fun build(value: String, lang: String?, payload: ByteArray?, existingRecord: NdefRecord?): NdefRecord {
        return NdefRecord.createApplicationRecord(value.trim())
    }
}
