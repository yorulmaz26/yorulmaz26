import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
}

val gdxVersion: String by project
val natives: Configuration by configurations.creating

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// Reklam ayarları: ads.properties (AdMob kimlikleri ve hedef kitle). Dosya ya da bir satır yoksa Google'ın resmi
// DENEME kimlikleri kullanılır: uygulama yalnızca "Test Ad" etiketli deneme reklamı gösterir, gelir oluşmaz.
val adsProps = Properties().apply {
    val f = rootProject.file("ads.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun adProp(key: String, def: String) = (adsProps.getProperty(key) ?: "").trim().ifEmpty { def }
val testAppId = "ca-app-pub-3940256099942544~3347511713"
val admobAppId = adProp("admobAppId", testAppId)
val rewardedUnitId = adProp("rewardedUnitId", "ca-app-pub-3940256099942544/5224354917")
val interstitialUnitId = adProp("interstitialUnitId", "ca-app-pub-3940256099942544/1033173712")
/** "child": kitle çocukları da kapsar (varsayılan, güvenli); "general": yalnızca 13 yaş üstü. */
val adsChildAudience = adProp("audience", "child") != "general"
val adsTestDevices = adProp("testDeviceIds", "")
/** "off": geçiş reklamları tümüyle kapalı (yalnızca isteğe bağlı ödüllü reklamlar kalır). */
val adsInterstitials = adProp("interstitials", "on") != "off"
val usingTestAds = listOf(admobAppId, rewardedUnitId, interstitialUnitId).any { it.startsWith("ca-app-pub-3940256099942544") }

android {
    namespace = "com.besaniyelik.dunya"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.besaniyelik.dunya"
        minSdk = 24 // Google Mobile Ads 25.x en az Android 7.0 (API 24) ister
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        manifestPlaceholders["admobAppId"] = admobAppId
        buildConfigField("String", "AD_REWARDED_UNIT", "\"$rewardedUnitId\"")
        buildConfigField("String", "AD_INTERSTITIAL_UNIT", "\"$interstitialUnitId\"")
        buildConfigField("boolean", "ADS_CHILD_AUDIENCE", adsChildAudience.toString())
        buildConfigField("String", "ADS_TEST_DEVICES", "\"$adsTestDevices\"")
        buildConfigField("boolean", "ADS_INTERSTITIALS", adsInterstitials.toString())
    }
    buildFeatures { buildConfig = true }

    sourceSets["main"].apply {
        assets.srcDirs(rootProject.file("assets"))
        jniLibs.srcDirs("libs")
        res.srcDirs("res")
        manifest.srcFile("AndroidManifest.xml")
    }

    // Çocukları da kapsayan kitlede reklam kimliği izni pakete hiç girmez (Aileler Politikası)
    if (adsChildAudience) {
        sourceSets["release"].manifest.srcFile("manifest-child/AndroidManifest.xml")
        sourceSets["debug"].manifest.srcFile("manifest-child/AndroidManifest.xml")
    }

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("upload") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    androidResources { ignoreAssetsPatterns.add("!save") }
    packaging { resources.excludes += setOf("META-INF/*.kotlin_module", "META-INF/versions/**") }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation(project(":core"))
    implementation("com.badlogicgames.gdx:gdx-backend-android:$gdxVersion")
    // Reklam: Google Mobile Ads (AdMob) + kullanıcı onayı (UMP). Bütün kullanım AndroidAds.kt içindedir.
    implementation("com.google.android.gms:play-services-ads:25.5.0")
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")
    for (abi in listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")) {
        natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-$abi")
        natives("com.badlogicgames.gdx:gdx-box2d-platform:$gdxVersion:natives-$abi")
        natives("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-$abi")
    }
}

// libGDX yerel kütüphanelerini (libgdx.so, libgdx-box2d.so, libgdx-freetype.so) libs/<abi> altına kopyalar.
val copyAndroidNatives by tasks.registering {
    doFirst {
        natives.files.forEach { jar ->
            val abi = Regex("natives-([^.]+)\\.jar").find(jar.name)?.groupValues?.get(1) ?: return@forEach
            val outDir = file("libs/$abi")
            outDir.mkdirs()
            copy {
                from(zipTree(jar))
                into(outDir)
                include("*.so")
            }
        }
    }
}
tasks.matching { it.name.contains("merge") && it.name.contains("JniLibFolders") }.configureEach { dependsOn(copyAndroidNatives) }
tasks.named("preBuild") { dependsOn(copyAndroidNatives) }

// Deneme reklam kimlikleriyle yayın paketi üretilirse açıkça uyar (gerçek kimlikler ads.properties dosyasına yazılır).
tasks.matching { it.name == "bundleRelease" || it.name == "assembleRelease" }.configureEach {
    doFirst {
        if (usingTestAds) logger.warn("UYARI: ads.properties içinde gerçek AdMob kimlikleri yok; bu paket yalnızca DENEME reklamı gösterir (gelir oluşmaz).")
        logger.lifecycle("Reklam kitlesi: " + (if (adsChildAudience) "child (çocukları da kapsar; kişiselleştirilmemiş, G içerik)" else "general (13 yaş üstü; içerik sınırı PG)") + "; geçiş reklamları: " + (if (adsInterstitials) "açık" else "kapalı"))
    }
}
