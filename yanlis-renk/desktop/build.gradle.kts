plugins { kotlin("jvm"); application }
kotlin { jvmToolchain(21) }
val gdxVersion: String by project
dependencies {
    implementation(project(":core"))
    implementation("com.badlogicgames.gdx:gdx-backend-lwjgl3:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")
    implementation("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-desktop")
}
application { mainClass.set("com.yanlisrenk.desktop.DesktopLauncherKt") }
tasks.named<JavaExec>("run") {
    workingDir = rootProject.file("android/assets")
    isIgnoreExitValue = true
}
// Görsel QA: ./gradlew :desktop:screenshots  (Xvfb altında ekran görüntüleri alır)
tasks.register<JavaExec>("screenshots") {
    group = "yanlisrenk"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.yanlisrenk.desktop.ScreenshotRunnerKt")
    workingDir = rootProject.file("android/assets")
    args(rootProject.file("build/screenshots").absolutePath)
}
