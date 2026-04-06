# Keep Kotlin serialization metadata used by JSON export/import flows.
-keepclassmembers class ** {
    *** Companion;
}

-keepclassmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class **$$serializer { *; }

# Keep Room schema classes safe for release shrinking.
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }

# Security-crypto pulls annotation-only errorprone references that are not needed at runtime.
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi
