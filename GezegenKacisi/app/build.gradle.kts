plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.gezegenkacisi"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.gezegenkacisi"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "1.6"
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

// Harici kütüphane yok: oyun tamamen Android Canvas ile çiziliyor.
