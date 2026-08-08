# Room generates implementations reflectively at startup.
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# kotlinx-serialization keeps generated serializers on the companion.
-keepclassmembers class **$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}

# The Xray core AAR is gomobile-generated JNI; its bindings are reached from native code.
-keep class go.** { *; }
-keep class libv2ray.** { *; }
