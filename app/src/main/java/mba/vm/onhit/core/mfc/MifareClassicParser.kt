package mba.vm.onhit.core.mfc

import android.nfc.NdefMessage
import android.util.Log
import mba.vm.onhit.Constant.Companion.MIFARE_CLASSICAL_BLOCK_SIZE
import mba.vm.onhit.utils.HexUtils.encodeHex
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

object MifareClassicParser {
    private const val TAG_NULL = 0x00
    private const val TAG_NDEF = 0x03
    private const val TAG_TERMINATOR = 0xFE

    val KEY_MAD_NFC_FORUM = byteArrayOf(0xA0.toByte(), 0xA1.toByte(), 0xA2.toByte(), 0xA3.toByte(), 0xA4.toByte(), 0xA5.toByte())
    val KEY_NDEF_APPLICATION = byteArrayOf(0xD3.toByte(), 0xF7.toByte(), 0xD3.toByte(), 0xF7.toByte(), 0xD3.toByte(), 0xF7.toByte())

    fun checkNdef(sectors: List<MifareClassicSector>): NdefMessage? {
        if (sectors.isEmpty()) return null
        val allDataBytes = ByteArrayOutputStream()
        for ((sectorIndex, sector) in sectors.withIndex()) {
            val expectedKey = if (sectorIndex == 0) KEY_MAD_NFC_FORUM else KEY_NDEF_APPLICATION
            if (!sector.trailerBlock.keyA.contentEquals(expectedKey)) return null
            if (sectorIndex == 0) continue
            for (block in sector.dataBlocks) {
                allDataBytes.write(block.data)
            }
        }
        return parseNdefFromRawBytes(allDataBytes.toByteArray())
    }

    private fun parseNdefFromRawBytes(rawBytes: ByteArray): NdefMessage? {
        return runCatching {
            var index = 0
            var ndefBytes: ByteArray? = null

            while (index < rawBytes.size) {
                val tag = rawBytes[index].toInt() and 0xFF
                when (tag) {
                    TAG_NULL -> index++
                    TAG_TERMINATOR -> break
                    TAG_NDEF -> {
                        index++
                        if (index >= rawBytes.size) break
                        var length = rawBytes[index].toInt() and 0xFF
                        index++
                        if (length == 0xFF) {
                            if (index + 1 >= rawBytes.size) break
                            val lenH = rawBytes[index].toInt() and 0xFF
                            val lenL = rawBytes[index + 1].toInt() and 0xFF
                            length = (lenH shl 8) or lenL
                            index += 2
                        }
                        if (index + length <= rawBytes.size) ndefBytes = rawBytes.copyOfRange(index, index + length)
                        break
                    }
                    else -> {
                        index++
                        if (index >= rawBytes.size) break
                        val length = rawBytes[index].toInt() and 0xFF
                        index += 1 + length
                    }
                }
            }
            ndefBytes?.let {
                Log.i("MifareClassicParser", "NDEF Data: ${encodeHex(it)}")
                NdefMessage(it)
            }
        }.onFailure { e ->
            Log.e("MifareClassicParser", "Parsing NDEF failed", e)
        }.getOrNull()
    }

    fun parse(fileBytes: ByteArray): List<MifareClassicSector> {
        val sectors = mutableListOf<MifareClassicSector>()
        val buffer = ByteBuffer.wrap(fileBytes)

        while (buffer.hasRemaining()) {
            val sectorIndex = sectors.size
            val blocksInThisSector = if (sectorIndex < 32) 4 else 16
            val sectorSizeInBytes = blocksInThisSector * MIFARE_CLASSICAL_BLOCK_SIZE

            if (buffer.remaining() < sectorSizeInBytes) break
            val dataBlocks = List(blocksInThisSector - 1) {
                val blockBytes = ByteArray(MIFARE_CLASSICAL_BLOCK_SIZE)
                buffer.get(blockBytes)
                MifareClassicSector.MifareBlock(blockBytes)
            }
            val trailerBytes = ByteArray(MIFARE_CLASSICAL_BLOCK_SIZE)
            buffer.get(trailerBytes)
            val trailerBlock = MifareClassicSector.MifareTrailerBlock.fromByteArray(trailerBytes)
            sectors.add(MifareClassicSector(dataBlocks, trailerBlock))
        }
        return sectors
    }

    fun getTagInfo(sector: MifareClassicSector): Pair<ByteArray?, Byte?> {
        val block0 = sector.dataBlocks.getOrNull(0)?.data ?: return null to null
        if (block0.size < 8) return null to null

        val sak = block0[5]
        val atqa = byteArrayOf(block0[6], block0[7])
        return atqa to sak
    }

    fun blockToSector(blockIndex: Int): Int {
        if (blockIndex !in 0..255) return -1
        return if (blockIndex < 128) {
            blockIndex ushr 2
        } else {
            32 + ((blockIndex - 128) ushr 4)
        }
    }

    val List<MifareClassicSector>.maxNdefMessageSize: Int
        get() {
            if (this.size < 2) return 0
            val totalNdefPhysicalBytes = this.subList(1, this.size).sumOf { sector ->
                sector.dataBlocks.size * MIFARE_CLASSICAL_BLOCK_SIZE
            }
            if (totalNdefPhysicalBytes == 0) return 0
            val tlvHeaderExpense = 4
            val maxPayload = totalNdefPhysicalBytes - tlvHeaderExpense
            return if (maxPayload > 0) maxPayload else 0
        }
}