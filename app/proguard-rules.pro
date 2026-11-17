# Firestore model sınıfları (data katmanı DTO'ları) yansıma ile okunur.
-keepclassmembers class com.kuaforumcepte.app.**.dto.** { *; }
-keep class com.kuaforumcepte.app.**.dto.** { <init>(); }
# kotlinx.serialization (type-safe navigation route'ları)
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
