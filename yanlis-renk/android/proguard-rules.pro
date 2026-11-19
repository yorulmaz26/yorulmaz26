-verbose
-dontwarn com.badlogic.gdx.backends.android.AndroidFragmentApplication
-dontwarn com.badlogic.gdx.utils.GdxBuild
-dontwarn com.badlogic.gdx.jnigen.**
-keep class com.badlogic.gdx.controllers.android.AndroidControllers
-keepclassmembers class com.badlogic.gdx.backends.android.AndroidInput* { <init>(...); }
-keepclassmembers class com.badlogic.gdx.physics.box2d.World { *; }
-keep class com.badlogic.gdx.graphics.g2d.freetype.** { *; }
-keepclasseswithmembernames class * { native <methods>; }
# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses, Signature
-keep,includedescriptorclasses class com.yanlisrenk.**$$serializer { *; }
-keepclassmembers class com.yanlisrenk.** { *** Companion; }
-keepclasseswithmembers class com.yanlisrenk.** { kotlinx.serialization.KSerializer serializer(...); }
# GameSession.resetProgress yansıma ile SaveData alanlarını kopyalar
-keep class com.yanlisrenk.logic.save.SaveData { *; }
