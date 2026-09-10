# Keep OkHttp / okio platform-optional warnings quiet
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Glance uses reflection for widget providers
-keep class androidx.glance.** { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }

# OkHttp: keep platform adapters
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Tink (via security-crypto) references errorprone annotations at compile time only
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi
