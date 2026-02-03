package mba.vm.onhit.hook

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.kyuubiran.ezxhelper.xposed.EzXposed
import mba.vm.onhit.BuildConfig
import mba.vm.onhit.Constant.Companion.NFC_SERVICE_PACKAGE_NAME

class MainHook : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        EzXposed.initHandleLoadPackage(lpparam)
        when (lpparam.packageName) {
            NFC_SERVICE_PACKAGE_NAME -> {
                initHook(lpparam.classLoader, NfcServiceHook, NfcDispatchManagerHook, PackageManagerHook)
            }
            else -> initHook(lpparam.classLoader, PackageManagerHook)
        }
    }

    private fun initHook(classLoader: ClassLoader, vararg hooks: BaseHook) {
        hooks.forEach { hook ->
            try {
                hook.init(classLoader)
            } catch (e: Exception) {
                XposedBridge.log("[ ${BuildConfig.APPLICATION_ID} ] Failed to Init ${hook.name}, ${e.message}")
            }
        }
    }
}