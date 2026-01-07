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

# Keep Koin
-keep class org.koin.** { *; }
