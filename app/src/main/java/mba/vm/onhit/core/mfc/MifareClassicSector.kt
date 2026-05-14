package mba.vm.onhit.core.mfc

import mba.vm.onhit.core.mfc.MifareClassicalParser.MIFARE_CLASSICAL_BLOCK_SIZE


data class MifareClassicSector(
    var dataBlocks: Array<MifareBlock>,
    var trailerBlock: ByteArray
) {
    @JvmInline
    value class MifareBlock(val data: ByteArray) {
        init {
            require(data.size == MIFARE_CLASSICAL_BLOCK_SIZE) { "Block size must be 16" }
        }
    }

    init {
        val dataBlkSize = dataBlocks.size
        require(dataBlkSize == 3 || dataBlkSize == 15) { "Invalid data blocks size: $dataBlkSize" }
        require(dataBlocks.all { it.data.size == MIFARE_CLASSICAL_BLOCK_SIZE }) { "Each data block must be 16 bytes" }
        require(trailerBlock.size == 16) { "Trailer block must be 16 bytes" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MifareClassicSector

        if (!dataBlocks.contentDeepEquals(other.dataBlocks)) return false
        if (!trailerBlock.contentEquals(other.trailerBlock)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = dataBlocks.contentDeepHashCode()
        result = 31 * result + trailerBlock.contentHashCode()
        return result
    }
}
