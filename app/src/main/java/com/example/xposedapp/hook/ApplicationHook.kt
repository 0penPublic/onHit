package com.example.xposedapp.hook

import android.app.Application
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder
import io.github.kyuubiran.ezxhelper.xposed.dsl.HookFactory.`-Static`.createHook

object ApplicationHook : BaseHook() {

    override val name: String = "ApplicationHook"

    override fun init() {
        MethodFinder.fromClass(Application::class).filterByName("onCreate").first().createHook {
            after {
                // This code will run after Application.onCreate is called
                // You can add your custom logic here
                println("Application onCreate called")
            }
        }
    }
}