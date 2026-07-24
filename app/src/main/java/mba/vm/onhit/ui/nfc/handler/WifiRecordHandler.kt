package mba.vm.onhit.ui.nfc.handler

import android.content.Context
import android.nfc.NdefRecord
import mba.vm.onhit.R
import mba.vm.onhit.ui.model.BuiltRecord
import mba.vm.onhit.ui.nfc.handler.wifi.NdefWifiHelper
import mba.vm.onhit.ui.nfc.handler.wifi.WiFiData

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
}
