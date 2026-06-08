package mba.vm.onhit.hook

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.kyuubiran.ezxhelper.xposed.EzXposed
import mba.vm.onhit.BuildConfig

class MainHook : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        EzXposed.initHandleLoadPackage(lpparam)
        initHook(lpparam.classLoader, lpparam.packageName,NfcServiceHook, NfcDispatchManagerHook, PackageManagerHook)
    }

    private fun initHook(classLoader: ClassLoader, packageName: String, vararg hooks: BaseHook) {
        hooks.forEach { hook ->
            try {
                hook.init(classLoader, packageName)
            } catch (e: Exception) {
                XposedBridge.log("[ ${BuildConfig.APPLICATION_ID} ] Failed to Init ${hook.name}, ${e.message}")
            }
        }
    }
}