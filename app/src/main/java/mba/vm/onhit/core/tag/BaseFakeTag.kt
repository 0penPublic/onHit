package mba.vm.onhit.core.tag

import android.nfc.NdefMessage
import android.os.Bundle
import mba.vm.onhit.core.emulator.FakeTagEndpointHandler
import mba.vm.onhit.core.emulator.FakeTagSession
import mba.vm.onhit.core.model.TagTechSpec
import mba.vm.onhit.core.model.TagTechnology
import java.lang.reflect.Proxy

abstract class BaseFakeTag {

    abstract var uid: ByteArray

    abstract val technologies: List<TagTechSpec>

    open var ndef: NdefMessage? = null

    val techList: IntArray
        get() = TagTechnology.arrayOfTagTechnology(*technologies.map { it.tech }.toTypedArray())

    val techExtras: Array<Bundle>
        get() = technologies.map { it.extras }.toTypedArray()

    abstract fun init(uid: ByteArray, bytes: ByteArray)

    open fun transceive(
        data: ByteArray,
        raw: Boolean,
        returnCode: IntArray
    ): ByteArray? = null

    fun makeEndpoint(
        nfcClassloader: ClassLoader,
        tagEndpointInterface: Class<*>
    ): Any {
        return Proxy.newProxyInstance(
            nfcClassloader,
            arrayOf(tagEndpointInterface),
            FakeTagEndpointHandler(this, FakeTagSession())
        )
    }

    companion object {
        fun create(type: String): BaseFakeTag? = when (type.lowercase()) {
            "mfc" -> MifareClassic()
            "ndef" -> Ndef()
            "trace" -> TraceReplay()
            else -> null
        }
    }
}