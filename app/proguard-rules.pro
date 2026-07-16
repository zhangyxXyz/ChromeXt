-keep class org.matrix.chromext.MainHook { *; }
-keepattributes SourceFile,LineNumberTable

# Optional OEM WindowManager extensions are discovered reflectively at runtime.
-dontwarn androidx.window.extensions.**
-dontwarn androidx.window.sidecar.**
