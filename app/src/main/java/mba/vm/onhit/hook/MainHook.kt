package mba.vm.onhit.hook

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.kyuubiran.ezxhelper.xposed.EzXposed
import mba.vm.onhit.Constant.Companion.NFC_SERVICE_PACKAGE_NAME

class MainHook : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        EzXposed.initHandleLoadPackage(lpparam)
        when (lpparam.packageName) {
            NFC_SERVICE_PACKAGE_NAME -> initHooks(NfcServiceHook, lpparam.classLoader)
        }
    }

    private fun initHooks(hook: BaseHook, classLoader: ClassLoader?) {
        try {
            hook.init(classLoader)
        } catch (e: Exception) {
            XposedBridge.log(e)
        }
    }
}