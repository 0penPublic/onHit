package mba.vm.onhit.ui.nfc.handler

import android.content.Context
import android.nfc.NdefRecord
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Spinner
import androidx.core.widget.doAfterTextChanged
import mba.vm.onhit.R
import mba.vm.onhit.model.BuiltRecord
import mba.vm.onhit.ui.helper.NdefWifiHelper
import mba.vm.onhit.model.WiFiData

class WifiRecordHandler(context: Context) : NdefRecordHandler(context) {
    override fun canHandle(record: NdefRecord): Boolean {
        val typeStr = String(record.type, Charsets.US_ASCII)
        return record.tnf == NdefRecord.TNF_MIME_MEDIA && typeStr == "application/vnd.wfa.wsc"
    }

    override fun canHandle(type: String): Boolean {
        return type == context.getString(R.string.build_ndef_type_wifi)
    }

    override fun parse(record: NdefRecord): BuiltRecord? {
        val wifiData = try { NdefWifiHelper.parsePayload(record.payload) } catch(_: Exception) { null }
        if (wifiData != null) {
            return BuiltRecord(
                type = context.getString(R.string.build_ndef_type_wifi),
                value = "${wifiData.ssid}\n${wifiData.password}\n${wifiData.security}\n${wifiData.encryption}",
                record = record,
                payload = record.payload
            )
        }
        return null
    }

    override fun build(value: String, lang: String?, payload: ByteArray?, existingRecord: NdefRecord?): NdefRecord {
        val lines = value.split("\n")
        val wifiData = WiFiData(
            lines.getOrNull(0) ?: "",
            lines.getOrNull(1) ?: "",
            lines.getOrNull(2) ?: "None",
            lines.getOrNull(3) ?: "None"
        )
        return NdefRecord.createMime("application/vnd.wfa.wsc", NdefWifiHelper.buildPayload(wifiData))
    }

    override fun getLayoutId(type: String): Int = R.layout.layout_ndef_input_wifi

    override fun bindView(
        container: ViewGroup,
        record: BuiltRecord,
        type: String,
        onUpdate: () -> Unit,
        onPickFile: () -> Unit,
        onExportPayload: (String) -> Unit
    ) {
        val lines = record.value.split("\n")
        val etSsid = container.findViewById<EditText>(R.id.et_ndef_wifi_ssid)
        val etPassword = container.findViewById<EditText>(R.id.et_ndef_wifi_password)
        val btnToggle = container.findViewById<ImageButton>(R.id.btn_toggle_password)

        etSsid?.setText(lines.getOrNull(0) ?: "")
        etPassword?.setText(lines.getOrNull(1) ?: "")

        btnToggle?.setOnClickListener {
            if (etPassword.inputType == (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)) {
                etPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                btnToggle.setImageResource(R.drawable.baseline_password_show_24)
            } else {
                etPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                btnToggle.setImageResource(R.drawable.baseline_password_hide_24)
            }
            etPassword.setSelection(etPassword.text.length)
        }
        
        if (etPassword != null) {
            if (etPassword.inputType == (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)) {
                btnToggle?.setImageResource(R.drawable.baseline_password_show_24)
            } else {
                btnToggle?.setImageResource(R.drawable.baseline_password_hide_24)
            }
        }

        val spinnerSec = container.findViewById<Spinner>(R.id.spinner_ndef_wifi_sec)
        val spinnerEnc = container.findViewById<Spinner>(R.id.spinner_ndef_wifi_enc)

        if (spinnerSec != null) {
            val secNames = NdefWifiHelper.authModes.map { it.first }
            spinnerSec.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, secNames).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            spinnerSec.setSelection(secNames.indexOf(lines.getOrNull(2) ?: "None").coerceAtLeast(0))
            spinnerSec.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                    onUpdate()
                }
                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }
        }

        if (spinnerEnc != null) {
            val encNames = NdefWifiHelper.encryptionModes.map { it.first }
            spinnerEnc.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, encNames).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            spinnerEnc.setSelection(encNames.indexOf(lines.getOrNull(3) ?: "None").coerceAtLeast(0))
            spinnerEnc.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                    onUpdate()
                }
                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }
        }

        etSsid?.doAfterTextChanged { onUpdate() }
        etPassword?.doAfterTextChanged { onUpdate() }
    }

    override fun updateRecordFromUI(
        container: ViewGroup,
        oldRecord: BuiltRecord,
        type: String,
        buildNdefRecord: (String, String, String?, ByteArray?) -> NdefRecord
    ): BuiltRecord {
        val ssid = container.findViewById<EditText>(R.id.et_ndef_wifi_ssid)?.text?.toString() ?: ""
        val password = container.findViewById<EditText>(R.id.et_ndef_wifi_password)?.text?.toString() ?: ""
        val security = container.findViewById<Spinner>(R.id.spinner_ndef_wifi_sec)?.selectedItem?.toString() ?: "None"
        val encryption = container.findViewById<Spinner>(R.id.spinner_ndef_wifi_enc)?.selectedItem?.toString() ?: "None"
        val value = "$ssid\n$password\n$security\n$encryption"
        
        val newNdefRecord = try {
            buildNdefRecord(type, value, null, null)
        } catch (_: Exception) {
            oldRecord.record
        }

        return BuiltRecord(type, value, newNdefRecord, null, null)
    }

    override fun getSummary(record: BuiltRecord): String {
        val lines = record.value.split("\n")
        val ssid = lines.getOrNull(0) ?: ""
        val password = lines.getOrNull(1) ?: ""
        val maskedPassword = "*".repeat(password.length.coerceAtMost(12).coerceAtLeast(4))
        return if (ssid.isNotEmpty()) "$ssid ($maskedPassword)" else maskedPassword
    }
}
