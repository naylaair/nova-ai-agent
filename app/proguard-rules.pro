-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keep,includedescriptorclasses class com.nova.agent.**$$serializer { *; }
-keepclassmembers class com.nova.agent.** {
    *** Companion;
}
-keepclasseswithmembers class com.nova.agent.** {
    kotlinx.serialization.KSerializer serializer(...);
}
