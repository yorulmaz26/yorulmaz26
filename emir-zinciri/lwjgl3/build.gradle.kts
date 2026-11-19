plugins {
    kotlin("jvm")
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

val gdxVersion: String by project

dependencies {
    implementation(project(":core"))
    implementation("com.badlogicgames.gdx:gdx-backend-lwjgl3:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")
    implementation("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-desktop")
}

application {
    mainClass.set("com.emirzinciri.robotkomutan.desktop.DesktopLauncherKt")
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.file("assets")
    isIgnoreExitValue = true
    // Örn: ./gradlew :lwjgl3:run --args="--screenshot level:1"
}
