# kotlinx.serialization: keep generated serializers for the sync messages.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.godskingdomnow.groceryaccountant.sync.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.godskingdomnow.groceryaccountant.sync.**$$serializer { *; }
