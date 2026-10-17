# kotlinx.serialization: keep generated serializers for @Serializable classes (catalog JSON,
# Navigation 3 keys, export files).
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class **$$serializer { *; }
-keep,includedescriptorclasses class dev.quinton.goa.**$$serializer { *; }
