// libGDX oyun katmanı: çizim, giriş, arayüz. Simülasyonu :sim modülünden kullanır.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    api(project(":sim"))
    api(libs.gdx)
    api(libs.gdx.freetype)
}
