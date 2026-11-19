plugins {
    id("com.android.application")
    kotlin("android")
}
val gdxVersion: String by project
val natives: Configuration by configurations.creating

android {
    namespace = "com.kayipadalar.android"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.kayipadalar.hareketlikrallik"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.4.0"
    }
    sourceSets["main"].apply {
        manifest.srcFile("AndroidManifest.xml")
        res.srcDirs("res")
        assets.srcDirs(rootProject.file("assets"))
        jniLibs.srcDirs("libs")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation("com.badlogicgames.gdx:gdx-backend-android:$gdxVersion")
    listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64").forEach { abi ->
        natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-$abi")
        natives("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-$abi")
    }
}

// libGDX yerel kütüphanelerini jniLibs klasörüne kopyalar.
val copyAndroidNatives by tasks.registering {
    doFirst {
        natives.files.forEach { jar ->
            val abi = jar.nameWithoutExtension.substringAfter("natives-")
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
tasks.matching { it.name.contains("merge") && it.name.contains("JniLibFolders") }.configureEach {
    dependsOn(copyAndroidNatives)
}
