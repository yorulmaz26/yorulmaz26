pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ZamaninEfendisi"

// logic  : Saf Kotlin oyun mantığı (nedensellik motoru, bölümler, kayıt, can sistemi). Grafikten bağımsızdır, JVM testleri burada.
// core   : libGDX ekranları, izometrik çizim, arayüz, ses. logic modülünü kullanır.
// android: Android başlatıcısı (AndroidApplication) ve yerel kütüphaneler.
// desktop: Geliştirme amaçlı masaüstü başlatıcısı (LWJGL3).
include(":logic", ":core", ":android", ":desktop")
