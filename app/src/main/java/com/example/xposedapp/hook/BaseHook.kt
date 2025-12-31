package com.example.xposedapp.hook

abstract class BaseHook {

    abstract val name: String
    abstract fun init()
}