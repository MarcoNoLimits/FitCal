# Google Play Services Ads ProGuard Rules
-keep public class com.google.android.gms.ads.** {
   public *;
}
-keep public class com.google.ads.** {
   public *;
}
-keep class com.google.android.gms.internal.ads.** { *; }

# Keep JavaScript interfaces for WebViews used by AdMob
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Kotlinx Serialization
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class * {
    *** Companion;
}
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,allowaccessmodification class com.fitcal.** { *; }

# Ktor & Coroutines
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn io.ktor.**


# Google User Messaging Platform (UMP Consent)
-keep class com.google.android.ump.** { *; }
-dontwarn com.google.android.ump.**

# Supabase & Ktor
-dontwarn io.github.jan.supabase.**
-keep class io.github.jan.supabase.** { *; }

# Coil
-dontwarn coil3.**


# Ktor discovers its HTTP engine and serialization providers via ServiceLoader
-keep class io.ktor.client.engine.** { *; }
-keep class io.ktor.serialization.kotlinx.** { *; }
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**

# Keep line numbers in Sentry stack traces (upload mapping.txt to de-obfuscate)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
