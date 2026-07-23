package mba.vm.onhit.hook

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.kyuubiran.ezxhelper.xposed.EzXposed
import mba.vm.onhit.BuildConfig
import mba.vm.onhit.hook.nfc.NfcDispatchManagerHook
import mba.vm.onhit.hook.nfc.NfcServiceHook
import mba.vm.onhit.hook.nfc.PackageManagerHook
import mba.vm.onhit.utils.LogUtils.logE

class MainHook : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        EzXposed.initHandleLoadPackage(lpparam)
        if (lpparam.packageName == BuildConfig.APPLICATION_ID) return
        initHook(lpparam.classLoader, lpparam.packageName,
            NfcServiceHook,
            NfcDispatchManagerHook,
            PackageManagerHook
        )
    }

    private fun initHook(classLoader: ClassLoader, packageName: String, vararg hooks: BaseHook) {
        hooks.forEach { hook ->
            try {
                hook.init(classLoader, packageName)
            } catch (e: Exception) {
                logE("Failed to Init ${hook.name}, ${e.message}", e)
            }
        }
    }
}