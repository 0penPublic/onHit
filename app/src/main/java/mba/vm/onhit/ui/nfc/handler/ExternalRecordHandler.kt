package mba.vm.onhit.ui.nfc.handler

import android.content.Context
import android.nfc.NdefRecord
import mba.vm.onhit.R
import mba.vm.onhit.ui.model.BuiltRecord
import mba.vm.onhit.utils.HexUtils

class ExternalRecordHandler(context: Context) : NdefRecordHandler(context) {
    override fun canHandle(record: NdefRecord): Boolean {
        return record.tnf == NdefRecord.TNF_EXTERNAL_TYPE
    }

    override fun canHandle(type: String): Boolean {
        return type == context.getString(R.string.build_ndef_type_external)
    }

    override fun parse(record: NdefRecord): BuiltRecord {
        val typeStr = String(record.type, Charsets.US_ASCII)
        return BuiltRecord(
            type = context.getString(R.string.build_ndef_type_external),
            value = typeStr,
            record = record,
            payload = record.payload
        )
    }

    override fun build(value: String, lang: String?, payload: ByteArray?, existingRecord: NdefRecord?): NdefRecord {
        val extPayload = payload ?: HexUtils.decodeHex(value)
        val parts = value.split(":", limit = 2)
        return if (parts.size == 2) {
            NdefRecord.createExternal(parts[0].trim(), parts[1].trim(), extPayload)
        } else {
            NdefRecord.createExternal("android.com", value.trim(), extPayload)
        }
    }
}
