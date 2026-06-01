# Genesis Emulator ProGuard Rules
-keep class com.dune2emu.RetroBridge { *; }
-keep class * implements java.io.Serializable { *; }

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# GLES
-keep class javax.microedition.khronos.egl.** { *; }
-keep class javax.microedition.khronos.opengles.** { *; }
