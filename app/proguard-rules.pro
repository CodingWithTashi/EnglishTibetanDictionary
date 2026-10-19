# Crashlytics: readable stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.kharagedition.tibetandictionary.**$$serializer { *; }
-keepclassmembers class com.kharagedition.tibetandictionary.** {
    *** Companion;
}
-keepclasseswithmembers class com.kharagedition.tibetandictionary.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Type-safe navigation routes (serialized by class name)
-keep class com.kharagedition.tibetandictionary.navigation.** { *; }

# Room, Hilt and Firebase ship consumer rules.
