package mba.vm.onhit.ui.nfc.handler

import android.content.Context
import android.nfc.NdefRecord
import mba.vm.onhit.R
import mba.vm.onhit.ui.model.BuiltRecord
import java.util.Locale

class TextRecordHandler(context: Context) : NdefRecordHandler(context) {
    override fun canHandle(record: NdefRecord): Boolean {
        return record.tnf == NdefRecord.TNF_WELL_KNOWN && 
               record.type.contentEquals(NdefRecord.RTD_TEXT)
    }

    override fun canHandle(type: String): Boolean {
        return type == context.getString(R.string.build_ndef_type_text)
    }

    override fun parse(record: NdefRecord): BuiltRecord? {
        val payload = record.payload
        if (payload.isEmpty()) return null
        val status = payload[0].toInt()
        val isUtf16 = (status and 0x80) != 0
        val langCodeLen = status and 0x3F
        val charset = if (isUtf16) Charsets.UTF_16 else Charsets.UTF_8
        
        val lang = if (payload.size >= 1 + langCodeLen) String(payload, 1, langCodeLen, Charsets.US_ASCII) else ""
        val text = if (payload.size > 1 + langCodeLen) String(payload, 1 + langCodeLen, payload.size - 1 - langCodeLen, charset) else ""
        
        return BuiltRecord(
            type = context.getString(R.string.build_ndef_type_text),
            value = text,
            record = record,
            lang = lang
        )
    }

    override fun build(value: String, lang: String?, payload: ByteArray?, existingRecord: NdefRecord?): NdefRecord {
        val language = lang ?: Locale.getDefault().language.ifBlank { "en" }
        return NdefRecord.createTextRecord(language, value)
    }
}
