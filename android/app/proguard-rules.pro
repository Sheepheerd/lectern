# PDFBox-Android reflects over its font/resource classes.
-dontwarn org.bouncycastle.**
-dontwarn javax.naming.**
# JPEG-2000 support is an optional pdfbox-android dependency we do not ship.
-dontwarn com.gemalto.jp2.**
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }

# Navigation's type-safe routes and the shelf files are kotlinx.serialization
# @Serializable classes; keep their generated serializers.
-keepclassmembers class com.lectern.** {
    *** Companion;
}
-keepclasseswithmembers class com.lectern.** {
    kotlinx.serialization.KSerializer serializer(...);
}
