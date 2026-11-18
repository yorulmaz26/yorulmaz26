plugins { kotlin("jvm"); kotlin("plugin.serialization"); application }
kotlin { jvmToolchain(21) }
dependencies {
    implementation(project(":logic"))
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
}
application { mainClass.set("com.yanlisrenk.server.TimeServerKt") }
tasks.test { useJUnit() }
