package mba.vm.onhit.hook

import io.github.kyuubiran.ezxhelper.android.logging.Logger
import mba.vm.onhit.BuildConfig

abstract class BaseHook {

    open val name: String
        get() = this::class.java.simpleName
    abstract fun init(classLoader: ClassLoader, packageName: String)

    fun log(text: String) = if (BuildConfig.DEBUG) Logger.i("[ onHit ] [ $name ] $text") else Unit
}