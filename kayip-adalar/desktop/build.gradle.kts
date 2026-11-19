plugins {
    kotlin("jvm")
    application
}
val gdxVersion: String by project
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation(project(":core"))
    implementation("com.badlogicgames.gdx:gdx-backend-lwjgl3:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")
    implementation("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-desktop")
    implementation("com.badlogicgames.gdx:gdx-tools:$gdxVersion")
}

tasks.register<JavaExec>("packSprites") {
    group = "kayip adalar"
    description = "assets/game PNG dosyalarını TextureAtlas olarak paketler."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.kayipadalar.desktop.PackSpritesKt")
    args(rootProject.file("assets/game").absolutePath, rootProject.file("assets/atlas").absolutePath)
}
application { mainClass.set("com.kayipadalar.desktop.DesktopLauncherKt") }
tasks.named<JavaExec>("run") {
    workingDir = rootProject.file("assets")
    isIgnoreExitValue = true
}
