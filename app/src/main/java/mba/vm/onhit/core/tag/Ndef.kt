package mba.vm.onhit.core.tag

import android.nfc.NdefMessage
import android.os.Bundle
import mba.vm.onhit.core.TagTechnology

class Ndef : BaseFakeTag() {
    override val name: String = this.javaClass.name
    lateinit var ndefMessage: NdefMessage
    lateinit var uid: ByteArray

    override fun init(uid: ByteArray, bytes: ByteArray): BaseFakeTag {
        this.uid = uid
        this.ndefMessage = NdefMessage(bytes)
        return this
    }

    override fun makeEndpoint(
        nfcClassloader: ClassLoader,
        tagEndpointInterface: Class<*>
    ): Any = createTagEndpoint(
        nfcClassloader,
        tagEndpointInterface,
        uid,
        TagTechnology.arrayOfTagTechnology(TagTechnology.NDEF),
        arrayOf(Bundle().apply {
            putParcelable("ndefmsg", ndefMessage)
            putInt("ndefmaxlength", Int.MAX_VALUE)
            putInt("ndefcardstate", 1)
            putInt("ndeftype", 4)
        }),
        { Pair(true, byteArrayOf()) },
        ndefMessage
    )
}