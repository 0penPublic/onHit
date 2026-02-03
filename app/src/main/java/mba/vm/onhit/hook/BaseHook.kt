package mba.vm.onhit.hook

import io.github.kyuubiran.ezxhelper.android.logging.Logger
import mba.vm.onhit.BuildConfig

abstract class BaseHook {

    abstract val name: String
    abstract fun init(classLoader: ClassLoader)

    fun log(text: String) = if (BuildConfig.DEBUG) Logger.i("[ onHit ] [ $name ] $text") else Unit
}