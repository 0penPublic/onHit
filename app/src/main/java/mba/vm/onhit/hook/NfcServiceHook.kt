package mba.vm.onhit.hook

import android.app.Application
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.github.kyuubiran.ezxhelper.ObjectHelper.Companion.objectHelper
import com.github.kyuubiran.ezxhelper.ParamTypes
import com.github.kyuubiran.ezxhelper.Params
import com.github.kyuubiran.ezxhelper.finders.MethodFinder.`-Static`.methodFinder
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers.findClass
import io.github.kyuubiran.ezxhelper.xposed.dsl.HookFactory.`-Static`.createHook
import mba.vm.onhit.Constant
import mba.vm.onhit.core.TagTechnology
import mba.vm.onhit.hook.boardcast.TagEmulatorBroadcastReceiver
import java.lang.reflect.Proxy


object NfcServiceHook : BaseHook() {
    private var nfcService: Any? = null
    private var nfcClassLoader: ClassLoader? = null
    private var nfcServiceClazz: Class<*>? = null

    override val name: String = this::class.simpleName!!


    override fun init(classLoader: ClassLoader?) {
        classLoader?.let {
            nfcClassLoader = classLoader
        } ?: run {
            XposedBridge.log("[ onHit ] nfcClassLoader is null")
        }
        val nfcServiceClassName = "com.android.nfc.NfcService"
        nfcServiceClazz = findClass(nfcServiceClassName, classLoader)
        findClass("com.android.nfc.NfcApplication", classLoader)
            .methodFinder()
            .filterByName("onCreate")
            .first()
            .createHook {
                after { params ->
                    val app = params.thisObject as? Application
                    app?.let {
                        nfcService = app.objectHelper().getObjectOrNull("mNfcService") ?: run {
                            XposedBridge.log("[ onHit ] Cannot get NFC Service now")
                        }
                        ContextCompat.registerReceiver(
                            app,
                            TagEmulatorBroadcastReceiver(),
                            IntentFilter().apply {
                                addAction(Constant.BROADCAST_TAG_EMULATOR_REQUEST)
                            },
                            ContextCompat.RECEIVER_EXPORTED
                        )
                    }
                }
            }
        nfcServiceClazz?.let { clazz ->
            val sendMessageMethod = clazz.methodFinder().filterByName("sendMessage").first()
            sendMessageMethod.createHook {
                before { params ->
                    val what = params.args[0] ?: return@before
                    val obj = params.args[1] ?: return@before
                    if (what != 0) return@before
                    val uid: ByteArray? = obj.objectHelper().invokeMethod(
                        methodName = "getUid",
                        returnType = ByteArray::class.java,
                        paramTypes = ParamTypes(emptyArray()),
                        params = Params(emptyArray())
                    ) as? ByteArray
                    uid?.let {
                        val uidHex = it.joinToString(":") { b -> "%02X".format(b) }
                        XposedBridge.log("[ onHit ] UID=$uidHex")
                    }
                }
            }
        }
    }

    fun dispatchFakeTag(
        uid: ByteArray,
        ndef: NdefMessage?
    ) {
        val service = nfcService ?: run {
            XposedBridge.log("[NFCServiceHook] service not ready")
            return
        }
        val clazz = nfcServiceClazz ?: return
        val cl = nfcClassLoader ?: return
        val tag = buildFakeTag(uid, ndef, cl)
        clazz.methodFinder()
            .filterByName("onRemoteEndpointDiscovered")
            .first()
            .invoke(service, tag)
    }

    fun buildFakeTag(
        uid: ByteArray,
        ndef: NdefMessage?,
        nfcClassLoader: ClassLoader?
    ): Any {
        val tagEndpointInterface = findClass($$"com.android.nfc.DeviceHost$TagEndpoint", nfcClassLoader)
        return Proxy.newProxyInstance(nfcClassLoader, arrayOf(tagEndpointInterface)) { _, method, _ ->
            when (method.name) {
                "getUid" -> uid
                "findAndReadNdef" -> ndef
                "getNdef" -> ndef
                "readNdef" -> ndef?.toByteArray() ?: byteArrayOf()
                "connect" -> true
                "disconnect" -> true
                "reconnect" -> true
                "transceive" -> byteArrayOf()
                "getConnectedTechnology" -> 0
                "startPresenceChecking" -> {
                    null
                }
                "getTechList" -> TagTechnology.arrayOfTagTechnology(
                    TagTechnology.NFC_A,
                    TagTechnology.NDEF,
                    TagTechnology.MIFARE_ULTRALIGHT
                )
                "getTechExtras" -> {
                    val aBundle = Bundle().apply {
                        putShort("sak", 0x00.toShort())
                        putByteArray("atqa", byteArrayOf(0x44, 0x00))
                    }
                    val ndefBundle = Bundle().apply {
                        putParcelable("ndefmsg", ndef)
                        putInt("ndefmaxlength", Int.MAX_VALUE)
                        putInt("ndefcardstate", 1)
                        putInt("ndeftype", 4)
                    }
                    val uBundle = Bundle().apply {
                        putBoolean("is_ultralight_c", false)
                    }
                    arrayOf(aBundle, ndefBundle, uBundle)
                }
                "getHandle" -> 0
                "isPresent" -> true
                else -> {
                    when (method.returnType) {
                        Boolean::class.javaPrimitiveType -> false
                        Int::class.javaPrimitiveType -> 0
                        Void.TYPE -> null
                        else -> null
                    }
                }
            }
        }
    }
}