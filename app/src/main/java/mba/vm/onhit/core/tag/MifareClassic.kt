package mba.vm.onhit.core.tag

import android.os.Bundle
import mba.vm.onhit.core.TagTechnology
import mba.vm.onhit.core.mfc.MifareClassicSector
import mba.vm.onhit.core.mfc.MifareClassicParser

// WIP
class MifareClassic : BaseFakeTag() {
    var uid: ByteArray = byteArrayOf()
    var sectors: Array<MifareClassicSector> = arrayOf()
    var atqa: ByteArray = byteArrayOf(0x00, 0x04)
    var sak: Short = 0x08
    var currentUnlockSectorIndex: Int = -1

    override fun init(uid: ByteArray, bytes: ByteArray): BaseFakeTag {
        this.uid = uid
        sectors = MifareClassicParser.parse(bytes)
        if (sectors.isNotEmpty()) {
            val cardInfo = MifareClassicParser.getTagInfo(sectors[0])
            atqa = cardInfo.first ?: byteArrayOf(0x00, 0x04)
            sak = cardInfo.second?.toShort() ?: 0x08
        }
        return this
    }

    private fun getBlockData(targetBlock: Int): ByteArray? {
        var currentTotal = 0
        for (sector in sectors) {
            val size = sector.dataBlocks.size + 1
            if (targetBlock < currentTotal + size) {
                val rel = targetBlock - currentTotal
                if (rel < sector.dataBlocks.size) {
                    return sector.dataBlocks[rel].data
                } else {
                    val rawTrailer = sector.trailerBlock
                    if (rawTrailer.size < 16) return rawTrailer
                    val maskedTrailer = ByteArray(16)
                    System.arraycopy(rawTrailer, 6, maskedTrailer, 6, 4)
                    System.arraycopy(rawTrailer, 10, maskedTrailer, 10, 6)
                    return maskedTrailer
                }
            }
            currentTotal += size
        }
        return null
    }

    fun authentication(cmd: ByteArray): Pair<Boolean, ByteArray> {
        currentUnlockSectorIndex = -1
        fun auth(sectorIndex: Int, isKeyB: Boolean, key: ByteArray): Pair<Boolean, ByteArray> {
            val sector = sectors.getOrNull(sectorIndex) ?: return Pair(false, byteArrayOf())
            val targetKey = if (isKeyB) {
                sector.trailerBlock.sliceArray(10..15)
            } else {
                sector.trailerBlock.sliceArray(0..5)
            }
            return if (key.contentEquals(targetKey)) {
                currentUnlockSectorIndex = sectorIndex
                Pair(true, byteArrayOf(0x00))
            } else {
                Pair(false, byteArrayOf())
            }
        }
        if (cmd.size < 12) return Pair(false, byteArrayOf())
        val targetBlock = cmd[1].toInt() and 0xFF
        val sectorIndex = MifareClassicParser.blockToSector(targetBlock)
        val providedKey = cmd.sliceArray(6..11)
        return auth(sectorIndex, (cmd[0].toInt() and 0xFF) == 0x61, providedKey)
    }

    fun transceive(req: ByteArray): Pair<Boolean, ByteArray> {
        if (req.isEmpty()) return Pair(false, byteArrayOf())
        val firstByte = req[0].toInt() and 0xFF
        when (firstByte) {
            0x60, 0x61 -> return authentication(req)
            0x30 -> {
                if (req.size < 2) return Pair(false, byteArrayOf())
                val targetBlock = req[1].toInt() and 0xFF
                if (currentUnlockSectorIndex != MifareClassicParser.blockToSector(targetBlock)) {
                    return Pair(false, byteArrayOf())
                }
                val blockData = getBlockData(targetBlock)
                return if (blockData != null) {
                    Pair(true, blockData)
                } else {
                    Pair(false, byteArrayOf())
                }
            }
        }
        return Pair(true, byteArrayOf())
    }

    override fun makeEndpoint(
        nfcClassloader: ClassLoader,
        tagEndpointInterface: Class<*>
    ): Any {
        return createTagEndpoint(
            nfcClassloader,
            tagEndpointInterface,
            uid,
            TagTechnology.arrayOfTagTechnology(
                TagTechnology.NFC_A,
                TagTechnology.MIFARE_CLASSIC
            ),
            arrayOf(
                Bundle().apply {
                    putShort("sak", sak)
                    putByteArray("atqa", atqa)
                },
                Bundle()
            ),
            ::transceive
        )
    }
}