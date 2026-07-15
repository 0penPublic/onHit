package mba.vm.onhit.core.mfc

import android.nfc.NdefMessage
import android.util.Log
import mba.vm.onhit.utils.HexUtils.encodeHex
import java.io.ByteArrayOutputStream

object MifareClassicParser {
    const val MIFARE_CLASSICAL_BLOCK_SIZE = 16
    val KEY_MAD_NFC_FORUM = byteArrayOf(0xA0.toByte(), 0xA1.toByte(), 0xA2.toByte(), 0xA3.toByte(), 0xA4.toByte(), 0xA5.toByte())
    val KEY_NDEF_APPLICATION = byteArrayOf(0xD3.toByte(), 0xF7.toByte(), 0xD3.toByte(), 0xF7.toByte(), 0xD3.toByte(), 0xF7.toByte())

    fun checkNdef(sectors: Array<MifareClassicSector>): NdefMessage? {
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
                    0x00 -> index++
                    0xFE -> break
                    0x03 -> {
                        index++
                        var length = rawBytes[index].toInt() and 0xFF
                        index++
                        if (length == 0xFF) {
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
                        val length = rawBytes[index].toInt() and 0xFF
                        index += 1 + length
                    }
                }
            }
            ndefBytes?.let {
                Log.i(this::class.simpleName, "NDEF Data: ${encodeHex(it)}")
                NdefMessage(it)
            }
        }.onFailure { e ->
            Log.e(this::class.simpleName, "Parsing NDEF failed", e)
        }.getOrNull()
    }
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
                    MifareClassicSector.MifareTrailerBlock.fromByteArray(trailerBlock)
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

    fun blockToSector(blockIndex: Int): Int {
        if (blockIndex !in 0..<256) return -1
        return if (blockIndex < 32 * 4) blockIndex / 4
        else 32 + (blockIndex - 32 * 4) / 16
    }

    val Array<MifareClassicSector>.maxNdefMessageSize: Int
        get() {
            if (this.size < 2) return 0
            val ndefSectors = this.drop(1)
            val totalNdefPhysicalBytes = ndefSectors.sumOf { sector ->
                sector.dataBlocks.size * MIFARE_CLASSICAL_BLOCK_SIZE
            }
            if (totalNdefPhysicalBytes == 0) return 0
            val tlvHeaderExpense = 4
            val maxPayload = totalNdefPhysicalBytes - tlvHeaderExpense
            return if (maxPayload > 0) maxPayload else 0
        }
}