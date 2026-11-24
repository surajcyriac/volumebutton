# R8 optimization rules for maximum memory and binary footprint reduction
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Strip Kotlin metadata and debug info
-dontnote **
-dontwarn **
-repackageclasses
-allowaccessmodification

-keepclassmembers class * {
    @androidx.annotation.Keep <fields>;
    @androidx.annotation.Keep <methods>;
}
