-dontwarn com.badlogic.gdx.backends.android.AndroidFragmentApplication
-dontwarn com.badlogic.gdx.utils.GdxBuild
-dontwarn com.badlogic.gdx.physics.box2d.utils.Box2DBuild
-dontwarn com.badlogic.gdx.jnigen.**
-keep class com.badlogic.gdx.controllers.android.AndroidControllers
-keepclassmembers class com.badlogic.gdx.backends.android.AndroidInput* { <init>(...); }
-keepclassmembers class com.badlogic.gdx.physics.box2d.World {
   boolean contactFilter(long, long);
   void    beginContact(long);
   void    endContact(long);
   void    preSolve(long, long);
   void    postSolve(long, long);
   boolean reportFixture(long);
   float   reportRayFixture(long, float, float, float, float, float);
}
-keep class com.badlogic.gdx.graphics.g2d.freetype.** { *; }
# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    static ** Companion;
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.besaniyelik.dunya.**$$serializer { *; }
-keepclassmembers class com.besaniyelik.dunya.** { *** Companion; }
-keepclasseswithmembers class com.besaniyelik.dunya.** { kotlinx.serialization.KSerializer serializer(...); }
-keep class com.badlogic.gdx.physics.box2d.** { native <methods>; }
# Cihazda test edilemeyen sürümde yansıma/JNI riskini sıfırlamak için oyun ve libGDX sınıfları korunur.
-keep class com.besaniyelik.dunya.** { *; }
-keep class com.badlogic.gdx.** { *; }
-dontwarn com.badlogic.gdx.**
