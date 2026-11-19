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
    System.getProperty("genIds")?.let { systemProperty("genIds", it) }
    System.getProperty("debugLevel")?.let { systemProperty("debugLevel", it) }
    maxHeapSize = "2g"
}

tasks.register<JavaExec>("generateLevels") {
    group = "kayip adalar"
    description = "Alan 2-100 bölüm dosyalarını üretir ve çözülebilirliği doğrular."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.kayipadalar.logic.tools.GenerateLevelsKt")
    args(rootProject.file("assets").absolutePath, (project.findProperty("areas") ?: project.findProperty("fromArea") ?: "2").toString(), (project.findProperty("toArea") ?: "100").toString())
    maxHeapSize = "3g"
}
