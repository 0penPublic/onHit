package mba.vm.onhit.ui.nfc.handler

import android.content.Context
import android.nfc.NdefRecord
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import mba.vm.onhit.R
import mba.vm.onhit.model.BuiltRecord
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

    override fun getLayoutId(type: String): Int = R.layout.layout_ndef_input_payload

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
        val btnSelectFile = container.findViewById<View>(R.id.btn_select_file)
        val btnExportPayload = container.findViewById<View>(R.id.btn_export_payload)

        tvLabel?.text = context.getString(R.string.build_ndef_label_type)
        etValue?.hint = context.getString(R.string.build_ndef_mime_value_hint)
        etValue?.setText(record.value)

        etValue?.doAfterTextChanged { onUpdate() }
        btnSelectFile?.setOnClickListener { onPickFile() }
        btnExportPayload?.setOnClickListener {
            onExportPayload(etValue?.text?.toString()?.trim() ?: "")
        }
    }

    override fun updateRecordFromUI(
        container: ViewGroup,
        oldRecord: BuiltRecord,
        type: String,
        buildNdefRecord: (String, String, String?, ByteArray?) -> NdefRecord
    ): BuiltRecord {
        val value = container.findViewById<EditText>(R.id.et_ndef_value)?.text?.toString()?.trim() ?: ""
        var payload = oldRecord.payload

        if (oldRecord.type == type && oldRecord.value != value) payload = null

        val newNdefRecord = try {
            buildNdefRecord(type, value, null, payload)
        } catch (_: Exception) {
            oldRecord.record
        }

        return BuiltRecord(type, value, newNdefRecord, null, payload)
    }
}
