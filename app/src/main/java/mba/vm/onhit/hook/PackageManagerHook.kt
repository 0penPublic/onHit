package mba.vm.onhit.hook

import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder
import io.github.kyuubiran.ezxhelper.xposed.dsl.HookFactory.`-Static`.createHook
import mba.vm.onhit.Constant.Companion.PACKAGE_MANAGER_SYSTEM_NFC_FEATURES

object PackageManagerHook : BaseHook() {
    override val name: String = this.javaClass.simpleName
    override fun init(classLoader: ClassLoader) {
        MethodFinder.fromClass(
            "android.app.ApplicationPackageManager",
            classLoader
        )
            .filterByName("hasSystemFeature")
            .first()
            .createHook {
                before { param ->
                    log("hasSystemFeature called in ${param.thisObject.javaClass.name}")
                    if (param.args.isEmpty()) return@before
                    if (param.args[0] as? String in PACKAGE_MANAGER_SYSTEM_NFC_FEATURES) param.result = true
                }
            }
    }
}
