package mba.vm.onhit.ui.nfc.handler

import android.content.Context
import android.nfc.NdefRecord
import mba.vm.onhit.R
import mba.vm.onhit.ui.model.BuiltRecord
import mba.vm.onhit.utils.HexUtils
import java.util.Locale

class UnknownRecordHandler(context: Context) : NdefRecordHandler(context) {
    override fun canHandle(record: NdefRecord): Boolean = true
    override fun canHandle(type: String): Boolean = true

    override fun parse(record: NdefRecord): BuiltRecord {
        val label = context.getString(R.string.build_ndef_type_unknown, String.format(Locale.getDefault(), "TNF:%d", record.tnf))
        return BuiltRecord(
            type = label,
            value = HexUtils.toHexString(record.payload),
            record = record,
            payload = record.payload
        )
    }

    override fun build(value: String, lang: String?, payload: ByteArray?, existingRecord: NdefRecord?): NdefRecord {
        val rawPayload = payload ?: HexUtils.decodeHex(value)
        return if (existingRecord != null) {
            NdefRecord(existingRecord.tnf, existingRecord.type, existingRecord.id, rawPayload)
        } else {
            // Fallback to text record if we have nothing to go on
            NdefRecord.createTextRecord(lang ?: Locale.getDefault().language.ifBlank { "en" }, value)
        }
    }
}
