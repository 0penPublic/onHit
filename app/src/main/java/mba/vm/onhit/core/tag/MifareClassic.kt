package mba.vm.onhit.core.tag

import android.os.Bundle
import mba.vm.onhit.core.tag.mfc.MifareClassicParser
import mba.vm.onhit.core.tag.mfc.MifareClassicParser.checkNdef
import mba.vm.onhit.core.tag.mfc.MifareClassicParser.maxNdefMessageSize
import mba.vm.onhit.core.tag.mfc.MifareClassicProtocol
import mba.vm.onhit.core.tag.mfc.MifareClassicTag
import mba.vm.onhit.core.model.TagTechSpec
import mba.vm.onhit.core.model.TagTechnology

class MifareClassic : BaseFakeTag() {
    override var uid: ByteArray = byteArrayOf()
    private var tagData: MifareClassicTag? = null
    private var protocol: MifareClassicProtocol? = null

    private val internalTechnologies = mutableListOf<TagTechSpec>()
    override val technologies: List<TagTechSpec>
        get() = internalTechnologies

    override fun init(uid: ByteArray, bytes: ByteArray) {
        this.uid = uid
        val sectors = MifareClassicParser.parse(bytes)
        if (sectors.isNotEmpty()) {
            val cardInfo = MifareClassicParser.getTagInfo(sectors[0])
            val atqa = cardInfo.first ?: byteArrayOf(0x00, 0x04)
            val sak = cardInfo.second?.toShort() ?: 0x08
            val mfcTag = MifareClassicTag(uid, sectors, atqa, sak)
            this.tagData = mfcTag
            this.protocol = MifareClassicProtocol(mfcTag)

            internalTechnologies.clear()
            internalTechnologies.add(
                TagTechSpec(
                    TagTechnology.NFC_A,
                    Bundle().apply {
                        putShort("sak", sak)
                        putByteArray("atqa", atqa)
                    }
                )
            )
            internalTechnologies.add(
                TagTechSpec(
                    TagTechnology.MIFARE_CLASSIC,
                    Bundle()
                )
            )

            ndef = checkNdef(sectors)
            ndef?.let {
                internalTechnologies.add(
                    TagTechSpec(
                        TagTechnology.NDEF,
                        Bundle().apply {
                            putParcelable("ndefmsg", it)
                            putInt("ndefmaxlength", sectors.maxNdefMessageSize)
                            putInt("ndefcardstate", 1)
                            putInt("ndeftype", 101)
                        }
                    )
                )
            }
        }
    }

    override fun transceive(data: ByteArray, raw: Boolean, returnCode: IntArray): ByteArray? {
        if (raw) return null
        return protocol?.processCommand(data, returnCode)
    }
}
