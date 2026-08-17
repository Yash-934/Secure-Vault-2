# ============================================================================
# PRODUCTION HARDENING & ANTI-TAMPERING PROGUARD / R8 OBFUSCATION RULES
# ============================================================================

# ----------------------------------------------------------------------------
# 1. OPTIMIZATION & AGGRESSIVE OBFUSCATION
# ----------------------------------------------------------------------------
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-dontskipnonpubliclibraryclassmembers
-allowaccessmodification

# Hide source file names and line numbers in production traces
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

# Preserve runtime annotations and generic signatures for Jetpack Compose & Room
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# ----------------------------------------------------------------------------
# 2. HEAVY OBFUSCATION FOR CRYPTO, KEYSTORE, AND SECURITY LOGIC
# ----------------------------------------------------------------------------
# Heavily obfuscate internal security methods and names while keeping class references intact if needed
-keepclassmembers class com.example.security.** {
    public private protected *;
}

# Preserve Native Cryptographic & Keystore Methods
-keepclasseswithmembernames class * {
    native <methods>;
}

-keep class javax.crypto.** { *; }
-keep class java.security.** { *; }
-keep class android.security.keystore.** { *; }

# ----------------------------------------------------------------------------
# 3. JETPACK ROOM DATABASE ENTITIES & DAOS
# ----------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.paging.**

# Keep Data Models / Entities explicitly
-keep class com.example.data.VaultItem { *; }
-keep class com.example.data.IntruderLog { *; }
-keep class com.example.data.local.VaultSettings { *; }

# ----------------------------------------------------------------------------
# 4. WORKMANAGER & BACKGROUND WORKERS
# ----------------------------------------------------------------------------
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class com.example.security.DeadManSwitchWorker { *; }

# ----------------------------------------------------------------------------
# 5. JETPACK COMPOSE & VIEWMODELS
# ----------------------------------------------------------------------------
-keep class com.example.ui.VaultViewModel { *; }
-keep class com.example.ui.viewmodel.SettingsViewModel { *; }
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    public <init>(...);
}

# Preserve Compose Recomposition Lambdas & State
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}

# ----------------------------------------------------------------------------
# 6. CAMERAX & BIOMETRICS
# ----------------------------------------------------------------------------
-keep class androidx.camera.core.** { *; }
-keep class androidx.camera.camera2.** { *; }
-keep class androidx.biometric.** { *; }

# ----------------------------------------------------------------------------
# 7. LOG STRIPPING (PURGE DEBUG LOGS FROM PRODUCTION APK)
# ----------------------------------------------------------------------------
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}
