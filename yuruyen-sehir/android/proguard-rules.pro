# libGDX: yerel (JNI) yöntemler ve yansıma ile oluşturulan arka uç sınıfları
-keepclasseswithmembernames class * { native <methods>; }
# libGDX Pools/ReflectionPool, Scene2D olay ve eylemlerini yansıma ile parametresiz kurucudan oluşturur;
# R8 bu kurucuları silerse sürüm paketi açılışta çöker (CI emülatör testinde yakalandı). libGDX tümüyle korunur.
-keep class com.badlogic.gdx.** { *; }
-keepclassmembers class * extends com.badlogic.gdx.utils.Pool$Poolable { <init>(); }
-keepclassmembers class * extends com.badlogic.gdx.scenes.scene2d.Event { <init>(); }
-keepclassmembers class * extends com.badlogic.gdx.scenes.scene2d.Action { <init>(); }
-keep class com.badlogic.gdx.backends.android.** { *; }
-keep class com.badlogic.gdx.graphics.g2d.freetype.** { *; }
-keep class com.badlogic.gdx.utils.GdxNativesLoader { *; }
-keep class com.badlogic.gdx.utils.SharedLibraryLoader { *; }
-keep class com.badlogic.gdx.utils.BufferUtils { *; }
-keep class com.badlogic.gdx.math.Matrix4 { *; }
-dontwarn com.badlogic.gdx.**

# Oyun mantığı: kotlinx.serialization ile kaydedilen sınıflar ve enum adları kayıt biçiminin parçasıdır.
-keep class com.yuruyensehir.logic.** { *; }
-keepclassmembers class com.yuruyensehir.logic.** { *** Companion; kotlinx.serialization.KSerializer serializer(...); }
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-dontnote kotlinx.serialization.**
-dontwarn kotlinx.serialization.**

# Çökme raporlarında satır numaraları
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
