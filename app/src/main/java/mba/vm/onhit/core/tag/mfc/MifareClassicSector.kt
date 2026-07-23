package mba.vm.onhit.core.tag.mfc

import mba.vm.onhit.Constant.Companion.MIFARE_CLASSICAL_BLOCK_SIZE


data class MifareClassicSector(
    val dataBlocks: List<MifareDataBlock>,
    val trailerBlock: MifareTrailerBlock
) {
    data class MifareTrailerBlock(
        val keyA: ByteArray = ByteArray(6) { 0xFF.toByte() },
        val accessBits: ByteArray = byteArrayOf(0xFF.toByte(), 0x07.toByte(), 0x80.toByte()),
        val userData: Byte = 0x69.toByte(),
        val keyB: ByteArray = ByteArray(6) { 0xFF.toByte() }
    ) {
        init {
            require(keyA.size == 6) { "Key A must be 6 bytes" }
            require(accessBits.size == 3) { "Access Bits must be 3 bytes" }
            require(keyB.size == 6) { "Key B must be 6 bytes" }
        }

        @Suppress("unused")
        fun toByteArray(): ByteArray {
            val result = ByteArray(MIFARE_CLASSICAL_BLOCK_SIZE)
            System.arraycopy(keyA, 0, result, 0, 6)
            System.arraycopy(accessBits, 0, result, 6, 3)
            result[9] = userData
            System.arraycopy(keyB, 0, result, 10, 6)
            return result
        }

        fun toMaskedByteArray(): ByteArray {
            val result = ByteArray(MIFARE_CLASSICAL_BLOCK_SIZE)
            System.arraycopy(accessBits, 0, result, 6, 3)
            result[9] = userData
            return result
        }

        companion object {
            fun fromByteArray(bytes: ByteArray): MifareTrailerBlock {
                require(bytes.size == MIFARE_CLASSICAL_BLOCK_SIZE) { "Trailer block bytes must be $MIFARE_CLASSICAL_BLOCK_SIZE bytes" }
                return MifareTrailerBlock(
                    keyA = bytes.copyOfRange(0, 6),
                    accessBits = bytes.copyOfRange(6, 9),
                    userData = bytes[9],
                    keyB = bytes.copyOfRange(10, 16)
                )
            }
        }
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as MifareTrailerBlock
            return userData == other.userData &&
                    keyA.contentEquals(other.keyA) &&
                    accessBits.contentEquals(other.accessBits) &&
                    keyB.contentEquals(other.keyB)
        }

        override fun hashCode(): Int {
            var result = keyA.contentHashCode()
            result = 31 * result + accessBits.contentHashCode()
            result = 31 * result + userData
            result = 31 * result + keyB.contentHashCode()
            return result
        }
    }

    @JvmInline
    value class MifareDataBlock(val data: ByteArray) {
        init {
            require(data.size == MIFARE_CLASSICAL_BLOCK_SIZE) { "Block size must be $MIFARE_CLASSICAL_BLOCK_SIZE" }
        }
    }

    init {
        val dataBlkSize = dataBlocks.size
        require(dataBlkSize == 3 || dataBlkSize == 15) { "Invalid data blocks size: $dataBlkSize" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as MifareClassicSector

        if (trailerBlock != other.trailerBlock) return false
        if (dataBlocks.size != other.dataBlocks.size) return false
        for (i in dataBlocks.indices) {
            if (!dataBlocks[i].data.contentEquals(other.dataBlocks[i].data)) return false
        }
        return true
    }

    override fun hashCode(): Int {
        var result = trailerBlock.hashCode()
        for (block in dataBlocks) {
            result = 31 * result + block.data.contentHashCode()
        }
        return result
    }
}
