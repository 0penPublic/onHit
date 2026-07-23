package mba.vm.onhit.core.recorder.trace

import mba.vm.onhit.core.model.TagTechSpec

data class TagTrace(
    val uid: ByteArray,
    val technologies: Array<TagTechSpec>,
    val transceiveData: MutableList<TransceiveData> = mutableListOf(),
    val formatVersion: Int = TAG_TRACE_VERSION,
) {
    companion object {
        const val TAG_TRACE_VERSION = 1
    }

    data class TransceiveData(
        val cmd: ByteArray,
        val raw: Boolean,
        val returnCodes: IntArray,
        val resp: ByteArray?
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as TransceiveData
            if (raw != other.raw) return false
            if (!cmd.contentEquals(other.cmd)) return false
            if (!returnCodes.contentEquals(other.returnCodes)) return false
            if (!resp.contentEquals(other.resp)) return false
            return true
        }
        override fun hashCode(): Int {
            var result = raw.hashCode()
            result = 31 * result + cmd.contentHashCode()
            result = 31 * result + returnCodes.contentHashCode()
            result = 31 * result + resp.contentHashCode()
            return result
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TagTrace

        if (formatVersion != other.formatVersion) return false
        if (!uid.contentEquals(other.uid)) return false
        if (!technologies.contentEquals(other.technologies)) return false
        if (transceiveData != other.transceiveData) return false

        return true
    }

    override fun hashCode(): Int {
        var result = formatVersion
        result = 31 * result + uid.contentHashCode()
        result = 31 * result + technologies.contentHashCode()
        result = 31 * result + transceiveData.hashCode()
        return result
    }

    fun addExchange(
        cmd: ByteArray,
        raw: Boolean,
        returnCodes: IntArray,
        resp: ByteArray?
    ) {
        transceiveData.add(TransceiveData(
            cmd,
            raw,
            returnCodes,
            resp
        ))
    }
}