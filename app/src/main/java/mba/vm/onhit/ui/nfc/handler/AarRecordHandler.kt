package mba.vm.onhit.ui.nfc.handler

import android.content.Context
import android.nfc.NdefRecord
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import mba.vm.onhit.R
import mba.vm.onhit.model.BuiltRecord

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

        tvLabel?.text = context.getString(R.string.build_ndef_label_package)
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
            buildNdefRecord(type, value, null, null)
        } catch (_: Exception) {
            oldRecord.record
        }

        return BuiltRecord(type, value, newNdefRecord, null, null)
    }
}
