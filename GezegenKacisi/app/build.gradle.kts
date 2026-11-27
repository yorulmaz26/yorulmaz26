plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// --- AdMob reklam kimlikleri ---
// Şu an Google'ın herkese açık TEST kimlikleri kullanılıyor (reklamlarda "Test Ad" yazar, gelir getirmez).
// Yayından önce AdMob hesabınızdaki kendi uygulama ve reklam birimi kimliklerinizle değiştirin.
val admobAppId = "ca-app-pub-3940256099942544~3347511713"
val adBannerId = "ca-app-pub-3940256099942544/9214589741"       // uyarlanabilir banner
val adInterstitialId = "ca-app-pub-3940256099942544/1033173712" // geçiş (resim) reklamı
val adRewardedId = "ca-app-pub-3940256099942544/5224354917"     // ödüllü video

android {
    namespace = "com.example.gezegenkacisi"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.gezegenkacisi"
        minSdk = 26
        targetSdk = 36
        versionCode = 13
        versionName = "3.0"

        manifestPlaceholders["admobAppId"] = admobAppId
        resValue("string", "ad_banner_id", adBannerId)
        resValue("string", "ad_interstitial_id", adInterstitialId)
        resValue("string", "ad_rewarded_id", adRewardedId)
    }

    // Sabit test imzası: her derlemede aynı anahtar kullanılır, böylece yeni sürüm eskisinin üstüne kurulur.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

// Oyun tamamen Android Canvas ile çiziliyor; tek dış kütüphaneler reklam (AdMob) ve reklam izni (UMP).
dependencies {
    implementation("com.google.android.gms:play-services-ads:24.9.0")
    implementation("com.google.android.ump:user-messaging-platform:3.2.0")
    testImplementation("junit:junit:4.13.2")
}

// --- Çeviri denetimi -------------------------------------------------------------------------------
// Her derlemede çalışır: values-xx/strings.xml dosyalarında varsayılan (İngilizce) dosyadaki her anahtar
// bulunmalı, fazlası olmamalı ve %1$d / %1$s yer tutucuları aynı olmalı. Sorun varsa derleme durur.
// Elle çalıştırmak için: ./gradlew :app:checkTranslations
val checkTranslations by tasks.registering {
    group = "verification"
    description = "Eksik/fazla çeviri anahtarlarını ve yer tutucu uyumsuzluklarını bulur."
    val resDir = layout.projectDirectory.dir("src/main/res").asFile
    inputs.files(fileTree(resDir) { include("values*/strings.xml") })
    doLast {
        fun parse(f: File): Map<String, List<String>> {
            val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f)
            val out = LinkedHashMap<String, List<String>>()
            val strings = doc.getElementsByTagName("string")
            for (i in 0 until strings.length) {
                val e = strings.item(i) as org.w3c.dom.Element
                if (e.getAttribute("translatable") == "false") continue
                out[e.getAttribute("name")] = listOf(e.textContent)
            }
            val plurals = doc.getElementsByTagName("plurals")
            for (i in 0 until plurals.length) {
                val e = plurals.item(i) as org.w3c.dom.Element
                val items = e.getElementsByTagName("item")
                val texts = (0 until items.length).map { (items.item(it) as org.w3c.dom.Element) }
                if (texts.none { it.getAttribute("quantity") == "other" }) {
                    throw GradleException("${f.parentFile.name}: plurals '${e.getAttribute("name")}' içinde quantity=\"other\" yok")
                }
                out["plurals:" + e.getAttribute("name")] = texts.map { it.textContent }
            }
            return out
        }
        val ph = Regex("%(\\d+\\$)?[sdf]")
        fun holders(texts: List<String>) = texts.flatMap { t -> ph.findAll(t).map { it.value } }.toSortedSet()
        val base = parse(File(resDir, "values/strings.xml"))
        val problems = ArrayList<String>()
        val langs = resDir.listFiles { d -> d.isDirectory && d.name.startsWith("values-") && File(d, "strings.xml").exists() }
            .orEmpty().sortedBy { it.name }
        for (dir in langs) {
            val tr = parse(File(dir, "strings.xml"))
            val missing = base.keys - tr.keys
            val extra = tr.keys - base.keys
            missing.forEach { problems += "${dir.name}: eksik çeviri '$it'" }
            extra.forEach { problems += "${dir.name}: varsayılan dosyada olmayan anahtar '$it'" }
            for (k in base.keys intersect tr.keys) {
                // Çoğul biçimlerde "one" sayıyı yazmayabilir; yalnızca "other" ile karşılaştır
                val a = holders(if (k.startsWith("plurals:")) base.getValue(k).takeLast(1) else base.getValue(k))
                val b = holders(if (k.startsWith("plurals:")) tr.getValue(k).takeLast(1) else tr.getValue(k))
                if (a != b) problems += "${dir.name}: '$k' yer tutucuları farklı ($a / $b)"
            }
        }
        if (problems.isNotEmpty()) throw GradleException("Çeviri denetimi başarısız:\n" + problems.joinToString("\n"))
        logger.lifecycle("Çeviri denetimi: ${langs.size} dil, ${base.size} anahtar, eksik yok.")
    }
}
tasks.named("preBuild") { dependsOn(checkTranslations) }
