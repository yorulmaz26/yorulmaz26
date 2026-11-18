# Kuş Dili — R8 koruma kuralları (yayın derlemesi küçültülürken silinmesin).

# WebRTC (flutter_webrtc) yerel kodu JNI ile çağırır.
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# Biyometri istemi
-keep class androidx.biometric.** { *; }
