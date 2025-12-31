package com.example.xposedapp

import com.example.xposedapp.hook.ApplicationHook
import com.example.xposedapp.hook.BaseHook
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.kyuubiran.ezxhelper.android.logging.Logger
import io.github.kyuubiran.ezxhelper.xposed.EzXposed

const val TARGET_APP = "com.example.target"
const val TAG = "EzXposedApp"

class MainHook : IXposedHookLoadPackage, IXposedHookZygoteInit {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName == TARGET_APP) return
        EzXposed.initHandleLoadPackage(lpparam)
        Logger.tag = TAG
        initHooks(
            // Add your hooks here
            // ExampleHook1, ExampleHook2, etc.
            ApplicationHook
        )
    }

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        EzXposed.initZygote(startupParam)
    }

    private fun initHooks(vararg hooks: BaseHook) {
        for (hook in hooks) {
            try {
                hook.init()
            } catch (e: Exception) {
                Logger.e("Init hook failed!", e)
            }
        }
    }
}