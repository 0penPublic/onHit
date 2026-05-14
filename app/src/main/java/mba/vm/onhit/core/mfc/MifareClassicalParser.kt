package mba.vm.onhit.core.mfc

object MifareClassicalParser {
    const val MIFARE_CLASSICAL_BLOCK_SIZE = 16

    fun parse(fileBytes: ByteArray): Array<MifareClassicSector> {
        val sectors = mutableListOf<MifareClassicSector>()
        var byteOffset = 0

        while (byteOffset < fileBytes.size) {
            val sectorIndex = sectors.size
            val blocksInThisSector = if (sectorIndex < 32) 4 else 16
            val sectorSizeInBytes = blocksInThisSector * 16
            if (byteOffset + sectorSizeInBytes > fileBytes.size) {
                break
            }
            val sectorData = fileBytes.sliceArray(
                byteOffset until (byteOffset + sectorSizeInBytes)
            )
            val dataBlocks = Array(blocksInThisSector - 1) { i ->
                MifareClassicSector.MifareBlock(
                    sectorData.sliceArray(
                        i * MIFARE_CLASSICAL_BLOCK_SIZE until
                                (i + 1) * MIFARE_CLASSICAL_BLOCK_SIZE
                    )
                )
            }
            val trailerBlock = sectorData.sliceArray(
                (blocksInThisSector - 1) * MIFARE_CLASSICAL_BLOCK_SIZE until sectorSizeInBytes
            )
            sectors.add(
                MifareClassicSector(
                    dataBlocks,
                    trailerBlock
                )
            )
            byteOffset += sectorSizeInBytes
        }
        return sectors.toTypedArray()
    }

    fun getTagInfo(sector: MifareClassicSector): Pair<ByteArray?, Byte?> {
        if (sector.dataBlocks.isEmpty()) return null to null
        val block0 = sector.dataBlocks[0].data
        if (block0.size < 8) return null to null
        val sak = block0[5]
        val atqa = byteArrayOf(block0[6], block0[7])
        return atqa to sak
    }
}