package mba.vm.onhit.utils

import io.github.kyuubiran.ezxhelper.android.logging.Logger
import mba.vm.onhit.BuildConfig

@Suppress("unused")
object LogUtils {
    private const val TAG_WIDTH = 20
    private fun String.center(width: Int): String {
        if (this.length >= width) return this.take(width)
        val totalPadding = width - this.length
        val leftPadding = totalPadding / 2
        val rightPadding = totalPadding - leftPadding
        return " ".repeat(leftPadding) + this + " ".repeat(rightPadding)
    }

    val currentCallerClassName: String
        get() {
            val stackTrace = Thread.currentThread().stackTrace
            val targetPackage = BuildConfig.APPLICATION_ID
            for (element in stackTrace) {
                val fullName = element.className
                if (!fullName.startsWith(targetPackage)) continue
                val simpleName = fullName.substringAfterLast('.')
                if (simpleName.contains("LogUtils", ignoreCase = true) ||
                    simpleName.contains("Logger", ignoreCase = true)) {
                    continue
                }
                val cleanName = simpleName.substringBefore('$')
                return cleanName.center(TAG_WIDTH)
            }
            return "Unknown".center(TAG_WIDTH)
        }

    fun logI(msg: String, thr: Throwable? = null) {
        if (BuildConfig.DEBUG) Logger.i("[ onHit ] [ $currentCallerClassName ] [I] $msg", thr)
    }

    fun logD(msg: String, thr: Throwable? = null) {
        if (BuildConfig.DEBUG) Logger.d("[ onHit ] [ $currentCallerClassName ] [D] $msg", thr)
    }

    fun logW(msg: String, thr: Throwable? = null) {
        if (BuildConfig.DEBUG) Logger.w("[ onHit ] [ $currentCallerClassName ] [W] $msg", thr)
    }

    fun logE(msg: String, thr: Throwable? = null) {
        if (BuildConfig.DEBUG) Logger.e("[ onHit ] [ $currentCallerClassName ] [E] $msg", thr)
    }

    fun logV(msg: String, thr: Throwable? = null) {
        if (BuildConfig.DEBUG) Logger.v("[ onHit ] [ $currentCallerClassName ] [V] $msg", thr)
    }
}