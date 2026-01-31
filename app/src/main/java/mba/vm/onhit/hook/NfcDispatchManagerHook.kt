package mba.vm.onhit.hook

import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder
import io.github.kyuubiran.ezxhelper.xposed.dsl.HookFactory.`-Static`.createHook
import mba.vm.onhit.BuildConfig

/**
 * Hook for Oplus NFC Dispatch Manager (ColorOS)
 *
 * Purpose:
 *   Prevent the dispatch manager from blocking NFC operations
 *   when this app is in the foreground.
 */
object NfcDispatchManagerHook : BaseHook() {
    override val name: String = this::class.simpleName!!

    override fun init(classLoader: ClassLoader?) {
        classLoader ?: run {
            log("nfcClassLoader is null")
            return
        }
        val clazz = try {
            Class.forName("com.oplus.nfc.dispatch.NfcDispatchManager", false, classLoader)
        } catch (_: ClassNotFoundException) {
            log("NfcDispatchManager not found, skip hook")
            return
        }
        MethodFinder.fromClass(clazz)
            .filterByName("checkForegroundDiretWhiteList")
            .first()
            .createHook {
                before { param ->
                    val pkg = param.args[0] as String
                    // If the package is our app, force the method to return false
                    // Effect: NFC dispatch will not treat this app as whitelisted
                    if (pkg == BuildConfig.APPLICATION_ID) param.result = false
                }
            }
    }
}