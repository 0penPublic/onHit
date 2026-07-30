package mba.vm.onhit.hook.nfc

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.SharedMemory
import de.robv.android.xposed.XposedHelpers.findClass
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder
import io.github.kyuubiran.ezxhelper.core.helper.ObjectHelper.`-Static`.objectHelper
import io.github.kyuubiran.ezxhelper.xposed.dsl.HookFactory.`-Static`.createHook
import mba.vm.onhit.BuildConfig
import mba.vm.onhit.Constant
import mba.vm.onhit.ICallback
import mba.vm.onhit.IService
import mba.vm.onhit.hook.core.recorder.TagRecorder
import mba.vm.onhit.hook.core.tag.BaseFakeTag
import mba.vm.onhit.hook.BaseHook
import mba.vm.onhit.hook.core.tag.TagType
import mba.vm.onhit.utils.LogUtils.logE
import mba.vm.onhit.utils.LogUtils.logI
import java.lang.reflect.Method
import kotlin.system.exitProcess

object NfcServiceHook : BaseHook() {
    private var isInitialized = false
    private lateinit var nfcApplication: Application
    private lateinit var nfcService: Any
    private lateinit var nfcServiceHandler: Handler
    private lateinit var nfcClassLoader: ClassLoader
    private lateinit var dispatchTagEndpoint: Method
    private lateinit var tagEndpointInterface: Class<*>

    private var nfcHookService: IService? = null

    private val callback = object : ICallback.Stub() {
        
        override fun toggleRecorder() {
            logI("AIDL: toggleRecorder")
            TagRecorder.toggleRecorder()
        }

        override fun requestRecorderState() {
            logI("AIDL: requestRecorderState")
            TagRecorder.notifyState()
        }

        override fun restartNfcService() {
            logI("AIDL: restartNfcService")
            exitProcess(0)
        }

        override fun emulateTag(uid: ByteArray?, sharedMemory: SharedMemory?, size: Int, tagTypeByte: Byte) {
            logI("AIDL: emulateTag")
            if (sharedMemory == null) {
                logE("No sharedMemory provided for emulation")
                return
            }
            val tagType = TagType.fromByte(tagTypeByte)
            try {
                val data = ByteArray(size)
                val buffer = sharedMemory.mapReadOnly()
                buffer[data]
                SharedMemory.unmap(buffer)
                sharedMemory.close()
                val tag = BaseFakeTag.create(tagType)
                tag.init(uid, data)
                dispatchFakeTag(tag)
            } catch (e: Exception) {
                logE("Failed to dispatchFakeTag: $e", e)
            }
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            logI("Connected to Service")
            nfcHookService = IService.Stub.asInterface(service)
            runCatching {
                nfcHookService?.registerCallback(callback)
            }.onFailure { logE("Failed to register callback", it) }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            logI("Disconnected from Service")
            nfcHookService = null
        }
    }

    private fun bindService() {
        val intent = Intent().apply {
            component = ComponentName(BuildConfig.APPLICATION_ID, Constant.SERVICE_CLASS_NAME)
        }
        runCatching {
            nfcApplication.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }.onFailure { logE("Failed to bind Service", it) }
    }

    fun getService() = nfcHookService

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
        if (isInitialized) return
        isInitialized = true
        nfcClassLoader = classLoader
        tagEndpointInterface = findAvailableClass(
            nfcClassLoader,
            $$"$${packageName}.DeviceHost$TagEndpoint",
            $$"$${Constant.NFC_SERVICE_PACKAGE_NAME}.DeviceHost$TagEndpoint",
        ) ?: return
        val nfcApplicationClass = findAvailableClass(
            nfcClassLoader,
            "${packageName}.NfcApplication",
            "${Constant.NFC_SERVICE_PACKAGE_NAME}.NfcApplication"
        ) ?: return
        MethodFinder.fromClass(nfcApplicationClass)
            .filterByName("onCreate")
            .first()
            .createHook {
                after { params ->
                    nfcApplication = params.thisObject as? Application ?: run {
                        logE("Failed to get NfcApplication, hook Failed.")
                        return@after
                    }
                    nfcService = nfcApplication.objectHelper().getObjectOrNull("mNfcService") ?: run {
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
                    if (BuildConfig.DEBUG) nfcService.objectHelper()
                        .setObject("DBG", true)
                    bindService()
                    logI("initialized successfully.")
                }
            }
    }

    fun dispatchFakeTag(
        fakeTag: BaseFakeTag
    ) {
        if (!isInitialized) return
        val targetClassLoader = tagEndpointInterface.classLoader ?: nfcClassLoader
        logI("Try to dispatch { uid=${fakeTag.uid.toHexString()}, techList=${fakeTag.techList.contentToString()} }...")
        NfcDispatchManagerHook.databaseManager ?.let {
            logI("Oplus Database Manager find, try to delete tag cache...")
            NfcDispatchManagerHook.deleteTagCache(fakeTag.uid)
        }
        val tag = fakeTag.makeEndpoint(targetClassLoader, tagEndpointInterface)
        nfcServiceHandler.post {
            val token = Binder.clearCallingIdentity()
            try {
                logI("Invoking dispatchTagEndpoint...")
                dispatchTagEndpoint.invoke(
                    nfcServiceHandler,
                    tag,
                    nfcService.objectHelper().getObjectOrNull("mReaderModeParams")
                )
                logI("dispatchTagEndpoint invoked successfully.")
            } catch (e: Throwable) {
                logE("Error during dispatchTagEndpoint.invoke: ${e.message}", e)
                if (e is java.lang.reflect.InvocationTargetException) {
                    logE("Cause of InvocationTargetException: ${e.cause?.message}", e.cause)
                }
            } finally {
                Binder.restoreCallingIdentity(token)
            }
        }
    }
}