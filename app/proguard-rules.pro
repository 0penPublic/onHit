-optimizationpasses 8
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