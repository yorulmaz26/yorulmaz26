-dontwarn com.badlogic.gdx.backends.android.AndroidFragmentApplication
-keep class com.badlogic.gdx.controllers.android.AndroidControllers
-keepclassmembers class com.badlogic.gdx.backends.android.AndroidInput* { <init>(...); }
-keep class com.badlogic.gdx.graphics.g2d.freetype.** { *; }
# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.yeraltiimparatorlugu.** { *** Companion; }
-keepclasseswithmembers class com.yeraltiimparatorlugu.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.yeraltiimparatorlugu.**$$serializer { *; }
