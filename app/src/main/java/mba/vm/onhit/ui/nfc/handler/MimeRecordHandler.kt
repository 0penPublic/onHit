package mba.vm.onhit.ui.nfc.handler

import android.content.Context
import android.nfc.NdefRecord
import mba.vm.onhit.R
import mba.vm.onhit.ui.model.BuiltRecord
import mba.vm.onhit.utils.HexUtils

class MimeRecordHandler(context: Context) : NdefRecordHandler(context) {
    override fun canHandle(record: NdefRecord): Boolean {
        return record.tnf == NdefRecord.TNF_MIME_MEDIA
    }

    override fun canHandle(type: String): Boolean {
        return type == context.getString(R.string.build_ndef_type_mime)
    }

    override fun parse(record: NdefRecord): BuiltRecord {
        val mimeType = String(record.type, Charsets.US_ASCII)
        return BuiltRecord(
            type = context.getString(R.string.build_ndef_type_mime),
            value = mimeType,
            record = record,
            payload = record.payload
        )
    }

    override fun build(value: String, lang: String?, payload: ByteArray?, existingRecord: NdefRecord?): NdefRecord {
        val mimePayload = payload ?: HexUtils.decodeHex(value)
        val parts = value.split(":", limit = 2)
        val mimeType = if (parts.size == 2 && payload == null) parts[0].trim() else value.trim()
        val finalMime = if (mimeType.contains("/")) mimeType else "application/octet-stream"
        return NdefRecord.createMime(finalMime, mimePayload)
    }
}
