plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val gdxVersion: String by rootProject.extra
val natives: Configuration by configurations.creating

android {
    namespace = "com.yuruyensehir.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.yuruyensehir.gocebe"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("AndroidManifest.xml")
            res.srcDirs("res")
            assets.srcDirs(rootProject.file("assets"))
            jniLibs.srcDirs("libs")
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
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
