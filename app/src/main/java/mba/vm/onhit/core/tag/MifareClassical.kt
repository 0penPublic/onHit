package mba.vm.onhit.core.tag

import android.os.Bundle
import mba.vm.onhit.core.TagTechnology
import mba.vm.onhit.core.mfc.MifareClassicSector
import mba.vm.onhit.core.mfc.MifareClassicalParser

class MifareClassical : BaseFakeTag() {
    override val name: String = this.javaClass.name
    var uid: ByteArray = byteArrayOf()
    var sectors: Array<MifareClassicSector> = arrayOf()
    var atqa: ByteArray = byteArrayOf(0x00, 0x04)
    var sak: Short = 0x08

    override fun init(uid: ByteArray, bytes: ByteArray): BaseFakeTag {
        this.uid = uid
        sectors = MifareClassicalParser.parse(bytes)
        if (sectors.isNotEmpty()) {
            val cardInfo = MifareClassicalParser.getTagInfo(sectors[0])
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
                return if (rel < sector.dataBlocks.size) sector.dataBlocks[rel].data else sector.trailerBlock
            }
            currentTotal += size
        }
        return null
    }

    fun authentication(cmd: ByteArray): ByteArray? {
        fun auth(sectorIndex: Int, isKeyB: Boolean, key: ByteArray): ByteArray? {
            val sector = sectors.getOrNull(sectorIndex) ?: return null
            val targetKey = if (isKeyB) {
                sector.trailerBlock.sliceArray(10..15)
            } else {
                sector.trailerBlock.sliceArray(0..5)
            }
            return if (key.contentEquals(targetKey)) {
                byteArrayOf(0x00)
            } else {
                null
            }
        }
        if (cmd.size < 8) return null
        val targetBlock = cmd[1].toInt() and 0xFF
        val sectorIndex = MifareClassicalParser.blockToSector(targetBlock)
        val providedKey = cmd.sliceArray(2..7)
        return auth(sectorIndex, cmd[0].toInt() == 0x60, providedKey)
    }

    fun transceive(req: ByteArray): ByteArray {
        if (req.isEmpty()) return byteArrayOf(0x00)
        val firstByte = req[0].toInt() and 0xFF
        when (firstByte) {
            0x60, 0x61 -> authentication(req)
            0x30 -> { // Read
                if (req.size < 2) return byteArrayOf(0x00)
                val targetBlock = req[1].toInt() and 0xFF
                return getBlockData(targetBlock) ?: ByteArray(16)
            }
        }
        return byteArrayOf(0x00)
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