package mba.vm.onhit.core.tag

import android.nfc.NdefMessage
import android.os.Bundle
import mba.vm.onhit.core.TagTechnology
import mba.vm.onhit.core.mfc.MifareClassicParser
import mba.vm.onhit.core.mfc.MifareClassicParser.checkNdef
import mba.vm.onhit.core.mfc.MifareClassicParser.maxNdefMessageSize
import mba.vm.onhit.core.mfc.MifareClassicSector

class MifareClassic : BaseFakeTag() {
    var uid: ByteArray = byteArrayOf()
    var sectors: List<MifareClassicSector> = listOf()
    var atqa: ByteArray = byteArrayOf(0x00, 0x04)
    var sak: Short = 0x08
    var ndef: NdefMessage? = null
    var techs: MutableList<TagTechnology> = mutableListOf(TagTechnology.NFC_A, TagTechnology.MIFARE_CLASSIC)
    var extras: MutableList<Bundle> = mutableListOf(
        Bundle().apply {
            putShort("sak", sak)
            putByteArray("atqa", atqa)
        },
        Bundle()
    )
    var currentUnlockSectorIndex: Int = -1

    /**
     * Initializes the fake tag using raw binary dump bytes from a Proxmark3 (pm3) Mifare Classic export.
     *
     * NOTE: The configured [uid] might differ from the first 4 bytes of Block 0 (the manufacturer block).
     */
    override fun init(uid: ByteArray, bytes: ByteArray): BaseFakeTag {
        this.uid = uid
        sectors = MifareClassicParser.parse(bytes)
        if (sectors.isNotEmpty()) {
            val cardInfo = MifareClassicParser.getTagInfo(sectors[0])
            atqa = cardInfo.first ?: byteArrayOf(0x00, 0x04)
            sak = cardInfo.second?.toShort() ?: 0x08
        }
        ndef = checkNdef(sectors)
        ndef?.let {
            techs.add(TagTechnology.NDEF)
            extras.add(
                Bundle().apply {
                    putParcelable("ndefmsg", it)
                    putInt("ndefmaxlength", sectors.maxNdefMessageSize)
                    putInt("ndefcardstate", 4)
                    putInt("ndeftype", 1)
                }
            )
        }
        return this
    }

    private fun getBlockData(targetBlock: Int): ByteArray? {
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

    fun authentication(cmd: ByteArray): ByteArray? {
        currentUnlockSectorIndex = -1
        if (cmd.size < 12) return null
        val targetBlock = cmd[1].toInt() and 0xFF
        val sectorIndex = MifareClassicParser.blockToSector(targetBlock)
        val providedKey = cmd.sliceArray(6..11)
        val isKeyB = (cmd[0].toInt() and 0xFF) == 0x61
        val sector = sectors.getOrNull(sectorIndex) ?: return null
        val targetKey = if (isKeyB) sector.trailerBlock.keyB else sector.trailerBlock.keyA
        return if (providedKey.contentEquals(targetKey)) {
            currentUnlockSectorIndex = sectorIndex
            byteArrayOf(0x00)
        } else {
            null
        }
    }

    fun transceive(req: ByteArray): ByteArray? {
        // I don't think we need write functionality; this is just a read-only PoC. I guess.
        if (req.isEmpty()) return null
        val firstByte = req[0].toInt() and 0xFF

        when (firstByte) {
            0x60, 0x61 -> return authentication(req)
            0x30 -> {
                if (req.size < 2) return null
                val targetBlock = req[1].toInt() and 0xFF
                if (currentUnlockSectorIndex != MifareClassicParser.blockToSector(targetBlock)) return null
                val blockData = getBlockData(targetBlock)
                return blockData
            }
        }
        return null
    }

    override fun makeEndpoint(
        nfcClassloader: ClassLoader,
        tagEndpointInterface: Class<*>
    ): Any = createTagEndpoint(
        nfcClassloader,
        tagEndpointInterface,
        uid,
        TagTechnology.arrayOfTagTechnology(*techs.toTypedArray()),
        extras.toTypedArray(),
        ::transceive,
        ndef = ndef
    )
}