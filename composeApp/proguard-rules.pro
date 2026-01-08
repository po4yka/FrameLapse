# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.po4yka.framelapse.**$$serializer { *; }
-keepclassmembers class com.po4yka.framelapse.** {
    *** Companion;
}
-keepclasseswithmembers class com.po4yka.framelapse.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep SQLDelight
-keep class com.po4yka.framelapse.data.local.** { *; }
-keep class app.cash.sqldelight.** { *; }
-keep interface app.cash.sqldelight.** { *; }

# Keep Koin
-keep class org.koin.** { *; }

# ==================== MediaPipe ====================
# Keep MediaPipe core classes
-keep class com.google.mediapipe.** { *; }
-keep class com.google.mediapipe.tasks.** { *; }
-keep class com.google.mediapipe.framework.** { *; }
-keep class com.google.mediapipe.components.** { *; }

# Keep MediaPipe native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep protobuf classes used by MediaPipe
-keep class com.google.protobuf.** { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}

# ==================== CameraX ====================
# Keep CameraX classes
-keep class androidx.camera.** { *; }
-keep interface androidx.camera.** { *; }

# Keep Camera2 interop classes
-keep class androidx.camera.camera2.** { *; }

# ==================== Jetpack Compose ====================
# Keep Compose runtime
-keep class androidx.compose.runtime.** { *; }
-keepclassmembers class androidx.compose.runtime.** { *; }

# Keep Compose UI
-keep class androidx.compose.ui.** { *; }

# Keep Compose stability annotations
-keep class androidx.compose.runtime.Stable { *; }
-keep class androidx.compose.runtime.Immutable { *; }

# Keep Composable functions metadata
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# Keep Compose Material3
-keep class androidx.compose.material3.** { *; }

# ==================== Lifecycle & ViewModel ====================
-keep class androidx.lifecycle.** { *; }
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * extends androidx.lifecycle.AndroidViewModel { *; }

# ==================== Coroutines ====================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keep class kotlinx.coroutines.android.** { *; }

# ==================== General ====================
# Keep R8 from stripping interface methods
-keep,allowoptimization class * implements ** {
    <methods>;
}

# Keep source file names and line numbers for better crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep annotations
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
