plugins {
    id("com.android.application")
    kotlin("android")
}

import java.util.Properties

val gdxVersion: String by project
val natives: Configuration by configurations.creating

// Yayın imzası: emir-zinciri/keystore.properties (depoya EKLENMEZ). Örnek: keystore.properties.example
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.isFile) f.inputStream().use { load(it) }
}

android {
    namespace = "com.emirzinciri.robotkomutan"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.emirzinciri.robotkomutan"
        minSdk = 24
        targetSdk = 35
        versionCode = 5
        versionName = "1.1.0"
    }

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) create("release") {
            storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
            storePassword = keystoreProps.getProperty("storePassword")
            keyAlias = keystoreProps.getProperty("keyAlias")
            keyPassword = keystoreProps.getProperty("keyPassword")
        }
    }

    sourceSets["main"].apply {
        assets.srcDirs(rootProject.file("assets"))
        jniLibs.srcDirs("libs")
    }

    buildTypes {
        release {
            // R8 küçültmesi, gerçek cihazda doğrulanana kadar kapalı (kotlinx.serialization kuralları proguard-rules.pro'da hazır).
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":core"))
    implementation("com.badlogicgames.gdx:gdx-backend-android:$gdxVersion")
    for (abi in listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")) {
        natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-$abi")
        natives("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-$abi")
    }
}

// libGDX yerel kütüphanelerini (.so) jniLibs klasörüne kopyalar.
val copyAndroidNatives by tasks.registering {
    val outDir = file("libs")
    inputs.files(natives)
    outputs.dir(outDir)
    doLast {
        natives.files.forEach { jar ->
            val abi = when {
                jar.name.endsWith("natives-armeabi-v7a.jar") -> "armeabi-v7a"
                jar.name.endsWith("natives-arm64-v8a.jar") -> "arm64-v8a"
                jar.name.endsWith("natives-x86_64.jar") -> "x86_64"
                jar.name.endsWith("natives-x86.jar") -> "x86"
                else -> return@forEach
            }
            copy {
                from(zipTree(jar))
                into(File(outDir, abi))
                include("*.so")
            }
        }
    }
}
tasks.matching { it.name.contains("merge") && it.name.contains("JniLibFolders") }.configureEach {
    dependsOn(copyAndroidNatives)
}
