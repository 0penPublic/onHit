package mba.vm.onhit.hook

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.kyuubiran.ezxhelper.xposed.EzXposed
import mba.vm.onhit.Constant
import mba.vm.onhit.hook.nfc.NfcDispatchManagerHook
import mba.vm.onhit.hook.nfc.NfcServiceHook
import mba.vm.onhit.hook.nfc.PackageManagerHook
import mba.vm.onhit.utils.LogUtils
import mba.vm.onhit.utils.LogUtils.logE

class MainHook : IXposedHookLoadPackage {

    private companion object {
        val NFC_SERVICE_PACKAGES = setOf(
            Constant.NFC_SERVICE_PACKAGE_NAME,
            Constant.SAMSUNG_NFC_SERVICE_PACKAGE_NAME
        )
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Both packages are declared in xposed_scope; keep Samsung's NFC stack working.
        val isNfcServiceProcess = NFC_SERVICE_PACKAGES.any {
            lpparam.processName == it || lpparam.processName.startsWith("$it:")
        }
        LogUtils.setNfcServiceProcess(isNfcServiceProcess)
        if (!isNfcServiceProcess) return

        EzXposed.initHandleLoadPackage(lpparam)
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