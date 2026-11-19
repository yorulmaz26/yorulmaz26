plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
}
tasks.test {
    useJUnit()
    systemProperty("assets.dir", rootProject.file("assets").absolutePath)
    maxHeapSize = "2g"
}
