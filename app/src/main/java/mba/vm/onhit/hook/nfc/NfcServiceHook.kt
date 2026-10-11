package mba.vm.onhit.hook.nfc

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import androidx.core.content.ContextCompat
import de.robv.android.xposed.XposedHelpers.findClass
import de.robv.android.xposed.XposedHelpers.getStaticObjectField
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder
import io.github.kyuubiran.ezxhelper.core.helper.ObjectHelper.`-Static`.objectHelper
import io.github.kyuubiran.ezxhelper.xposed.dsl.HookFactory.`-Static`.createHook
import mba.vm.onhit.BuildConfig
import mba.vm.onhit.Constant
import mba.vm.onhit.Constant.Companion.NFC_SERVICE_PACKAGE_NAME
import mba.vm.onhit.broadcast.NfcHookBroadcastReceiver
import mba.vm.onhit.hook.BaseHook
import mba.vm.onhit.hook.core.recorder.TagRecorder
import mba.vm.onhit.hook.core.tag.BaseFakeTag
import mba.vm.onhit.utils.LogUtils.logE
import mba.vm.onhit.utils.LogUtils.logI
import java.lang.reflect.Method

object NfcServiceHook : BaseHook() {
    private var isInitialized = false
    private val receiver: NfcHookBroadcastReceiver = NfcHookBroadcastReceiver()
    private lateinit var nfcApplication: Application
    private lateinit var nfcService: Any
    private lateinit var nfcServiceHandler: Handler
    private lateinit var nfcClassLoader: ClassLoader
    private lateinit var dispatchTagEndpoint: Method
    private lateinit var tagEndpointInterface: Class<*>

    fun findAvailableClass(classLoader: ClassLoader, vararg classNames: String): Class<*>? {
        classNames.forEach { name ->
            runCatching {
                return findClass(name, classLoader)
            }.exceptionOrNull()?.let {
                logE("Cannot find $name: ${it.message}", it)
            }
        }
        logE("Unable to find class from: ${classNames.joinToString()}")
        return null
    }


    @Synchronized
    override fun init(classLoader: ClassLoader, packageName: String) {
        fun resolveNfcClass(targetName: String) = findAvailableClass(
            classLoader,
            "$packageName.$targetName",
            "$NFC_SERVICE_PACKAGE_NAME.$targetName",
        )
        if (isInitialized) return else isInitialized = true
        nfcClassLoader = classLoader
        tagEndpointInterface = resolveNfcClass($$"DeviceHost$TagEndpoint") ?: return
        val nfcApplicationClass = resolveNfcClass("NfcApplication") ?: return
        MethodFinder.fromClass(nfcApplicationClass)
            .filterByName("onCreate")
            .first()
            .createHook {
                after { params ->
                    nfcApplication = params.thisObject as? Application ?: run {
                        logE("Failed to get NfcApplication, hook Failed.")
                        return@after
                    }
                    nfcService = nfcApplication.objectHelper().getObjectOrNull("mNfcService")
                        ?: resolveNfcClass("NfcService")
                            ?.runCatching { getStaticObjectField(this, "sService") }?.getOrNull()
                        ?: run {
                            logE("Cannot get NFC Service now, Hook Failed. Is NFC Service Working?")
                            return@after
                        }
                    nfcServiceHandler = nfcService.objectHelper().getObjectOrNull("mHandler") as? Handler?: run {
                        logE("Cannot get NFC Service Handler, Hook Failed.")
                        return@after
                    }
                    dispatchTagEndpoint = MethodFinder.fromClass(nfcServiceHandler.javaClass)
                        .filterByName("dispatchTagEndpoint")
                        .firstOrNull() ?: run {
                        logE("Cannot find dispatchTagEndpoint Method, Hook Failed.")
                        return@after
                    }
                    dispatchTagEndpoint.createHook {
                        before { param ->
                            TagRecorder.onTagEndpointDispatch(param.args[0])
                        }
                    }
                    if (BuildConfig.DEBUG) runCatching { nfcService.objectHelper().setObject("DBG", true) }
                    ContextCompat.registerReceiver(nfcApplication, receiver, IntentFilter().apply {
                        addAction(Constant.BROADCAST_TAG_EMULATOR_REQUEST)
                        addAction(Constant.BROADCAST_TOGGLE_TAG_RECORDER_REQUEST)
                        addAction(Constant.BROADCAST_TAG_RECORDER_STATE_REQUEST)
                        addAction(Constant.BROADCAST_RESTART_NFC_SERVICE)
                    }, ContextCompat.RECEIVER_EXPORTED)
                    logI("initialized successfully.")
                }
            }
    }

    fun sendBroadcast(intent: Intent) {
        if (!isInitialized) return
        nfcApplication.sendBroadcast(intent)
    }

    fun dispatchFakeTag(
        fakeTag: BaseFakeTag
    ) {
        if (!isInitialized) return
        val targetClassLoader = tagEndpointInterface.classLoader ?: nfcClassLoader
        logI("Try to dispatch { uid=${fakeTag.uid.toHexString()}, techList=${fakeTag.techList.contentToString()} }...")
        NfcDispatchManagerHook.databaseManager ?.let {
            logI("Oplus Database Manager found, try to delete tag cache...")
            NfcDispatchManagerHook.deleteTagCache(fakeTag.uid)
        }
        val tag = fakeTag.makeEndpoint(targetClassLoader, tagEndpointInterface)
        nfcServiceHandler.post {
            dispatchTagEndpoint.invoke(
                nfcServiceHandler,
                tag,
                nfcService.objectHelper().getObjectOrNull("mReaderModeParams")
            )
        }
    }
}
