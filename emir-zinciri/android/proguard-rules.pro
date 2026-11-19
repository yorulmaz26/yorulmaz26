-verbose
-dontwarn com.badlogic.gdx.backends.android.AndroidFragmentApplication
-keep class com.badlogic.gdx.controllers.android.AndroidControllers
-keepclassmembers class com.badlogic.gdx.backends.android.AndroidInput* {
   <init>(com.badlogic.gdx.Application, android.content.Context, java.lang.Object, com.badlogic.gdx.backends.android.AndroidApplicationConfiguration);
}
-keepclassmembers class com.badlogic.gdx.physics.box2d.World { *; }
# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.emirzinciri.robotkomutan.** {
    *** Companion;
}
-keepclasseswithmembers class com.emirzinciri.robotkomutan.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.emirzinciri.robotkomutan.**$$serializer { *; }
