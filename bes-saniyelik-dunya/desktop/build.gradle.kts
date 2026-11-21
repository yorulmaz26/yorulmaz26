plugins {
    kotlin("jvm")
    application
}
val gdxVersion: String by project
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
kotlin { jvmToolchain(21); compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation(project(":core"))
    implementation("com.badlogicgames.gdx:gdx-backend-lwjgl3:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")
    implementation("com.badlogicgames.gdx:gdx-box2d-platform:$gdxVersion:natives-desktop")
    implementation("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-desktop")
}
application { mainClass.set("com.besaniyelik.dunya.desktop.DesktopLauncherKt") }
tasks.named<JavaExec>("run") { workingDir = rootProject.file("assets"); isIgnoreExitValue = true; listOf("shots", "tourLevels", "tourMenus", "tourEndless", "introGif", "win", "speed", "skipLayers", "insets").forEach { k -> System.getProperty(k)?.let { systemProperty(k, it) } } }
