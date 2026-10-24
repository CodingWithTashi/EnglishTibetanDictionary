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

# Room: Room.databaseBuilder(context, X::class.java, ...) loads X_Impl by name. room-runtime's consumer rule
# (-keep class * extends androidx.room.RoomDatabase { void <init>(); }) keeps both names and the constructor;
# entities and DAOs are only reached through generated code, so nothing more is needed here.
# Hilt and Firebase (Analytics, Crashlytics, Auth, Functions, App Check) ship consumer rules as well.

# Credential Manager loads its Play services provider by reflection.
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** { *; }
