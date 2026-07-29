package mba.vm.onhit.ui.nfc.handler

import android.content.Context
import android.nfc.NdefRecord
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import mba.vm.onhit.R
import mba.vm.onhit.model.BuiltRecord
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

    override fun getLayoutId(type: String): Int = R.layout.layout_ndef_input_common

    override fun bindView(
        container: ViewGroup,
        record: BuiltRecord,
        type: String,
        onUpdate: () -> Unit,
        onPickFile: () -> Unit,
        onExportPayload: (String) -> Unit
    ) {
        val etValue = container.findViewById<EditText>(R.id.et_ndef_value)
        val tvLabel = container.findViewById<TextView>(R.id.tv_ndef_value_label)

        tvLabel?.text = context.getString(R.string.build_ndef_value)
        etValue?.setText(record.value)
        etValue?.doAfterTextChanged { onUpdate() }
    }

    override fun updateRecordFromUI(
        container: ViewGroup,
        oldRecord: BuiltRecord,
        type: String,
        buildNdefRecord: (String, String, String?, ByteArray?) -> NdefRecord
    ): BuiltRecord {
        val value = container.findViewById<EditText>(R.id.et_ndef_value)?.text?.toString()?.trim() ?: ""

        val newNdefRecord = try {
            buildNdefRecord(type, value, null, oldRecord.payload)
        } catch (_: Exception) {
            oldRecord.record
        }

        return BuiltRecord(type, value, newNdefRecord, null, oldRecord.payload)
    }

    override fun getSummary(record: BuiltRecord): String {
        return record.value
    }
}
