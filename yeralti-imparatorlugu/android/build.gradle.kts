// Android başlatıcı (dikey 9:16, offline). Oyunun tamamı :core ve :sim modüllerindedir.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

import java.util.Properties

val natives: Configuration by configurations.creating
// Yayın imzası: proje kökünde keystore.properties varsa kullanılır (depoya konmaz). Örnek: keystore.properties.example
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val abis = listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
val nativesDir = layout.buildDirectory.dir("gdx-natives")

android {
    namespace = "com.yeraltiimparatorlugu.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.yeraltiimparatorlugu"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.0"
    }

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    sourceSets["main"].apply {
        manifest.srcFile("AndroidManifest.xml")
        res.srcDirs("res")
        assets.srcDirs(rootProject.file("assets"))
        jniLibs.srcDirs(nativesDir)
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfigs.findByName("release")?.let { signingConfig = it }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation(project(":core"))
    implementation(libs.gdx.backend.android)
    abis.forEach { abi ->
        natives(variantOf(libs.gdx.platform) { classifier("natives-$abi") })
        natives(variantOf(libs.gdx.freetype.platform) { classifier("natives-$abi") })
    }
}

// libGDX yerel kütüphanelerini (.so) jniLibs klasörüne çıkarır.
val copyAndroidNatives by tasks.registering(Copy::class) {
    val files = natives
    into(nativesDir)
    files.forEach { jar ->
        val abi = abis.firstOrNull { jar.name.endsWith("natives-$it.jar") } ?: return@forEach
        from(zipTree(jar)) {
            include("*.so")
            into(abi)
        }
    }
}

tasks.matching { it.name.contains("merge") && it.name.contains("JniLibFolders") }.configureEach {
    dependsOn(copyAndroidNatives)
}
