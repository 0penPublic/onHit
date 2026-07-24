package mba.vm.onhit.ui.nfc

import android.content.Context
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import mba.vm.onhit.ui.model.BuiltRecord
import mba.vm.onhit.ui.nfc.handler.*

class NdefEditor(context: Context) {
    private val handlers = listOf(
        UriRecordHandler(context),
        TextRecordHandler(context),
        WifiRecordHandler(context),
        AarRecordHandler(context),
        MimeRecordHandler(context),
        ExternalRecordHandler(context)
    )
    private val fallbackHandler = UnknownRecordHandler(context)

    fun parseNdefMessage(bytes: ByteArray): List<BuiltRecord> {
        return try {
            val msg = NdefMessage(bytes)
            msg.records.mapNotNull { record ->
                val handler = handlers.find { it.canHandle(record) } ?: fallbackHandler
                handler.parse(record)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun buildNdefRecord(
        type: String, 
        value: String, 
        lang: String? = null, 
        payload: ByteArray? = null,
        existingRecord: NdefRecord? = null
    ): NdefRecord {
        val handler = handlers.find { it.canHandle(type) }
        return handler?.build(value, lang, payload, existingRecord)
            ?: fallbackHandler.build(
                value,
                lang,
                payload,
                existingRecord
            )
    }

    fun buildNdefBytes(records: List<NdefRecord>): ByteArray {
        return NdefMessage(records.toTypedArray()).toByteArray()
    }

    fun getHandler(type: String): NdefRecordHandler? {
        return handlers.find { it.canHandle(type) }
    }
}
