package mba.vm.onhit.core.tag

enum class TagType(val value: Byte) {
    NDEF(0x01),
    MFC(0x02),
    TRACE(0x03);

    companion object {
        fun fromByte(value: Byte): TagType = entries.find { it.value == value } ?: NDEF
    }
}
