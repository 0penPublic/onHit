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

    private fun printLog(tag: String, msg: String, thr: Throwable?, action: (String, Throwable?) -> Unit) {
        if (!BuildConfig.DEBUG) return
        action("[ onHit ] [ $currentCallerClassName ] [$tag] $msg", thr)
    }

    fun logI(msg: String, thr: Throwable? = null) = printLog("I", msg, thr, Logger::i)
    fun logD(msg: String, thr: Throwable? = null) = printLog("D", msg, thr, Logger::d)
    fun logW(msg: String, thr: Throwable? = null) = printLog("W", msg, thr, Logger::w)
    fun logE(msg: String, thr: Throwable? = null) = printLog("E", msg, thr, Logger::e)
    fun logV(msg: String, thr: Throwable? = null) = printLog("V", msg, thr, Logger::v)
}