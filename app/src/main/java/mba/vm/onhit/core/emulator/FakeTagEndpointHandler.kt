package mba.vm.onhit.core.emulator

import mba.vm.onhit.core.model.TagTechnology
import mba.vm.onhit.core.tag.BaseFakeTag
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method

class FakeTagEndpointHandler(
    private val tag: BaseFakeTag,
    private val session: FakeTagSession
) : InvocationHandler {
    override fun invoke(
        proxy: Any,
        method: Method,
        args: Array<out Any?>?
    ): Any? = when (method.name) {
        "getUid" -> tag.uid
        "findAndReadNdef",
        "getNdef" -> tag.ndef
        "readNdef" -> tag.ndef?.toByteArray() ?: byteArrayOf()
        "connect" -> connect(args!!)
        "disconnect" -> disconnect()
        "transceive" -> transceive(args!!)
        "getConnectedTechnology" -> session.connectedTechnology.flag
        "getTechList" -> tag.techList
        "getTechExtras" -> tag.techExtras
        "isPresent" -> true
        "getHandle" -> session.handle
        "getTechHandles" -> IntArray(tag.techList.size) { session.handle }
        else -> method.returnType.defaultValue()
    }

    private fun connect(args: Array<out Any?>): Boolean {
        session.connectedTechnology = TagTechnology.fromInt(args[0] as Int)
        return true
    }

    private fun disconnect(): Boolean {
        session.connectedTechnology = TagTechnology.Unknown
        return false
    }

    private fun transceive(args: Array<out Any?>): ByteArray? = tag.transceive(
        args[0] as ByteArray,
        args[1] as Boolean,
        args[2] as IntArray
    )

    private fun Class<*>.defaultValue(): Any? = when (this) {
        Boolean::class.javaPrimitiveType -> false
        Int::class.javaPrimitiveType -> 0
        Long::class.javaPrimitiveType -> 0L
        Short::class.javaPrimitiveType -> 0.toShort()
        Byte::class.javaPrimitiveType -> 0.toByte()
        Float::class.javaPrimitiveType -> 0f
        Double::class.javaPrimitiveType -> 0.0
        Char::class.javaPrimitiveType -> '\u0000'
        Void.TYPE -> null
        ByteArray::class.java -> byteArrayOf()
        IntArray::class.java -> intArrayOf()
        else -> null
    }
}
