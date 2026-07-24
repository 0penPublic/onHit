package mba.vm.onhit.utils

object HexUtils {
    fun decodeHex(hex: String): ByteArray {
        val s = hex.replace(" ", "").replace("\n", "").replace("\r", "")
        if (s.isEmpty()) return byteArrayOf()
        if (s.length % 2 != 0) return byteArrayOf()
        return try {
            ByteArray(s.length / 2) {
                s.substring(it * 2, it * 2 + 2).toInt(16).toByte()
            }
        } catch (_: Exception) {
            byteArrayOf()
        }
    }
    
    fun filterHex(s: String): String {
        return s.filter { it.isDigit() || it.uppercaseChar() in 'A'..'F' }
    }

    fun toHexString(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02X".format(it) }
    }
}
