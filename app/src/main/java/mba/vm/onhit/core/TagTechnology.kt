package mba.vm.onhit.core

@Suppress("unused")
enum class TagTechnology(val flag: Int) {
    NFC_A(1),
    NFC_B(2),
    ISO_DEP(3),
    NFC_F(4),
    NFC_V(5),
    NDEF(6),
    NDEF_FORMATABLE(7),
    MIFARE_CLASSIC(8),
    MIFARE_ULTRALIGHT(9),
    NFC_BARCODE(10);

    fun toFlag(): Int = flag
    companion object {
        fun arrayOfTagTechnology(vararg tagTechnologies: TagTechnology): IntArray {
            return tagTechnologies.map { it.flag }.toIntArray()
        }
    }
}
