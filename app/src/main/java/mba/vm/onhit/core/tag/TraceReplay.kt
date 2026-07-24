package mba.vm.onhit.core.tag

import android.nfc.NdefMessage
import androidx.core.os.BundleCompat
import mba.vm.onhit.core.model.TagTechSpec
import mba.vm.onhit.core.model.TagTechnology
import mba.vm.onhit.core.recorder.trace.TagTrace
import mba.vm.onhit.core.recorder.trace.TagTraceCodec
import mba.vm.onhit.core.recorder.trace.TraceKey

class TraceReplay : BaseFakeTag() {
    private lateinit var trace: TagTrace
    private val traceIndex = mutableMapOf<TraceKey, Int>()
    private val traceMap = mutableMapOf<TraceKey, MutableList<TagTrace.TransceiveData>>()
    override lateinit var uid: ByteArray
    override var ndef: NdefMessage? = null
    private val internalTechnologies = mutableListOf<TagTechSpec>()
    override val technologies: List<TagTechSpec>
        get() = internalTechnologies

    override fun init(uid: ByteArray, bytes: ByteArray) {
        this.uid = uid
        trace = TagTraceCodec.decode(bytes)
        trace.technologies.forEach(internalTechnologies::add)
        trace.transceiveData.forEach {
            val key = TraceKey(it.cmd, it.raw, it.returnCodes)
            traceMap.getOrPut(key) { mutableListOf() }.add(it)
        }
        ndef = trace.technologies
            .firstOrNull { it.tech == TagTechnology.NDEF }
            ?.extras
            ?.let { BundleCompat.getParcelable(it, "ndefmsg", NdefMessage::class.java) }
    }

    override fun transceive(
        data: ByteArray,
        raw: Boolean,
        returnCode: IntArray
    ): ByteArray? {
        val key = TraceKey(data, raw, returnCode)
        val matches = traceMap[key] ?: return null
        val index = traceIndex[key] ?: 0
        val actualIndex = minOf(index, matches.lastIndex)
        if (actualIndex < matches.lastIndex) {
            traceIndex[key] = actualIndex + 1
        }
        return matches[actualIndex].resp
    }
}