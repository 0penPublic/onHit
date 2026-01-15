-optimizationpasses 5
-allowaccessmodification
-overloadaggressively
-dontskipnonpubliclibraryclasses
-dontpreverify

-keep class mba.vm.onhit.MainHook {
    <init>();
    void handleLoadPackage(...);
    void initZygote(...);
}

-assumenosideeffects class android.util.Log {
    public static *** i(...);
    public static *** d(...);
    public static *** v(...);
    public static *** w(...);
    public static *** e(...);
    public static *** println(...);
}