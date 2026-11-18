plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}
kotlin { jvmToolchain(21) }
dependencies {
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
}
tasks.test {
    useJUnit()
    maxHeapSize = "2g"
    testLogging { events("passed", "failed", "skipped"); showStandardStreams = true; exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}
// Bölüm verisini üretir: ./gradlew :logic:generateLevels
tasks.register<JavaExec>("generateLevels") {
    group = "yanlisrenk"
    description = "500 bölüm + 100 alan verisini android/assets/levels altına yazar"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.yanlisrenk.logic.level.LevelExportKt")
    args(rootProject.file("android/assets/levels").absolutePath)
}
