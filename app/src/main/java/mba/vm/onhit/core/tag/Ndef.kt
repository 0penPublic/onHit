package mba.vm.onhit.core.tag

import android.nfc.NdefMessage
import android.os.Bundle
import mba.vm.onhit.core.model.TagTechSpec
import mba.vm.onhit.core.model.TagTechnology

class Ndef : BaseFakeTag() {
    override var uid: ByteArray = byteArrayOf()
    private val internalTechnologies = mutableListOf<TagTechSpec>()
    override val technologies: List<TagTechSpec>
        get() = internalTechnologies

    override var ndef: NdefMessage? = null

    override fun init(uid: ByteArray, bytes: ByteArray) {
        this.uid = uid
        this.ndef = runCatching { NdefMessage(bytes) }.getOrNull()
        internalTechnologies.clear()
        internalTechnologies.add(
            TagTechSpec(
                TagTechnology.NDEF,
                Bundle().apply {
                    putInt("ndefmaxlength", bytes.size)
                    putInt("ndefcardstate", 1)
                    putInt("ndeftype", 2)
                    putParcelable("ndefmsg", ndef)
                }
            )
        )
    }
}
