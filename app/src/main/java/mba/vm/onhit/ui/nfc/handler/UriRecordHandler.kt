package mba.vm.onhit.ui.nfc.handler

import android.content.Context
import android.nfc.NdefRecord
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import mba.vm.onhit.R
import mba.vm.onhit.model.BuiltRecord

class UriRecordHandler(context: Context) : NdefRecordHandler(context) {
    override fun canHandle(record: NdefRecord): Boolean {
        return record.tnf == NdefRecord.TNF_WELL_KNOWN && 
               record.type.contentEquals(NdefRecord.RTD_URI)
    }

    override fun canHandle(type: String): Boolean {
        return type == context.getString(R.string.build_ndef_type_website) ||
               type == context.getString(R.string.build_ndef_type_phone)
    }

    override fun parse(record: NdefRecord): BuiltRecord? {
        val payload = record.payload
        if (payload.isEmpty()) return null
        
        val prefixCode = payload[0].toInt() and 0xFF
        val prefix = URI_PREFIX_MAP[prefixCode] ?: ""
        val content = String(payload, 1, payload.size - 1, Charsets.UTF_8)
        val fullUri = prefix + content
        
        val type = if (fullUri.startsWith("tel:", ignoreCase = true)) {
            context.getString(R.string.build_ndef_type_phone)
        } else {
            context.getString(R.string.build_ndef_type_website)
        }

        return BuiltRecord(
            type = type,
            value = fullUri,
            record = record
        )
    }

    override fun build(value: String, lang: String?, payload: ByteArray?, existingRecord: NdefRecord?): NdefRecord {
        if (value.isEmpty()) {
            return NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_URI, null, byteArrayOf(0x00))
        }
        return NdefRecord.createUri(value)
    }

    override fun getLayoutId(type: String): Int {
        return if (type == context.getString(R.string.build_ndef_type_website)) {
            R.layout.layout_ndef_input_uri
        } else {
            R.layout.layout_ndef_input_common
        }
    }

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

        tvLabel?.text = when (type) {
            context.getString(R.string.build_ndef_type_website) -> context.getString(R.string.build_ndef_label_url)
            context.getString(R.string.build_ndef_type_phone) -> context.getString(R.string.build_ndef_label_phone)
            else -> context.getString(R.string.build_ndef_value)
        }

        if (type == context.getString(R.string.build_ndef_type_website)) {
            val spinnerPrefix = container.findViewById<Spinner>(R.id.spinner_uri_prefix)
            val prefixes = getPrefixes()
            val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, prefixes)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerPrefix?.adapter = adapter

            var matchedPrefix = ""
            var matchedIndex = 0
            val noneLabel = context.getString(R.string.build_ndef_uri_prefix_none)
            for ((index, prefix) in prefixes.withIndex()) {
                if (prefix != noneLabel && prefix != "Custom" &&
                    record.value.startsWith(prefix, ignoreCase = true)) {
                    if (prefix.length > matchedPrefix.length) {
                        matchedPrefix = prefix
                        matchedIndex = index
                    }
                }
            }

            spinnerPrefix?.onItemSelectedListener = null
            etValue?.setText(record.value.substring(matchedPrefix.length))
            spinnerPrefix?.setSelection(matchedIndex)

            if (matchedIndex == 0) {
                etValue?.hint = context.getString(R.string.build_ndef_value_hint)
            } else {
                etValue?.hint = "..."
            }

            spinnerPrefix?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, id: Long) {
                    if (pos == 0) {
                        etValue?.hint = context.getString(R.string.build_ndef_value_hint)
                    } else {
                        etValue?.hint = "..."
                    }
                    onUpdate()
                }
                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }
        } else {
            etValue?.setText(record.value)
        }

        etValue?.doAfterTextChanged { onUpdate() }
    }

    override fun updateRecordFromUI(
        container: ViewGroup,
        oldRecord: BuiltRecord,
        type: String,
        buildNdefRecord: (String, String, String?, ByteArray?) -> NdefRecord
    ): BuiltRecord {
        val value: String
        if (type == context.getString(R.string.build_ndef_type_website)) {
            val spinnerPrefix = container.findViewById<Spinner>(R.id.spinner_uri_prefix)
            val prefix = spinnerPrefix?.selectedItem?.toString() ?: ""
            val noneLabel = context.getString(R.string.build_ndef_uri_prefix_none)
            val actualPrefix = if (prefix == noneLabel || prefix == "Custom (Full URI)" || prefix == "Custom") "" else prefix
            val rest = container.findViewById<EditText>(R.id.et_ndef_value)?.text?.toString()?.trim() ?: ""
            value = actualPrefix + rest
        } else {
            value = container.findViewById<EditText>(R.id.et_ndef_value)?.text?.toString()?.trim() ?: ""
        }

        val newNdefRecord = try {
            buildNdefRecord(type, value, null, null)
        } catch (_: Exception) {
            oldRecord.record
        }

        return BuiltRecord(type, value, newNdefRecord, null, null)
    }

    fun getPrefixes(): List<String> {
        val list = mutableListOf<String>()
        list.add(context.getString(R.string.build_ndef_uri_prefix_none))
        // Add all standard prefixes from the map except 0x00 which is handled above
        URI_PREFIX_MAP.entries
            .filter { it.key != 0x00 }
            .sortedBy { it.key }
            .forEach { list.add(it.value) }
        return list
    }

    companion object {
        val URI_PREFIX_MAP = mapOf(
            0x00 to "",
            0x01 to "http://www.",
            0x02 to "https://www.",
            0x03 to "http://",
            0x04 to "https://",
            0x05 to "tel:",
            0x06 to "mailto:",
            0x07 to "ftp://anonymous:anonymous@",
            0x08 to "ftp://ftp.",
            0x09 to "ftps://",
            0x0A to "sftp://",
            0x0B to "smb://",
            0x0C to "nfs://",
            0x0D to "ftp://",
            0x0E to "dav://",
            0x0F to "news:",
            0x10 to "telnet://",
            0x11 to "imap:",
            0x12 to "rtsp://",
            0x13 to "urn:",
            0x14 to "pop:",
            0x15 to "sip:",
            0x16 to "sips:",
            0x17 to "tftp:",
            0x18 to "btspp://",
            0x19 to "btl2cap://",
            0x1A to "btgoep://",
            0x1B to "tcpobex://",
            0x1C to "irdaobex://",
            0x1D to "file://",
            0x1E to "urn:epc:id:",
            0x1F to "urn:epc:tag:",
            0x20 to "urn:epc:pat:",
            0x21 to "urn:epc:raw:",
            0x22 to "urn:otp:",
            0x23 to "urn:nfc:"
        )
    }
}
