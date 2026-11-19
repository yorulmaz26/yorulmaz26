plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val gdxVersion: String by rootProject.extra
val natives: Configuration by configurations.creating

android {
    namespace = "com.zamaninefendisi.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zamaninefendisi.ucag"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    sourceSets {
        getByName("main") {
            assets.srcDirs(rootProject.file("assets"))
            jniLibs.srcDirs("libs")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
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
    implementation("com.badlogicgames.gdx:gdx-backend-android:$gdxVersion")
    for (abi in listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")) {
        natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-$abi")
        natives("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-$abi")
    }
}

// libGDX yerel (.so) kütüphanelerini jar'lardan çıkarıp android/libs/<abi>/ altına kopyalar.
val copyAndroidNatives by tasks.registering {
    inputs.files(natives)
    outputs.dir("libs")
    doLast {
        natives.files.forEach { jar ->
            val abi = jar.nameWithoutExtension.substringAfterLast("natives-")
            val outputDir = file("libs/$abi")
            outputDir.mkdirs()
            project.copy {
                from(project.zipTree(jar))
                into(outputDir)
                include("*.so")
            }
        }
    }
}
tasks.matching { it.name.contains("merge") && it.name.contains("JniLibFolders") }.configureEach {
    dependsOn(copyAndroidNatives)
}
