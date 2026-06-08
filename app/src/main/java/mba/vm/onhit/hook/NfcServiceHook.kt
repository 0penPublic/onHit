package mba.vm.onhit.hook

import android.app.Application
import android.content.IntentFilter
import android.os.Handler
import androidx.core.content.ContextCompat
import de.robv.android.xposed.XposedHelpers.findClass
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder
import io.github.kyuubiran.ezxhelper.core.helper.ObjectHelper.`-Static`.objectHelper
import io.github.kyuubiran.ezxhelper.xposed.dsl.HookFactory.`-Static`.createHook
import mba.vm.onhit.BuildConfig
import mba.vm.onhit.Constant
import mba.vm.onhit.Constant.Companion.NFC_SERVICE_PACKAGE_NAME
import mba.vm.onhit.core.tag.BaseFakeTag
import mba.vm.onhit.hook.boardcast.NfcServiceHookBroadcastReceiver
import java.lang.reflect.Method


object NfcServiceHook : BaseHook() {
    private lateinit var nfcService: Any
    private lateinit var nfcServiceHandler: Handler
    private lateinit var nfcClassLoader: ClassLoader
    private lateinit var dispatchTagEndpoint: Method
    private lateinit var tagEndpointInterface: Class<*>

    override val name: String = this.javaClass.simpleName


    fun findAvailableClass(classLoader: ClassLoader, vararg classNames: String): Class<*>? {
        classNames.forEach { name ->
            runCatching {
                return findClass(name, classLoader)
            }
        }
        log("Unable to class from: ${classNames.joinToString()}")
        return null
    }

    override fun init(classLoader: ClassLoader, packageName: String) {
        nfcClassLoader = classLoader
        tagEndpointInterface = findAvailableClass(
            nfcClassLoader,
            $$"$${packageName}.DeviceHost$TagEndpoint",
            $$"$${NFC_SERVICE_PACKAGE_NAME}.DeviceHost$TagEndpoint",
        ) ?: return
        val nfcApplication = findAvailableClass(
            nfcClassLoader,
            "${packageName}.NfcApplication",
            "${NFC_SERVICE_PACKAGE_NAME}.NfcApplication"
        ) ?: return
        MethodFinder.fromClass(nfcApplication)
            .filterByName("onCreate")
            .first()
            .createHook {
                after { params ->
                    val app = params.thisObject as? Application
                    app?.let {
                        nfcService = app.objectHelper().getObjectOrNull("mNfcService") ?: run {
                            log("Cannot get NFC Service now, Hook Failed. Is NFC Service Working?")
                            return@after
                        }
                        nfcServiceHandler = nfcService.objectHelper().getObjectOrNull("mHandler") as? Handler?: run {
                            log("Cannot get NFC Service Handler, Hook Failed.")
                            return@after
                        }
                        dispatchTagEndpoint = MethodFinder.fromClass(nfcServiceHandler.javaClass)
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
        fakeTag: BaseFakeTag
    ) {
        val tag = fakeTag.makeEndpoint(nfcClassLoader, tagEndpointInterface)
        nfcServiceHandler.post {
            dispatchTagEndpoint.invoke(
                nfcServiceHandler,
                tag,
                nfcService.objectHelper().getObjectOrNull("mReaderModeParams")
            )
        }
    }
}