plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    // Bölüm doğrulama testleri assets/levels klasörünü okur.
    systemProperty("levels.dir", rootProject.file("assets/levels").absolutePath)
    testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}

tasks.register<JavaExec>("levelTool") {
    group = "tools"
    description = "Bölümleri çözücüyle çözer ve par/çözüm alanlarını doldurur."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.emirzinciri.robotkomutan.logic.tools.LevelToolKt")
    systemProperty("levels.dir", rootProject.file("assets/levels").absolutePath)
    maxHeapSize = "3g"
    jvmArgs("-Dstdout.encoding=UTF-8", "-Dfile.encoding=UTF-8")
}
