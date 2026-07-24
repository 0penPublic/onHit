-optimizationpasses 10
-allowaccessmodification
-overloadaggressively
-dontskipnonpubliclibraryclasses
-dontpreverify

-keep class mba.vm.onhit.hook.MainHook {
    <init>();
    void handleLoadPackage(...);
}

-assumenosideeffects class android.util.Log {
    public static *** i(...);
    public static *** d(...);
    public static *** v(...);
    public static *** w(...);
    public static *** e(...);
    public static *** println(...);
}

-assumenosideeffects class io.github.kyuubiran.ezxhelper.android.logging.Logger {
    public void i(...);
    public void d(...);
    public void w(...);
    public void e(...);
    public void v(...);
}

-assumenosideeffects class mba.vm.onhit.utils.LogUtils {
    public void logI(...);
    public void logD(...);
    public void logW(...);
    public void logE(...);
    public void logV(...);
}

-assumenosideeffects class de.robv.android.xposed.XposedBridge {
    public static void log(...);
}
