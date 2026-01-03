package mba.vm.onhit.hook

import android.app.Application
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.github.kyuubiran.ezxhelper.ObjectHelper.Companion.objectHelper
import com.github.kyuubiran.ezxhelper.finders.MethodFinder.`-Static`.methodFinder
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers.findClass
import io.github.kyuubiran.ezxhelper.xposed.dsl.HookFactory.`-Static`.createHook
import mba.vm.onhit.BuildConfig
import mba.vm.onhit.Constant
import mba.vm.onhit.core.TagTechnology
import mba.vm.onhit.hook.boardcast.TagEmulatorBroadcastReceiver
import java.lang.reflect.Proxy


object NfcServiceHook : BaseHook() {
    private var nfcService: Any? = null
    private var nfcClassLoader: ClassLoader? = null
    private var nfcServiceClazz: Class<*>? = null

    override val name: String = this::class.simpleName!!

    fun log(text: String) = if (BuildConfig.DEBUG) XposedBridge.log("[ onHit ] [ $name ] $text") else Unit


    override fun init(classLoader: ClassLoader?) {
        classLoader?.let {
            nfcClassLoader = classLoader
        } ?: run {
            log("[ onHit ] nfcClassLoader is null")
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
                            log("[ onHit ] Cannot get NFC Service now")
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
    }

    fun dispatchFakeTag(
        uid: ByteArray,
        ndef: NdefMessage?
    ) {
        val service = nfcService ?: run {
            log("[NFCServiceHook] service not ready")
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

    private fun buildFakeTag(
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
                "transceive" -> byteArrayOf()
                "getConnectedTechnology" -> TagTechnology.NDEF.flag
                "getTechList" -> TagTechnology.arrayOfTagTechnology(
                    TagTechnology.NDEF,
                )
                "getTechExtras" -> {
                    val ndefBundle = Bundle().apply {
                        putParcelable("ndefmsg", ndef)
                        putInt("ndefmaxlength", Int.MAX_VALUE)
                        putInt("ndefcardstate", 1)
                        putInt("ndeftype", 4)
                    }
                    arrayOf(ndefBundle)
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