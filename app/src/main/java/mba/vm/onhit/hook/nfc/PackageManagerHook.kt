package mba.vm.onhit.hook.nfc

import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder
import io.github.kyuubiran.ezxhelper.xposed.dsl.HookFactory.`-Static`.createHook
import mba.vm.onhit.Constant.Companion.PACKAGE_MANAGER_SYSTEM_NFC_FEATURES
import mba.vm.onhit.hook.BaseHook
import mba.vm.onhit.utils.LogUtils.logI

object PackageManagerHook : BaseHook() {
    override fun init(classLoader: ClassLoader, packageName: String) {
        MethodFinder.fromClass(
            "android.app.ApplicationPackageManager",
            classLoader
        )
            .filterByName("hasSystemFeature")
            .first()
            .createHook {
                before { param ->
                    logI("hasSystemFeature called in ${param.thisObject.javaClass.name}")
                    if (param.args.isEmpty()) return@before
                    if (param.args[0] as? String in PACKAGE_MANAGER_SYSTEM_NFC_FEATURES) param.result = true
                }
            }
    }
}
