pluginManagement {
    repositories {
        google()
        // Maven Central'ın Google aynası (oran sınırına takılmamak için önce denenir)
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        // Maven Central'ın Google aynası (oran sınırına takılmamak için önce denenir)
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
    }
}

rootProject.name = "YuruyenSehir"
include(":logic", ":core", ":android", ":desktop")
