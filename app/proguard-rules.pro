# kotlinx.serialization (rule packs, backups)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keep,includedescriptorclasses class app.arthik.**$$serializer { *; }
-keepclassmembers class app.arthik.** { *** Companion; }
-keepclasseswithmembers class app.arthik.** { kotlinx.serialization.KSerializer serializer(...); }

# SQLCipher uses JNI
-keep class net.zetetic.database.** { *; }
-keep class net.zetetic.database.sqlcipher.** { *; }
