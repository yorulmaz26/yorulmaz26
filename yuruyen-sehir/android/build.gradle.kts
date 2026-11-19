plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

import java.util.Properties

val gdxVersion: String by rootProject.extra

// Yayın imzası: anahtar bilgileri depoya girmez. yuruyen-sehir/keystore.properties dosyasından
// (storeFile, storePassword, keyAlias, keyPassword) veya aynı adlı ortam değişkenlerinden okunur.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(key: String): String? = keystoreProps.getProperty(key) ?: System.getenv("YS_" + key.uppercase())
val hasReleaseKey = signingValue("storeFile")?.let { rootProject.file(it).exists() } == true
val natives: Configuration by configurations.creating

android {
    namespace = "com.yuruyensehir.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.yuruyensehir.gocebe"
        minSdk = 24
        targetSdk = 35
        versionCode = 5
        versionName = "1.0.0"
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("AndroidManifest.xml")
            res.srcDirs("res")
            assets.srcDirs(rootProject.file("assets"))
            jniLibs.srcDirs("libs")
        }
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = rootProject.file(signingValue("storeFile")!!)
                storePassword = signingValue("storePassword")
                keyAlias = signingValue("keyAlias")
                keyPassword = signingValue("keyPassword")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Anahtar yoksa yalnızca yerel deneme için hata ayıklama anahtarıyla imzalanır (Play'e yüklenemez).
            signingConfig = if (hasReleaseKey) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += setOf("META-INF/*.kotlin_module", "META-INF/versions/9/previous-compilation-data.bin")
    }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

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
    val outDir = layout.projectDirectory.dir("libs")
    inputs.files(natives)
    outputs.dir(outDir)
    doLast {
        natives.resolvedConfiguration.resolvedArtifacts.forEach { artifact ->
            val abi = artifact.classifier?.removePrefix("natives-") ?: return@forEach
            val target = outDir.dir(abi).asFile
            target.mkdirs()
            project.copy {
                from(project.zipTree(artifact.file))
                into(target)
                include("*.so")
            }
        }
    }
}

tasks.matching { it.name.contains("merge") && it.name.contains("JniLibFolders") }.configureEach {
    dependsOn(copyAndroidNatives)
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(copyAndroidNatives) }
