package mba.vm.onhit.ui.nfc.handler

import android.content.Context
import android.nfc.NdefRecord
import android.view.ViewGroup
import mba.vm.onhit.model.BuiltRecord

abstract class NdefRecordHandler(protected val context: Context) {
    abstract fun canHandle(record: NdefRecord): Boolean
    abstract fun canHandle(type: String): Boolean
    abstract fun parse(record: NdefRecord): BuiltRecord?
    abstract fun build(value: String, lang: String?, payload: ByteArray?, existingRecord: NdefRecord? = null): NdefRecord

    abstract fun getLayoutId(type: String): Int

    abstract fun bindView(
        container: ViewGroup,
        record: BuiltRecord,
        type: String,
        onUpdate: () -> Unit,
        onPickFile: () -> Unit,
        onExportPayload: (String) -> Unit
    )

    abstract fun updateRecordFromUI(
        container: ViewGroup,
        oldRecord: BuiltRecord,
        type: String,
        buildNdefRecord: (String, String, String?, ByteArray?) -> NdefRecord
    ): BuiltRecord

    open fun getSummary(record: BuiltRecord): String {
        return record.value
    }
}
