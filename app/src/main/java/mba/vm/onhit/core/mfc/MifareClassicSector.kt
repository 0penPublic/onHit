package mba.vm.onhit.core.mfc

import mba.vm.onhit.core.mfc.MifareClassicParser.MIFARE_CLASSICAL_BLOCK_SIZE

data class MifareClassicSector(
    var dataBlocks: Array<MifareBlock>,
    var trailerBlock: MifareTrailerBlock
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

        fun toByteArray(): ByteArray {
            val result = ByteArray(16)
            System.arraycopy(keyA, 0, result, 0, 6)
            System.arraycopy(accessBits, 0, result, 6, 3)
            result[9] = userData
            System.arraycopy(keyB, 0, result, 10, 6)
            return result
        }

        fun toMaskedByteArray(): ByteArray {
            val raw = this.toByteArray()
            for (i in 0 until 6) raw[i] = 0.toByte()
            for (i in 10 until 16) raw[i] = 0.toByte()
            return raw
        }

        companion object {
            fun fromByteArray(bytes: ByteArray): MifareTrailerBlock {
                require(bytes.size == 16) { "Trailer block bytes must be 16 bytes" }
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

            if (!keyA.contentEquals(other.keyA)) return false
            if (!accessBits.contentEquals(other.accessBits)) return false
            if (userData != other.userData) return false
            if (!keyB.contentEquals(other.keyB)) return false

            return true
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
    value class MifareBlock(val data: ByteArray) {
        init {
            require(data.size == MIFARE_CLASSICAL_BLOCK_SIZE) { "Block size must be 16" }
        }
    }

    init {
        val dataBlkSize = dataBlocks.size
        require(dataBlkSize == 3 || dataBlkSize == 15) { "Invalid data blocks size: $dataBlkSize" }
        require(dataBlocks.all { it.data.size == MIFARE_CLASSICAL_BLOCK_SIZE }) { "Each data block must be 16 bytes" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MifareClassicSector

        if (!dataBlocks.contentDeepEquals(other.dataBlocks)) return false
        if (trailerBlock != other.trailerBlock) return false

        return true
    }

    override fun hashCode(): Int {
        var result = dataBlocks.contentDeepHashCode()
        result = 31 * result + trailerBlock.hashCode()
        return result
    }
}
