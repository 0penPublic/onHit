package mba.vm.onhit.utils

object HexUtils {
    fun decodeHex(hex: String): ByteArray {
        val s = hex.replace(" ", "")
        if (s.length % 2 != 0) return byteArrayOf()
        return ByteArray(s.length / 2) {
            s.substring(it * 2, it * 2 + 2).toInt(16).toByte()
        }
    }

    fun encodeHex(bytes: ByteArray?): String {
        return bytes?.joinToString("") { "%02X".format(it) } ?: ""
    }
    
    fun filterHex(s: String): String {
        return s.filter { it.isDigit() || it.uppercaseChar() in 'A'..'F' }
    }
}
