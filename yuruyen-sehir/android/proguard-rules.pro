# libGDX: yerel (JNI) yöntemler ve yansıma ile oluşturulan arka uç sınıfları
-keepclasseswithmembernames class * { native <methods>; }
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
