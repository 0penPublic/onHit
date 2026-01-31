package mba.vm.onhit.hook

import android.app.Application
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.os.Bundle
import androidx.core.content.ContextCompat
import de.robv.android.xposed.XposedHelpers.findClass
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder
import io.github.kyuubiran.ezxhelper.core.helper.ObjectHelper.`-Static`.objectHelper
import io.github.kyuubiran.ezxhelper.xposed.dsl.HookFactory.`-Static`.createHook
import mba.vm.onhit.BuildConfig
import mba.vm.onhit.Constant
import mba.vm.onhit.core.TagTechnology
import mba.vm.onhit.hook.boardcast.NfcServiceHookBroadcastReceiver
import java.lang.reflect.Method
import java.lang.reflect.Proxy


object NfcServiceHook : BaseHook() {
    private lateinit var nfcServiceHandler: Any
    private lateinit var nfcService: Any
    private lateinit var nfcClassLoader: ClassLoader
    private lateinit var dispatchTagEndpoint: Method
    private lateinit var tagEndpointInterface: Class<*>

    override val name: String = this::class.simpleName!!

    override fun init(classLoader: ClassLoader?) {
        classLoader?.let {
            nfcClassLoader = classLoader
        } ?: run {
            log("nfcClassLoader is null")
            return
        }
        tagEndpointInterface = findClass($$"com.android.nfc.DeviceHost$TagEndpoint", nfcClassLoader)
        MethodFinder.fromClass("com.android.nfc.NfcApplication", nfcClassLoader)
            .filterByName("onCreate")
            .first()
            .createHook {
                after { params ->
                    val app = params.thisObject as? Application
                    app?.let {
                        nfcService = app.objectHelper().getObjectOrNull("mNfcService") ?: run {
                            log("Cannot get NFC Service now")
                        }
                        nfcServiceHandler = nfcService.objectHelper().getObjectOrNull("mHandler")!!
                        dispatchTagEndpoint = MethodFinder.fromClass(nfcServiceHandler::class)
                            .filterByName("dispatchTagEndpoint")
                            .first()
                        if (BuildConfig.DEBUG) nfcService.objectHelper().setObject("DBG", true)
                        ContextCompat.registerReceiver(
                            app,
                            NfcServiceHookBroadcastReceiver(),
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
        val tag = buildFakeTag(uid, ndef)
        dispatchTagEndpoint.invoke(
            nfcServiceHandler,
            tag, nfcService.objectHelper().getObjectOrNull("mReaderModeParams")) ?: run {
            log("mReaderModeParams is null")
        }
    }

    private fun buildFakeTag(
        uid: ByteArray,
        ndef: NdefMessage?,
    ): Any {
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