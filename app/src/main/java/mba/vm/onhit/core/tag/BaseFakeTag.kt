package mba.vm.onhit.core.tag

import android.nfc.NdefMessage
import android.os.Bundle
import mba.vm.onhit.core.TagTechnology
import java.lang.reflect.Proxy
import java.security.SecureRandom

abstract class BaseFakeTag {
    open val name: String
        get() = this::class.java.simpleName
    abstract fun init(uid: ByteArray, bytes: ByteArray): BaseFakeTag
    abstract fun makeEndpoint(nfcClassloader: ClassLoader, tagEndpointInterface: Class<*>): Any

    companion object {
        val random = SecureRandom()
        val TAG_TYPE_MAPPING = mapOf(
            Pair("ndef", Ndef::class.java),
            Pair("mfc", MifareClassic::class.java)
        )

        var lastConnectedTechnology = TagTechnology.Unknown
        var lastHandle: Int = -1

        fun create(tech: String): BaseFakeTag? {
            return TAG_TYPE_MAPPING[tech]?.getDeclaredConstructor()?.newInstance()
        }

        fun createTagEndpoint(
            nfcClassloader: ClassLoader,
            tagEndpointInterface: Class<*>,
            uid: ByteArray,
            techList: IntArray,
            techExtras: Array<Bundle>,
            transceive: (cmd: ByteArray) -> ByteArray?,
            ndef: NdefMessage? = null
        ): Any {
            lastHandle = random.nextInt(Int.MAX_VALUE) + 1
            lastConnectedTechnology = TagTechnology.Unknown
            return Proxy.newProxyInstance(nfcClassloader, arrayOf(tagEndpointInterface)) { _, method, args ->
                when (method.name) {
                    "getUid" -> uid
                    "findAndReadNdef" -> ndef
                    "getNdef" -> ndef
                    "readNdef" -> ndef?.toByteArray() ?: byteArrayOf()
                    "connect" -> {
                        lastConnectedTechnology = TagTechnology.fromInt(args?.get(0) as? Int ?: 0)
                        true
                    }
                    "disconnect" -> false
                    "transceive" -> {
                        val data = args?.firstOrNull { it is ByteArray } as? ByteArray
                        val raw = args?.firstOrNull { it is Boolean } as? Boolean
                        val returnCode = args?.firstOrNull { it is IntArray } as? IntArray
                        if (data != null && raw != null && returnCode != null) {
                            returnCode[0] = 0
                            if (raw) {
                                null
                            } else {
                                transceive(data)
                            }
                        } else {
                            returnCode?.set(0, 0)
                            null
                        }
                    }
                    "getConnectedTechnology" -> lastConnectedTechnology.flag
                    "getTechList" -> techList
                    "getTechExtras" -> techExtras
                    "isPresent" -> true
                    "getHandle" -> lastHandle
                    "getTechHandles" -> IntArray(techList.size) { lastHandle }
                    else -> {
                        when (method.returnType) {
                            Boolean::class.javaPrimitiveType -> true
                            Int::class.javaPrimitiveType -> 0
                            Void.TYPE -> null
                            else -> {
                                if (method.returnType.isArray && method.returnType.componentType == Byte::class.javaPrimitiveType) {
                                    byteArrayOf()
                                } else if (method.returnType.isArray && method.returnType.componentType == Int::class.javaPrimitiveType) {
                                    intArrayOf()
                                } else null
                            }
                        }
                    }
                }
            }
        }
    }
}