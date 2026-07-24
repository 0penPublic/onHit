package mba.vm.onhit.ui.model

import android.nfc.NdefRecord

data class BuiltRecord(
    val type: String,
    val value: String,
    val record: NdefRecord,
    val lang: String? = null,
    val payload: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BuiltRecord

        if (type != other.type) return false
        if (value != other.value) return false
        if (record != other.record) return false
        if (lang != other.lang) return false
        if (payload != null) {
            if (other.payload == null) return false
            if (!payload.contentEquals(other.payload)) return false
        } else if (other.payload != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + value.hashCode()
        result = 31 * result + record.hashCode()
        result = 31 * result + (lang?.hashCode() ?: 0)
        result = 31 * result + (payload?.contentHashCode() ?: 0)
        return result
    }
}
