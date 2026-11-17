import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Saf Kotlin domain katmanı: Android/Firebase bağımlılığı yoktur, JVM'de test edilir.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
}
