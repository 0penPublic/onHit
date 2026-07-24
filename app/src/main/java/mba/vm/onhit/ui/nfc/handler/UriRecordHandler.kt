package mba.vm.onhit.ui.nfc.handler

import android.content.Context
import android.nfc.NdefRecord
import mba.vm.onhit.R
import mba.vm.onhit.ui.model.BuiltRecord

class UriRecordHandler(context: Context) : NdefRecordHandler(context) {
    override fun canHandle(record: NdefRecord): Boolean {
        return record.tnf == NdefRecord.TNF_WELL_KNOWN && 
               record.type.contentEquals(NdefRecord.RTD_URI)
    }

    override fun canHandle(type: String): Boolean {
        return type == context.getString(R.string.build_ndef_type_website)
    }

    override fun parse(record: NdefRecord): BuiltRecord? {
        val payload = record.payload
        if (payload.isEmpty()) return null
        
        val prefixCode = payload[0].toInt() and 0xFF
        val prefix = URI_PREFIX_MAP[prefixCode] ?: ""
        val content = String(payload, 1, payload.size - 1, Charsets.UTF_8)
        val fullUri = prefix + content
        
        return BuiltRecord(
            type = context.getString(R.string.build_ndef_type_website),
            value = fullUri,
            record = record
        )
    }

    override fun build(value: String, lang: String?, payload: ByteArray?, existingRecord: NdefRecord?): NdefRecord {
        val uri = if (value.isBlank()) "https://" else value
        return NdefRecord.createUri(uri)
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
