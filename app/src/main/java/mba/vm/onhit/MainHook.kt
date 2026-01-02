package mba.vm.onhit

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.kyuubiran.ezxhelper.xposed.EzXposed
import mba.vm.onhit.hook.BaseHook
import mba.vm.onhit.hook.NfcServiceHook

class MainHook : IXposedHookLoadPackage, IXposedHookZygoteInit {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.android.nfc") return
        EzXposed.initHandleLoadPackage(lpparam)
        initHooks(NfcServiceHook, lpparam.classLoader)
    }

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        EzXposed.initZygote(startupParam)
    }

    private fun initHooks(hook: BaseHook, classLoader: ClassLoader?) {
        try {
            hook.init(classLoader)
        } catch (e: Exception) {
            XposedBridge.log(e)
        }
    }
}