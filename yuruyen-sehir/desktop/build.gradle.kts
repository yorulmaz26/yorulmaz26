plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

val gdxVersion: String by rootProject.extra

dependencies {
    implementation(project(":core"))
    implementation("com.badlogicgames.gdx:gdx-backend-lwjgl3:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")
    implementation("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-desktop")
}

sourceSets["main"].resources.srcDir(rootProject.file("assets"))

application {
    mainClass.set("com.yuruyensehir.desktop.DesktopLauncherKt")
}

tasks.named<JavaExec>("run") {
    workingDir = layout.buildDirectory.dir("run").get().asFile.also { it.mkdirs() }
    isIgnoreExitValue = true
}

tasks.register<JavaExec>("screenshots") {
    group = "verification"
    description = "Oyunu Xvfb/masaüstünde çalıştırıp ekran görüntüleri alır."
    mainClass.set("com.yuruyensehir.desktop.DesktopLauncherKt")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = layout.buildDirectory.dir("run").get().asFile.also { it.mkdirs() }
    val out = rootProject.layout.projectDirectory.dir("screenshots-local").asFile
    systemProperty("ys.shots", out.absolutePath)
    System.getProperty("ys.size")?.let { systemProperty("ys.size", it) }
    doFirst { out.mkdirs() }
}
