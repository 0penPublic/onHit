package mba.vm.onhit.ui.nfc.handler.wifi

object NdefWifiHelper {

    val authModes = listOf(
        "None" to 0x0001,
        "WPA-Personal" to 0x0002,
        "WPA2-Personal" to 0x0020,
        "WPA/WPA2-Personal" to 0x0022,
        "WPA3-Personal" to 0x0040,
        "WPA-Enterprise" to 0x0008,
        "WPA2-Enterprise" to 0x0010,
        "WPA/WPA2-Enterprise" to 0x0018
    )

    val encryptionModes = listOf(
        "None" to 0x0001,
        "WEP" to 0x0002,
        "TKIP" to 0x0004,
        "AES" to 0x0008,
        "AES/TKIP" to 0x000C
    )

    private val authBuildMap = authModes.toMap()
    private val encryptionBuildMap = encryptionModes.toMap()

    fun buildPayload(data: WiFiData): ByteArray {
        val credential = mutableListOf<Byte>()

        fun addTlvToList(list: MutableList<Byte>, tag: Int, value: ByteArray) {
            list.add((tag shr 8).toByte())
            list.add((tag and 0xFF).toByte())
            list.add((value.size shr 8).toByte())
            list.add((value.size and 0xFF).toByte())
            for (b in value) list.add(b)
        }

        addTlvToList(credential, 0x1026, byteArrayOf(0x01))
        addTlvToList(credential, 0x1045, data.ssid.toByteArray(Charsets.UTF_8))

        val auth = authBuildMap[data.security] ?: 0x0001
        addTlvToList(credential, 0x1003, byteArrayOf(0, (auth and 0xFF).toByte()))

        val encr = encryptionBuildMap[data.encryption] ?: 0x0001
        addTlvToList(credential, 0x100F, byteArrayOf(0, (encr and 0xFF).toByte()))

        if (data.password.isNotEmpty()) {
            addTlvToList(credential, 0x1027, data.password.toByteArray(Charsets.UTF_8))
        }

        addTlvToList(credential, 0x1020, ByteArray(6) { 0xFF.toByte() })

        val out = mutableListOf<Byte>()
        addTlvToList(out, 0x104A, byteArrayOf(0x10))
        addTlvToList(out, 0x100E, credential.toByteArray())
        return out.toByteArray()
    }

    fun parsePayload(payload: ByteArray): WiFiData {
        var ssid = ""
        var password = ""
        var security = "None"
        var encryption = "None"

        fun parseTlvRange(data: ByteArray, start: Int, end: Int) {
            var i = start
            while (i + 4 <= end) {
                val tag = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
                val len = ((data[i + 2].toInt() and 0xFF) shl 8) or (data[i + 3].toInt() and 0xFF)
                i += 4
                if (i + len > end) break
                when (tag) {
                    0x100E -> parseTlvRange(data, i, i + len)
                    0x1045 -> ssid = String(data, i, len, Charsets.UTF_8)
                    0x1027 -> password = String(data, i, len, Charsets.UTF_8)
                    0x1003 -> {
                        val authValue = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
                        security = authModes.findLast { (_, mask) ->
                            if (mask == 0x0022 || mask == 0x0018) (authValue and mask) == mask
                            else (authValue and mask) != 0
                        }?.first ?: "None"
                    }
                    0x100F -> {
                        val encrValue = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
                        encryption = encryptionModes.findLast { (_, mask) ->
                            if (mask == 0x000C) (encrValue and mask) == mask
                            else (encrValue and mask) != 0
                        }?.first ?: "None"
                    }
                }
                i += len
            }
        }

        parseTlvRange(payload, 0, payload.size)
        return WiFiData(ssid, password, security, encryption)
    }
}

