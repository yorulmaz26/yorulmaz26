import java.util.Properties

plugins {
    id("com.android.application")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

// Firebase: android/app/google-services.json varsa bağlanır. Yoksa uygulama
// çevrimdışı açılır (yalnızca çevirici ve öğrenme modülü).
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

// Yayın imzası: android/key.properties (depoya EKLENMEZ, bkz. docs/RELEASE.md).
val keyProps = Properties().apply {
    val f = rootProject.file("key.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.kusdili.kus_dili"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        applicationId = "com.kusdili.kus_dili"
        // Biyometri ve güvenli depo eklentileri en az Android 7.0 ister.
        minSdk = maxOf(24, flutter.minSdkVersion)
        targetSdk = flutter.targetSdkVersion
        // Uses the version code from pubspec.yaml. When using split APKs, 1000 * ABI_VERSION
        // is added automatically by Flutter. (https://developer.android.com/studio/build/configure-apk-splits#configure-APK-versions)
        // You can force using the value of versionCode by specifying the `-P force-version-code-ignoring-abi=true`
        // flag during build.
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    signingConfigs {
        // YALNIZCA TEST: depoda duran, herkesin bildiği test anahtarı. Parmak
        // izi sabit olsun diye (Firebase telefonla giriş SHA-1/SHA-256 ister)
        // test derlemeleri bununla imzalanır. Mağaza için KULLANILMAZ.
        create("test") {
            storeFile = file("test-signing.p12")
            storePassword = "kusdili-test"
            keyAlias = "test"
            keyPassword = "kusdili-test"
            storeType = "PKCS12"
        }
        if (keyProps.isNotEmpty()) {
            create("release") {
                storeFile = file(keyProps.getProperty("storeFile"))
                storePassword = keyProps.getProperty("storePassword")
                keyAlias = keyProps.getProperty("keyAlias")
                keyPassword = keyProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("test")
        }
        release {
            // key.properties yoksa (ör. CI) test anahtarıyla imzalanır;
            // böyle bir paket mağazaya YÜKLENMEMELİ.
            signingConfig = if (keyProps.isNotEmpty()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("test")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

flutter {
    source = "../.."
}
