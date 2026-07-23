package mba.vm.onhit.core.tag.mfc

data class MifareClassicTag(
    val uid: ByteArray,
    val sectors: List<MifareClassicSector>,
    val atqa: ByteArray,
    val sak: Short
) {
    fun getBlockData(targetBlock: Int): ByteArray? {
        var currentTotal = 0
        for (sector in sectors) {
            val size = sector.dataBlocks.size + 1
            if (targetBlock < currentTotal + size) {
                val rel = targetBlock - currentTotal
                return if (rel < sector.dataBlocks.size) {
                    sector.dataBlocks[rel].data
                } else {
                    sector.trailerBlock.toMaskedByteArray()
                }
            }
            currentTotal += size
        }
        return null
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as MifareClassicTag
        if (!uid.contentEquals(other.uid)) return false
        if (sectors != other.sectors) return false
        if (!atqa.contentEquals(other.atqa)) return false
        if (sak != other.sak) return false
        return true
    }

    override fun hashCode(): Int {
        var result = uid.contentHashCode()
        result = 31 * result + sectors.hashCode()
        result = 31 * result + atqa.contentHashCode()
        result = 31 * result + sak.toInt()
        return result
    }
}
