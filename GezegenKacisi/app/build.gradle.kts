plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// --- AdMob reklam kimlikleri ---
// Şu an Google'ın herkese açık TEST kimlikleri kullanılıyor (reklamlarda "Test Ad" yazar, gelir getirmez).
// Yayından önce AdMob hesabınızdaki kendi uygulama ve reklam birimi kimliklerinizle değiştirin.
val admobAppId = "ca-app-pub-3940256099942544~3347511713"
val adBannerId = "ca-app-pub-3940256099942544/9214589741"       // uyarlanabilir banner
val adInterstitialId = "ca-app-pub-3940256099942544/1033173712" // geçiş (resim) reklamı
val adRewardedId = "ca-app-pub-3940256099942544/5224354917"     // ödüllü video

android {
    namespace = "com.example.gezegenkacisi"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.gezegenkacisi"
        minSdk = 26
        targetSdk = 35
        versionCode = 12
        versionName = "2.2"

        manifestPlaceholders["admobAppId"] = admobAppId
        resValue("string", "ad_banner_id", adBannerId)
        resValue("string", "ad_interstitial_id", adInterstitialId)
        resValue("string", "ad_rewarded_id", adRewardedId)
    }

    // Sabit test imzası: her derlemede aynı anahtar kullanılır, böylece yeni sürüm eskisinin üstüne kurulur.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

// Oyun tamamen Android Canvas ile çiziliyor; tek dış kütüphaneler reklam (AdMob) ve reklam izni (UMP).
dependencies {
    implementation("com.google.android.gms:play-services-ads:24.9.0")
    implementation("com.google.android.ump:user-messaging-platform:3.2.0")
}
