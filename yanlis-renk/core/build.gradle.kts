plugins { kotlin("jvm") }
kotlin { jvmToolchain(21) }
val gdxVersion: String by project
dependencies {
    api(project(":logic"))
    api("com.badlogicgames.gdx:gdx:$gdxVersion")
    api("com.badlogicgames.gdx:gdx-freetype:$gdxVersion")
}
