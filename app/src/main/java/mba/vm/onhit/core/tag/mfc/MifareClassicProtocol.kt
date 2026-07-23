package mba.vm.onhit.core.tag.mfc

class MifareClassicProtocol(private val tag: MifareClassicTag) {
    private var currentUnlockSectorIndex: Int = -1

    fun processCommand(data: ByteArray, returnCode: IntArray): ByteArray? {
        returnCode[0] = 0
        if (data.isEmpty()) return null
        val firstByte = data[0].toInt() and 0xFF

        return when (firstByte) {
            0x60, 0x61 -> authentication(data)
            0x30 -> readBlock(data)
            else -> null
        }
    }

    private fun authentication(cmd: ByteArray): ByteArray? {
        currentUnlockSectorIndex = -1
        if (cmd.size < 12) return null
        val targetBlock = cmd[1].toInt() and 0xFF
        val sectorIndex = MifareClassicParser.blockToSector(targetBlock)
        val providedKey = cmd.sliceArray(6..11)
        val isKeyB = (cmd[0].toInt() and 0xFF) == 0x61
        val sector = tag.sectors.getOrNull(sectorIndex) ?: return null
        val targetKey = if (isKeyB) sector.trailerBlock.keyB else sector.trailerBlock.keyA
        return if (providedKey.contentEquals(targetKey)) {
            currentUnlockSectorIndex = sectorIndex
            byteArrayOf(0x00)
        } else {
            null
        }
    }

    private fun readBlock(data: ByteArray): ByteArray? {
        if (data.size < 2) return null
        val targetBlock = data[1].toInt() and 0xFF
        if (currentUnlockSectorIndex != MifareClassicParser.blockToSector(targetBlock)) return null
        return tag.getBlockData(targetBlock)
    }
}
