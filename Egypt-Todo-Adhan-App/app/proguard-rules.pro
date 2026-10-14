# Destination: app/proguard-rules.pro
# R8 / ProGuard rules for the offline prayer-times app (release builds).

# Preserve generic signatures and annotations (needed by Room + kotlinx).
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# --- Adhan (com.batoulapps.adhan) ---
# Pure-Java library that uses reflection-friendly data classes; keep it whole.
-keep class com.batoulapps.adhan.** { *; }
-dontwarn com.batoulapps.adhan.**

# --- Room ---
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# --- Kotlin Coroutines ---
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# --- Kotlin metadata (so reflection on Kotlin types keeps working) ---
-keep class kotlin.Metadata { *; }
