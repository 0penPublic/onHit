package mba.vm.onhit.ui.handler

import android.app.Activity
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import mba.vm.onhit.R
import mba.vm.onhit.helper.DialogHelper
import mba.vm.onhit.ui.model.BuiltRecord
import mba.vm.onhit.utils.HexUtils
import java.util.Locale

class NdefEditor(
    private val activity: Activity,
    private val onSave: (ByteArray, DocumentFile?) -> Unit
) {

    fun showBuildNdefDialog(initialBytes: ByteArray? = null, fileToEdit: DocumentFile? = null) {
        if (initialBytes != null && initialBytes.size > 1024) {
            Toast.makeText(activity, R.string.build_ndef_error_file_too_large, Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = DialogHelper.createBottomDialog(
            activity,
            R.layout.bottom_sheet_build_ndef
        )

        val builtRecords = mutableListOf<BuiltRecord>()
        if (initialBytes != null) {
            builtRecords.addAll(parseNdefMessage(initialBytes))
        }

        val spinner = dialog.findViewById<Spinner>(R.id.spinner_ndef_type)
        val input = dialog.findViewById<EditText>(R.id.et_ndef_value)
        val btnAddRecord = dialog.findViewById<Button>(R.id.btn_add_ndef_record)
        val btnClearRecords = dialog.findViewById<Button>(R.id.btn_clear_ndef_records)
        val tvRecordCount = dialog.findViewById<TextView>(R.id.tv_ndef_record_count)
        val rvRecords = dialog.findViewById<RecyclerView>(R.id.rv_ndef_records)
        val tvEmpty = dialog.findViewById<TextView>(R.id.tv_ndef_empty)
        val btnOk = dialog.findViewById<Button>(R.id.btn_ndef_ok)
        val btnCancel = dialog.findViewById<Button>(R.id.btn_ndef_cancel)

        var editingIndex: Int? = null
        var recordAdapter: NdefRecordAdapter? = null

        val types = listOf(
            activity.getString(R.string.build_ndef_type_website),
            activity.getString(R.string.build_ndef_type_phone),
            activity.getString(R.string.build_ndef_type_text),
            activity.getString(R.string.build_ndef_type_aar),
            activity.getString(R.string.build_ndef_type_mime),
            activity.getString(R.string.build_ndef_type_external)
        )

        spinner.adapter = ArrayAdapter(
            activity,
            android.R.layout.simple_spinner_item,
            types
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        fun refreshRecordList() {
            val bytesSize = if (builtRecords.isEmpty()) {
                0
            } else {
                buildNdefBytes(builtRecords.map { it.record }).size
            }

            if (editingIndex == null) {
                tvRecordCount.text = if (bytesSize > 0) {
                    val capacity = when {
                        bytesSize <= 144 -> "NTAG213"
                        bytesSize <= 504 -> "NTAG215/216"
                        bytesSize <= 888 -> "NTAG216"
                        else -> "> NTAG216"
                    }
                    activity.getString(
                        R.string.build_ndef_record_status_with_capacity,
                        builtRecords.size,
                        bytesSize,
                        capacity
                    )
                } else {
                    activity.getString(
                        R.string.build_ndef_record_status,
                        builtRecords.size,
                        bytesSize
                    )
                }
            } else {
                tvRecordCount.text = activity.getString(R.string.build_ndef_editing_index, editingIndex!! + 1)
            }

            if (builtRecords.isEmpty()) {
                tvEmpty.visibility = View.VISIBLE
                rvRecords.visibility = View.GONE
            } else {
                tvEmpty.visibility = View.GONE
                rvRecords.visibility = View.VISIBLE
                recordAdapter?.updateData(builtRecords)
            }
        }

        fun enterEditMode(index: Int, record: BuiltRecord) {
            editingIndex = index
            val typeIndex = types.indexOf(record.type)
            if (typeIndex >= 0) spinner.setSelection(typeIndex)
            input.setText(record.value)

            btnAddRecord.text = activity.getString(R.string.build_ndef_cancel_edit)
            btnClearRecords.text = activity.getString(R.string.build_ndef_update_record)
            refreshRecordList()
        }

        fun exitEditMode() {
            editingIndex = null
            input.setText("")
            btnAddRecord.text = activity.getString(R.string.build_ndef_add_record)
            btnClearRecords.text = activity.getString(R.string.build_ndef_clear_records)
            refreshRecordList()
        }

        recordAdapter = NdefRecordAdapter(builtRecords) { index, record ->
            enterEditMode(index, record)
        }
        rvRecords.layoutManager = LinearLayoutManager(activity)
        rvRecords.adapter = recordAdapter

        fun addCurrentRecord(): Boolean {
            val type = spinner.selectedItem.toString()
            val value = input.text.toString().trim()

            if (value.isEmpty()) {
                Toast.makeText(
                    activity,
                    R.string.build_ndef_error_empty_value,
                    Toast.LENGTH_SHORT
                ).show()
                return false
            }

            return try {
                val record = buildNdefRecord(type, value)
                builtRecords.add(
                    BuiltRecord(
                        type = type,
                        value = value,
                        record = record
                    )
                )
                input.setText("")
                refreshRecordList()
                true
            } catch (e: Exception) {
                Toast.makeText(
                    activity,
                    activity.getString(
                        R.string.build_ndef_error_add_failed,
                        e.message ?: activity.getString(R.string.unknown_error)
                    ),
                    Toast.LENGTH_SHORT
                ).show()
                false
            }
        }

        btnAddRecord.setOnClickListener {
            if (editingIndex != null) {
                exitEditMode()
            } else {
                addCurrentRecord()
            }
        }

        btnClearRecords.setOnClickListener {
            if (editingIndex != null) {
                val index = editingIndex!!
                val type = spinner.selectedItem.toString()
                val value = input.text.toString().trim()
                if (value.isEmpty()) {
                    Toast.makeText(activity, R.string.build_ndef_error_empty_value, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                try {
                    val record = buildNdefRecord(type, value)
                    builtRecords[index] = BuiltRecord(type, value, record)
                    exitEditMode()
                } catch (e: Exception) {
                    Toast.makeText(activity, activity.getString(R.string.build_ndef_error_add_failed, e.message), Toast.LENGTH_SHORT).show()
                }
            } else {
                DialogHelper.showConfirmBottomSheet(
                    activity,
                    activity.getString(R.string.dialog_title_confirm_clear),
                    activity.getString(R.string.confirm_clear_ndef_hint)
                ) {
                    builtRecords.clear()
                    refreshRecordList()
                }
            }
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnOk.setOnClickListener {
            if (editingIndex != null) {
                val index = editingIndex!!
                val type = spinner.selectedItem.toString()
                val value = input.text.toString().trim()
                if (value.isNotEmpty()) {
                    try {
                        val record = buildNdefRecord(type, value)
                        builtRecords[index] = BuiltRecord(type, value, record)
                    } catch (e: Exception) {
                        Toast.makeText(activity, activity.getString(R.string.build_ndef_error_add_failed, e.message), Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                }
            } else if (input.text.toString().trim().isNotEmpty()) {
                if (!addCurrentRecord()) return@setOnClickListener
            }

            if (builtRecords.isEmpty()) {
                Toast.makeText(
                    activity,
                    R.string.build_ndef_error_empty_records,
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            try {
                val bytes = buildNdefBytes(builtRecords.map { it.record })
                onSave(bytes, fileToEdit)
                dialog.dismiss()
            } catch (e: Exception) {
                Toast.makeText(
                    activity,
                    activity.getString(
                        R.string.build_ndef_error_build_failed,
                        e.message ?: activity.getString(R.string.unknown_error)
                    ),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        refreshRecordList()
        dialog.show()
    }

    fun showNdefPreviewDialog(bytes: ByteArray, onConfirm: () -> Unit) {
        val dialog = DialogHelper.createBottomDialog(
            activity,
            R.layout.bottom_sheet_build_ndef
        )

        dialog.findViewById<View>(R.id.layout_editor_inputs).visibility = View.GONE
        dialog.findViewById<TextView>(R.id.tv_build_title).text = activity.getString(R.string.import_ndef)

        val builtRecords = parseNdefMessage(bytes)
        val tvRecordCount = dialog.findViewById<TextView>(R.id.tv_ndef_record_count)
        val rvRecords = dialog.findViewById<RecyclerView>(R.id.rv_ndef_records)
        val tvEmpty = dialog.findViewById<TextView>(R.id.tv_ndef_empty)
        val btnOk = dialog.findViewById<Button>(R.id.btn_ndef_ok)
        val btnCancel = dialog.findViewById<Button>(R.id.btn_ndef_cancel)

        val recordAdapter = NdefRecordAdapter(builtRecords) { _, _ -> }
        rvRecords.layoutManager = LinearLayoutManager(activity)
        rvRecords.adapter = recordAdapter

        btnOk.text = activity.getString(android.R.string.ok)
        btnOk.setOnClickListener {
            onConfirm()
            dialog.dismiss()
        }
        btnCancel.setOnClickListener { dialog.dismiss() }

        val bytesSize = bytes.size
        tvRecordCount.text = activity.getString(
            R.string.build_ndef_record_status,
            builtRecords.size,
            bytesSize
        )

        if (builtRecords.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            rvRecords.visibility = View.GONE
        } else {
            tvEmpty.visibility = View.GONE
            rvRecords.visibility = View.VISIBLE
        }

        dialog.show()
    }

    private fun parseNdefMessage(bytes: ByteArray): List<BuiltRecord> {
        return try {
            val msg = NdefMessage(bytes)
            msg.records.mapNotNull { parseNdefRecord(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseNdefRecord(record: NdefRecord): BuiltRecord? {
        val tnf = record.tnf
        val type = record.type
        val payload = record.payload

        return when (tnf) {
            NdefRecord.TNF_WELL_KNOWN -> {
                if (type.contentEquals(NdefRecord.RTD_URI)) {
                    val uri = record.toUri()?.toString() ?: return null
                    when {
                        uri.startsWith("tel:") -> BuiltRecord(activity.getString(R.string.build_ndef_type_phone), uri.substring(4), record)
                        else -> BuiltRecord(activity.getString(R.string.build_ndef_type_website), uri, record)
                    }
                } else if (type.contentEquals(NdefRecord.RTD_TEXT)) {
                    if (payload.isEmpty()) return null
                    val langCodeLen = payload[0].toInt() and 0x3F
                    val text = String(payload, 1 + langCodeLen, payload.size - 1 - langCodeLen, Charsets.UTF_8)
                    BuiltRecord(activity.getString(R.string.build_ndef_type_text), text, record)
                } else {
                    BuiltRecord(activity.getString(R.string.build_ndef_type_unknown, String(type, Charsets.US_ASCII)), HexUtils.encodeHex(payload), record)
                }
            }
            NdefRecord.TNF_EXTERNAL_TYPE -> {
                val typeStr = String(type, Charsets.US_ASCII)
                if (typeStr == "android.com:pkg") {
                    BuiltRecord(activity.getString(R.string.build_ndef_type_aar), String(payload, Charsets.UTF_8), record)
                } else {
                    BuiltRecord(activity.getString(R.string.build_ndef_type_external), String.format("%s: %s", typeStr, HexUtils.encodeHex(payload)), record)
                }
            }
            NdefRecord.TNF_MIME_MEDIA -> {
                BuiltRecord(activity.getString(R.string.build_ndef_type_mime), String.format(Locale.getDefault(), "%s: %s", String(type, Charsets.US_ASCII), HexUtils.encodeHex(payload)), record)
            }
            NdefRecord.TNF_ABSOLUTE_URI -> {
                BuiltRecord(activity.getString(R.string.build_ndef_type_website), String(payload, Charsets.UTF_8), record)
            }
            else -> {
                BuiltRecord(activity.getString(R.string.build_ndef_type_unknown, String.format(Locale.getDefault(), "TNF:%d", tnf)), HexUtils.encodeHex(payload), record)
            }
        }
    }

    private fun buildNdefRecord(type: String, value: String): NdefRecord {
        return when (type) {
            activity.getString(R.string.build_ndef_type_website) -> {
                val url = if (
                    value.startsWith("http://", ignoreCase = true) ||
                    value.startsWith("https://", ignoreCase = true)
                ) {
                    value
                } else {
                    String.format(Locale.getDefault(), "https://%s", value)
                }
                NdefRecord.createUri(url)
            }

            activity.getString(R.string.build_ndef_type_phone) -> {
                val phone = value.replace(" ", "")
                NdefRecord.createUri(String.format(Locale.getDefault(), "tel:%s", phone))
            }

            activity.getString(R.string.build_ndef_type_aar) -> {
                NdefRecord.createApplicationRecord(value.trim())
            }

            activity.getString(R.string.build_ndef_type_mime) -> {
                val parts = value.split(":", limit = 2)
                if (parts.size == 2) {
                    NdefRecord.createMime(parts[0].trim(), HexUtils.decodeHex(parts[1].trim()))
                } else {
                    NdefRecord.createMime("application/octet-stream", HexUtils.decodeHex(value.trim()))
                }
            }

            activity.getString(R.string.build_ndef_type_external) -> {
                val parts = value.split(":", limit = 2)
                if (parts.size == 2) {
                    NdefRecord.createExternal("android.com", parts[0].trim(), HexUtils.decodeHex(parts[1].trim()))
                } else {
                    throw Exception("Format: type:hex_data")
                }
            }

            else -> {
                createTextRecord(getCurrentNdefTextLanguage(), value)
            }
        }
    }

    private fun buildNdefBytes(records: List<NdefRecord>): ByteArray {
        return NdefMessage(records.toTypedArray()).toByteArray()
    }

    private fun getCurrentNdefTextLanguage(): String {
        val language = Locale.getDefault().language
        return language.ifBlank { "en" }
    }

    private fun createTextRecord(language: String, text: String): NdefRecord {
        val languageBytes = language.toByteArray(Charsets.US_ASCII)
        val textBytes = text.toByteArray(Charsets.UTF_8)
        val payload = ByteArray(1 + languageBytes.size + textBytes.size)

        payload[0] = languageBytes.size.toByte()
        System.arraycopy(languageBytes, 0, payload, 1, languageBytes.size)
        System.arraycopy(textBytes, 0, payload, 1 + languageBytes.size, textBytes.size)

        return NdefRecord(
            NdefRecord.TNF_WELL_KNOWN,
            NdefRecord.RTD_TEXT,
            ByteArray(0),
            payload
        )
    }
}
