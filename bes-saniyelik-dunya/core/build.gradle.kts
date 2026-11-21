plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

val gdxVersion: String by project

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { jvmToolchain(21); compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    api("com.badlogicgames.gdx:gdx:$gdxVersion")
    api("com.badlogicgames.gdx:gdx-box2d:$gdxVersion")
    api("com.badlogicgames.gdx:gdx-freetype:$gdxVersion")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.badlogicgames.gdx:gdx-backend-headless:$gdxVersion")
    testImplementation("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")
    testImplementation("com.badlogicgames.gdx:gdx-box2d-platform:$gdxVersion:natives-desktop")
}

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "3g"
    workingDir = rootProject.file("assets")
    systemProperty("assetsDir", rootProject.file("assets").absolutePath)
    testLogging { events("failed"); showStandardStreams = true; exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}

// Bölüm üretici: 500 bölümü üretir, her birini çözücüyle doğrular ve assets/levels altına yazar.
tasks.register<JavaExec>("generateLevels") {
    group = "content"
    dependsOn("testClasses")
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.besaniyelik.dunya.tools.LevelBuildTool")
    jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
    workingDir = rootProject.file("assets")
    args = listOf(rootProject.file("assets/levels").absolutePath, (project.findProperty("levels") ?: "1-500").toString(),
        (project.findProperty("offset") ?: "0").toString(), (project.findProperty("improve") ?: "0").toString(),
        (project.findProperty("restamp") ?: "0").toString())
    maxHeapSize = "3g"
}

// Zorluk analizi: her bölümü gürültülü (insan benzeri) girişlerle oynatıp ölçer.
tasks.register<JavaExec>("analyzeLevels") {
    group = "content"
    dependsOn("testClasses")
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.besaniyelik.dunya.tools.LevelAnalyzeTool")
    jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
    workingDir = rootProject.file("assets")
    args = listOf(rootProject.file("assets").absolutePath, (project.findProperty("levels") ?: "1-500").toString(),
        (project.findProperty("humanize") ?: "0").toString(), (project.findProperty("out") ?: rootProject.file("docs/difficulty_report.tsv").absolutePath).toString(),
        (project.findProperty("dump") ?: "-").toString())
    maxHeapSize = "3g"
}

tasks.register<JavaExec>("tightSpots") {
    dependsOn("testClasses"); classpath = sourceSets["test"].runtimeClasspath; mainClass.set("com.besaniyelik.dunya.tools.TightSpots")
    jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8"); maxHeapSize = "3g"
    args = listOf(rootProject.file("assets").absolutePath, (project.findProperty("levels") ?: "1-500").toString(), (project.findProperty("out") ?: "/tmp/tight.tsv").toString())
}
tasks.register<JavaExec>("probeAttempt") {
    dependsOn("testClasses"); classpath = sourceSets["test"].runtimeClasspath; mainClass.set("com.besaniyelik.dunya.tools.ProbeAttempt")
    jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8"); maxHeapSize = "3g"
    args = (project.findProperty("a") ?: "1 GAP /tmp/probe").toString().split(" ")
}
tasks.register<JavaExec>("draftStats") {
    dependsOn("testClasses"); classpath = sourceSets["test"].runtimeClasspath; mainClass.set("com.besaniyelik.dunya.tools.DraftStats")
    jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
    args = listOf((project.findProperty("attempts") ?: "3").toString()); systemProperty("show", (project.findProperty("show") ?: "kayan").toString())
}
tasks.register<JavaExec>("probeUnsolved") {
    dependsOn("testClasses"); classpath = sourceSets["test"].runtimeClasspath; mainClass.set("com.besaniyelik.dunya.tools.ProbeUnsolved")
    jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8"); maxHeapSize = "3g"
    args = (project.findProperty("a") ?: "300 20 0.3").toString().split(" ")
}
tasks.register<JavaExec>("draftDump") {
    dependsOn("testClasses"); classpath = sourceSets["test"].runtimeClasspath; mainClass.set("com.besaniyelik.dunya.tools.DraftDump")
    jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8"); maxHeapSize = "3g"
    args = (project.findProperty("a") ?: "1 0 0").toString().split(" ")
}
tasks.register<JavaExec>("probe2") {
    dependsOn("testClasses"); classpath = sourceSets["test"].runtimeClasspath; mainClass.set("com.besaniyelik.dunya.tools.Probe2"); args = listOf((project.findProperty("lv") ?: "1").toString())
}
tasks.register<JavaExec>("probe") {
    jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
    dependsOn("testClasses")
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.besaniyelik.dunya.tools.Probe")
    args = listOf((project.findProperty("lv") ?: "1").toString())
}
