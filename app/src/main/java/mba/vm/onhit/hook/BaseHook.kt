package mba.vm.onhit.hook

abstract class BaseHook {

    abstract val name: String
    abstract fun init(classLoader: ClassLoader?)
}