package mba.vm.onhit.hook

abstract class BaseHook {

    open val name: String
        get() = this::class.java.simpleName
    abstract fun init(classLoader: ClassLoader, packageName: String)
}