# libGDX
-dontwarn com.badlogic.gdx.backends.android.AndroidFragmentApplication
-keep class com.badlogic.gdx.controllers.android.AndroidControllers
-keepclassmembers class com.badlogic.gdx.backends.android.AndroidInput* { <init>(...); }
-keepclassmembers class com.badlogic.gdx.physics.box2d.World { *; }
-keep class com.badlogic.gdx.graphics.g2d.freetype.** { *; }
# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.zamaninefendisi.logic.** { *** Companion; }
-keepclasseswithmembers class com.zamaninefendisi.logic.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.zamaninefendisi.logic.**$$serializer { *; }
