import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
}

val gdxVersion: String by project
val natives: Configuration by configurations.creating

val keystoreProps = Properties().also { p ->
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { p.load(it) }
}

android {
    namespace = "com.yanlisrenk.android"
    compileSdk = 35
    buildFeatures { buildConfig = true }
    defaultConfig {
        applicationId = "com.yanlisrenk.oyun"
        minSdk = 26
        targetSdk = 35
        versionCode = 14
        versionName = "1.3.0"
        // AdMob kimlikleri: gradle.properties ya da -P ile verin. Varsayılanlar Google'ın resmî TEST kimlikleridir.
        fun prop(n: String, d: String) = (project.findProperty(n) as String?) ?: d
        manifestPlaceholders["admobAppId"] = prop("admobAppId", "ca-app-pub-3940256099942544~3347511713")
        buildConfigField("String", "ADMOB_BANNER", "\"" + prop("admobBanner", "ca-app-pub-3940256099942544/9214589741") + "\"")
        buildConfigField("String", "ADMOB_INTERSTITIAL", "\"" + prop("admobInterstitial", "ca-app-pub-3940256099942544/1033173712") + "\"")
        buildConfigField("String", "ADMOB_REWARDED", "\"" + prop("admobRewarded", "ca-app-pub-3940256099942544/5224354917") + "\"")
    }
    sourceSets["main"].apply {
        manifest.srcFile("AndroidManifest.xml")
        java.srcDirs("src/main/kotlin")
        res.srcDirs("res")
        assets.srcDirs("assets")
        jniLibs.srcDirs("libs")
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
    buildTypes {
        getByName("release") {
            // R8 kapalı: libGDX yansıma ile sınıf oluşturur (Pools, Actions, InputEvent);
            // küçültme bu kurucuları silip açılışta çökmeye yol açmıştı. Boyut farkı ~3 MB.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += setOf("META-INF/*.kotlin_module", "META-INF/AL2.0", "META-INF/LGPL2.1") }
}

dependencies {
    implementation(project(":core"))
    implementation("com.badlogicgames.gdx:gdx-backend-android:$gdxVersion")
    implementation("com.google.android.gms:play-services-ads:23.6.0")
    implementation("com.google.android.ump:user-messaging-platform:3.1.0")
    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-armeabi-v7a")
    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-arm64-v8a")
    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-x86")
    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-x86_64")
    natives("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-armeabi-v7a")
    natives("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-arm64-v8a")
    natives("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-x86")
    natives("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-x86_64")
}

// libGDX yerel kütüphanelerini libs/<abi> klasörüne kopyalar
tasks.register("copyAndroidNatives") {
    doFirst {
        natives.files.forEach { jar ->
            val abi = Regex("natives-([^.]+)\\.jar").find(jar.name)?.groupValues?.get(1) ?: return@forEach
            val out = file("libs/$abi"); out.mkdirs()
            copy { from(zipTree(jar)); into(out); include("*.so") }
        }
    }
}
tasks.matching { it.name.contains("merge") && it.name.contains("JniLibFolders") }.configureEach { dependsOn("copyAndroidNatives") }
