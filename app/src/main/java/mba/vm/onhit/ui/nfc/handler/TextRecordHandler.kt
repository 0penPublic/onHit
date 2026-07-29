package mba.vm.onhit.ui.nfc.handler

import android.content.Context
import android.nfc.NdefRecord
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.widget.doAfterTextChanged
import mba.vm.onhit.R
import mba.vm.onhit.model.BuiltRecord
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

    override fun getLayoutId(type: String): Int = R.layout.layout_ndef_input_text

    override fun bindView(
        container: ViewGroup,
        record: BuiltRecord,
        type: String,
        onUpdate: () -> Unit,
        onPickFile: () -> Unit,
        onExportPayload: (String) -> Unit
    ) {
        val etValue = container.findViewById<EditText>(R.id.et_ndef_value)
        val etLang = container.findViewById<EditText>(R.id.et_ndef_lang)

        etValue?.setText(record.value)
        etLang?.setText(record.lang ?: "")

        etValue?.doAfterTextChanged { onUpdate() }
        etLang?.doAfterTextChanged { onUpdate() }
    }

    override fun updateRecordFromUI(
        container: ViewGroup,
        oldRecord: BuiltRecord,
        type: String,
        buildNdefRecord: (String, String, String?, ByteArray?) -> NdefRecord
    ): BuiltRecord {
        val value = container.findViewById<EditText>(R.id.et_ndef_value)?.text?.toString()?.trim() ?: ""
        val lang = container.findViewById<EditText>(R.id.et_ndef_lang)?.text?.toString()?.trim()?.ifEmpty { null }

        val newNdefRecord = try {
            buildNdefRecord(type, value, lang, null)
        } catch (_: Exception) {
            oldRecord.record
        }

        return BuiltRecord(type, value, newNdefRecord, lang, null)
    }
}
