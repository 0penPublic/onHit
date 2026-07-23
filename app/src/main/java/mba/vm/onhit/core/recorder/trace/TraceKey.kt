package mba.vm.onhit.core.recorder.trace

data class TraceKey(
    val cmd: ByteArray,
    val raw: Boolean,
    val returnCodes: IntArray
) {
    override fun equals(other: Any?): Boolean {
        if (other !is TraceKey) return false
        return raw == other.raw &&
                cmd.contentEquals(other.cmd) &&
                returnCodes.contentEquals(other.returnCodes)
    }

    override fun hashCode(): Int {
        var result = raw.hashCode()
        result = 31 * result + cmd.contentHashCode()
        result = 31 * result + returnCodes.contentHashCode()
        return result
    }
}