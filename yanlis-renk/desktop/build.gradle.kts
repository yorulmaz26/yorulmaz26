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
    val lang = (project.findProperty("lang") as String?) ?: "tr"
    args(
        (project.findProperty("out") as String?) ?: rootProject.file("build/screenshots").absolutePath,
        "lang=$lang",
        "size=" + ((project.findProperty("size") as String?) ?: "480x1040"),
        "only=" + ((project.findProperty("only") as String?) ?: ""),
        "banner=" + ((project.findProperty("banner") as String?) ?: "110"),
    )
}
// Tanıtım videosu: xvfb-run -s "-screen 0 1100x1960x24" ./gradlew :desktop:trailer -Plang=tr -Pvariant=store -Ptrailer_out=/yol/video.mp4
tasks.register<JavaExec>("trailer") {
    group = "yanlisrenk"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.yanlisrenk.desktop.TrailerRunnerKt")
    workingDir = rootProject.file("android/assets")
    args(
        (project.findProperty("trailer_out") as String?) ?: rootProject.file("build/trailer/trailer.mp4").absolutePath,
        "lang=" + ((project.findProperty("lang") as String?) ?: "tr"),
        "variant=" + ((project.findProperty("variant") as String?) ?: "store"),
        "size=" + ((project.findProperty("size") as String?) ?: "1080x1920"),
        "fps=" + ((project.findProperty("fps") as String?) ?: "30"),
    )
}
