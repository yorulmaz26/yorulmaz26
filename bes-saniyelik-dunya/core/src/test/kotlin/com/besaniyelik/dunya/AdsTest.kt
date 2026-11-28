package com.besaniyelik.dunya

import com.besaniyelik.dunya.ads.AdDirector
import com.besaniyelik.dunya.ads.AdService
import com.besaniyelik.dunya.progress.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.test.*

/** Deneme reklam hizmeti: hazır olma durumu ve ödül sonucu elle ayarlanır; gösterimler sayılır. */
class TestAds(var rewarded: Boolean = true, var interstitial: Boolean = true, var earn: Boolean = true) : AdService {
    var rewardedShown = 0; var interstitialShown = 0; var prepared = 0; var muted = false
    override fun prepareInterstitial() { prepared++ }
    override fun setAppMuted(muted: Boolean) { this.muted = muted }
    override fun rewardedReady() = rewarded
    override fun showRewarded(onResult: (Boolean) -> Unit) { rewardedShown++; onResult(earn) }
    override fun interstitialReady() = interstitial
    override fun showInterstitial(onClosed: () -> Unit) { interstitialShown++; onClosed() }
    override fun privacyOptionsRequired() = false
    override fun showPrivacyOptions() {}
}

/** Reklam kuralları: oyuncuyu yormayan sıklık sınırları ve isteğe bağlı ödüllerin doğru verilmesi. */
class AdsTest {
    private class Env(day: Long = 20000) {
        val clock = FixedClock(day)
        val profile = ProfileService(SaveManager(FileSaveStorage(Files.createTempDirectory("reklam").toFile())), clock)
        val ads = TestAds()
        var time = 1_000_000L
        val director = AdDirector(ads, profile) { time }
        fun minutes(m: Double) { time += (m * 60_000).toLong() }
        fun completeLevels(n: Int) { for (id in 1..n) profile.onLevelResult(LevelResult(testLevel(id), true, 3, 30, 30, 0, 1800, 20, 6, 0, false)) }
        /** Doğal arada reklam gösterildi mi; devam işlevi her durumda tam bir kez çalışmalı. */
        fun breakShowsAd(): Boolean {
            val before = ads.interstitialShown
            var continued = 0
            director.atBreak { continued++ }
            assertEquals(1, continued, "sonuç ekranından çıkış her durumda tam bir kez sürmeli")
            return ads.interstitialShown > before
        }
    }

    @DisplayName("Geçiş reklamı: ilk 10 bölümde, oturumun ilk 2 dakikasında ve 3 bölümden sık gösterilmez")
    @Test fun interstitialIsRare() {
        val e = Env()
        // yeni oyuncu: 9 bölüm tamamlamış, uzun süredir oynuyor, yine de reklam yok
        e.completeLevels(9); e.minutes(30.0)
        repeat(5) { e.director.onBreakEarned() }
        assertFalse(e.breakShowsAd(), "ilk 10 bölüm tamamlanmadan geçiş reklamı gösterilmemeli")
        e.completeLevels(10)
        assertTrue(e.breakShowsAd(), "koşullar sağlanınca gösterilir")
        // hemen ardından: sayaç sıfırlandı
        assertFalse(e.breakShowsAd())
        e.director.onBreakEarned(); e.director.onBreakEarned(); e.minutes(10.0)
        assertFalse(e.breakShowsAd(), "3 doğal aradan önce gösterilmez")
        e.director.onBreakEarned()
        assertTrue(e.breakShowsAd())
        // 3 ara geçti ama 3 dakika geçmedi
        repeat(3) { e.director.onBreakEarned() }; e.minutes(2.0)
        assertFalse(e.breakShowsAd(), "iki geçiş reklamı arasında en az 3 dakika olmalı")
        e.minutes(1.5)
        assertTrue(e.breakShowsAd())
        assertEquals(3, e.ads.interstitialShown)
        assertEquals(3, e.profile.data.ads.interstitialTotal)
    }

    @DisplayName("Geçiş reklamı: oturum başında ve ödüllü reklamdan hemen sonra gösterilmez; reklam hazır değilse oyun beklemez")
    @Test fun interstitialGraceAndFallback() {
        val e = Env()
        e.completeLevels(12)
        repeat(3) { e.director.onBreakEarned() }
        e.minutes(1.0)
        assertFalse(e.breakShowsAd(), "oturumun ilk 2 dakikası reklamsız")
        e.minutes(5.0)
        // oyuncu az önce isteyerek bir ödüllü reklam izledi
        var earned = 0
        e.director.showRewarded(onEarned = { earned++ })
        assertEquals(1, earned)
        assertFalse(e.breakShowsAd(), "ödüllü reklamdan hemen sonra geçiş reklamı gösterilmez")
        e.minutes(2.5)
        assertFalse(e.breakShowsAd(), "izlenen ödüllü reklam 3 dakikalık beklemeyi yeniden başlatır")
        e.minutes(1.0)
        e.ads.interstitial = false
        assertFalse(e.breakShowsAd(), "reklam yüklenmemişse gösterilmez, oyun sürer")
        e.ads.interstitial = true
        assertTrue(e.breakShowsAd())
    }

    @DisplayName("Geçiş reklamı ancak gerekeceği zaman önceden yüklenir; oyuncu sesi kapattıysa reklam sessiz başlar")
    @Test fun lazyPreloadAndMute() {
        val e = Env()
        e.completeLevels(5)
        repeat(4) { e.director.onBreakEarned() }
        assertEquals(0, e.ads.prepared, "ilk bölümlerde geçiş reklamı için istek atılmaz")
        e.completeLevels(12)
        val fresh = Env(); fresh.completeLevels(12)
        fresh.director.onBreakEarned()
        assertEquals(0, fresh.ads.prepared, "sıradaki arada gösterilemeyecekse yüklenmez")
        fresh.director.onBreakEarned()
        assertEquals(1, fresh.ads.prepared, "bir sonraki arada gösterilebilecekse önceden yüklenir")
        // ses ayarı
        fresh.director.showRewarded(onEarned = {})
        assertFalse(fresh.ads.muted)
        fresh.profile.data.settings.sound = false; fresh.profile.data.settings.music = false
        fresh.director.showRewarded(onEarned = {})
        assertTrue(fresh.ads.muted, "oyunun sesi tamamen kapalıysa reklam da sessiz başlamalı")
    }

    @DisplayName("Ödüllü reklam: tamamlanmazsa ödül verilmez; hazır değilse düğmeler kapalıdır")
    @Test fun rewardedOnlyWhenEarned() {
        val e = Env()
        var earned = 0; var skipped = 0
        e.ads.earn = false
        e.director.showRewarded(onEarned = { earned++ }, onSkipped = { skipped++ })
        assertEquals(0, earned); assertEquals(1, skipped)
        assertEquals(0, e.profile.data.ads.rewardedTotal)
        e.ads.earn = true
        e.director.showRewarded(onEarned = { earned++ }, onSkipped = { skipped++ })
        assertEquals(1, earned); assertEquals(1, e.profile.data.ads.rewardedTotal)
        e.ads.rewarded = false
        assertFalse(e.director.rewardedReady); assertFalse(e.director.gemAdAvailable()); assertFalse(e.director.loginDoubleAvailable())
        e.director.showRewarded(onEarned = { earned++ }, onSkipped = { skipped++ })
        assertEquals(1, earned); assertEquals(2, skipped); assertEquals(2, e.ads.rewardedShown, "hazır olmayan reklam gösterilmeye çalışılmaz")
    }

    @DisplayName("Mağaza: reklamla elmas günde 5 kez, her seferinde 3 elmas; ertesi gün sıfırlanır")
    @Test fun gemAdsDailyCap() {
        val e = Env()
        val start = e.profile.data.gems
        assertEquals(AdDirector.GEM_ADS_PER_DAY, e.director.gemAdsLeft())
        repeat(AdDirector.GEM_ADS_PER_DAY) { assertEquals(AdDirector.GEMS_PER_AD, e.director.grantGemAd()) }
        assertEquals(start + AdDirector.GEM_ADS_PER_DAY * AdDirector.GEMS_PER_AD, e.profile.data.gems)
        assertEquals(0, e.director.gemAdsLeft()); assertFalse(e.director.gemAdAvailable())
        assertEquals(0, e.director.grantGemAd(), "sınır dolunca ödül verilmez")
        e.clock.day++
        assertEquals(AdDirector.GEM_ADS_PER_DAY, e.director.gemAdsLeft()); assertTrue(e.director.gemAdAvailable())
    }

    @DisplayName("Günlük ödül: alındığı gün reklamla bir kez ikiye katlanır")
    @Test fun loginDouble() {
        val e = Env()
        assertFalse(e.director.loginDoubleAvailable(), "ödül alınmadan katlanamaz")
        val first = e.profile.claimLogin()!!
        val coins = e.profile.data.coins; val gems = e.profile.data.gems
        assertTrue(e.director.loginDoubleAvailable())
        val again = e.director.grantLoginDouble()!!
        assertEquals(first.describe(), again.describe(), "aynı günün ödülü bir kez daha verilir")
        assertEquals(coins + first.coins, e.profile.data.coins); assertEquals(gems + first.gems, e.profile.data.gems)
        assertFalse(e.director.loginDoubleAvailable()); assertNull(e.director.grantLoginDouble(), "günde bir kez")
        e.clock.day++
        assertFalse(e.director.loginDoubleAvailable(), "yeni günün ödülü alınmadan katlanamaz")
    }

    @DisplayName("Reklamsız platformda (masaüstü, çevrimdışı) hiçbir reklam düğmesi çıkmaz ve akış kesilmez")
    @Test fun noAdsPlatform() {
        val clock = FixedClock(20000)
        val profile = ProfileService(SaveManager(FileSaveStorage(Files.createTempDirectory("reklamsiz").toFile())), clock)
        val d = AdDirector(AdService.NONE, profile)
        assertFalse(d.rewardedReady); assertFalse(d.gemAdAvailable()); assertFalse(d.loginDoubleAvailable()); assertFalse(d.privacyOptionsRequired)
        repeat(10) { d.onBreakEarned() }
        var continued = 0; d.atBreak { continued++ }; assertEquals(1, continued)
        var skipped = 0; d.showRewarded(onEarned = { fail("reklam yokken ödül verilemez") }, onSkipped = { skipped++ }); assertEquals(1, skipped)
    }

    @DisplayName("Cihaz tarihi geri alınınca günlük sınır sıfırlanmaz; uzun süre arka planda kalınca yeni oturum başlar")
    @Test fun clockAndSession() {
        val e = Env()
        repeat(AdDirector.GEM_ADS_PER_DAY) { e.director.grantGemAd() }
        e.clock.day--                                   // tarih bir gün geri alındı
        assertEquals(0, e.director.gemAdsLeft(), "tarihi geri almak hak kazandırmaz")
        e.clock.day++                                   // yeniden bugüne
        assertEquals(0, e.director.gemAdsLeft(), "aynı güne dönmek de sıfırlamaz")
        e.clock.day++                                   // gerçekten yeni gün
        assertEquals(AdDirector.GEM_ADS_PER_DAY, e.director.gemAdsLeft())
        // oturum: 3 bölüm tamamlanmış, uygulama uzun süre arka planda kalmış
        e.completeLevels(12); e.minutes(10.0)
        repeat(3) { e.director.onBreakEarned() }
        e.director.onAppPaused(); e.minutes(30.0); e.director.onAppResumed()
        assertFalse(e.breakShowsAd(), "uzun aradan dönünce ilk 2 dakika geçiş reklamı gösterilmez")
        e.minutes(2.5)
        assertTrue(e.breakShowsAd())
        // kısa süreli arka plan (ör. bildirime bakmak) oturumu yenilemez
        repeat(3) { e.director.onBreakEarned() }; e.minutes(4.0)
        e.director.onAppPaused(); e.minutes(0.2); e.director.onAppResumed()
        assertTrue(e.breakShowsAd())
    }

    @DisplayName("Bölüm sonu ek altını ve mağaza kartı: reklamsız platformda kart yok, reklam yüklenirken kart durur")
    @Test fun bonusCoinsAndGemCard() {
        val e = Env()
        val coins = e.profile.data.coins
        e.director.grantBonusCoins(31); e.director.grantBonusCoins(0); e.director.grantBonusCoins(-5)
        assertEquals(coins + 31, e.profile.data.coins)
        assertTrue(e.director.rewardedReady)            // bu oturumda bir reklam yüklendi
        e.ads.rewarded = false                          // izlendi; yenisi yükleniyor
        assertTrue(e.director.gemCardVisible(), "reklam yüklenirken kart görünür kalır (düğme kapalı)")
        assertFalse(e.director.gemAdAvailable())
        // hiç reklam yüklenemeyen oturum (ör. çevrimdışı): kart hiç gösterilmez
        val offline = AdDirector(TestAds(rewarded = false), e.profile)
        assertFalse(offline.gemCardVisible(), "reklam hiç yüklenmediyse boş vaat gösterilmez")
        val none = AdDirector(AdService.NONE, e.profile)
        assertFalse(none.supported); assertFalse(none.gemCardVisible())
        assertEquals(0, none.bannerHeightPx, "reklamsız platformda afiş yer kaplamaz")
    }

    @DisplayName("Sonsuz Dünya: işlenmeden kalan koşu kayıtta bekler, sonraki açılışta işlenir; devam edilirse bırakılır")
    @Test fun pendingEndlessRun() {
        val dir = Files.createTempDirectory("bekleyen").toFile()
        val clock = FixedClock(20000)
        val p1 = ProfileService(SaveManager(FileSaveStorage(dir)), clock)
        val run = EndlessResult(600, 60f, 300f, 40, 2, 55, 9, 0)
        p1.holdEndless(run); p1.save()                  // uygulama arka plana alındı, sonra süreç öldü
        assertEquals(0, p1.data.stats.endlessRuns)
        val p2 = ProfileService(SaveManager(FileSaveStorage(dir)), clock)
        assertEquals(1, p2.data.stats.endlessRuns, "yarım kalan koşu açılışta işlenmeli")
        assertEquals(600, p2.data.stats.endlessBestScore); assertNull(p2.data.pendingEndless)
        val p3 = ProfileService(SaveManager(FileSaveStorage(dir)), clock)
        assertEquals(1, p3.data.stats.endlessRuns, "ikinci kez işlenmemeli")
        // reklamla devam edilirse bekleyen sonuç bırakılır
        p3.holdEndless(run); p3.releaseEndless(); p3.save()
        assertEquals(1, ProfileService(SaveManager(FileSaveStorage(dir)), clock).data.stats.endlessRuns)
        // normal bitiş: sonuç işlenince bekleyen kayıt temizlenir
        p3.holdEndless(run); p3.onEndlessResult(run)
        assertNull(p3.data.pendingEndless); assertEquals(2, p3.data.stats.endlessRuns)
    }

    @DisplayName("Reklam alanları olmayan eski kayıt dosyası sorunsuz açılır")
    @Test fun oldSaveWithoutAdFields() {
        val dir = Files.createTempDirectory("eskikayit").toFile()
        val clock = FixedClock(20000)
        val p1 = ProfileService(SaveManager(FileSaveStorage(dir)), clock)
        p1.data.coins = 777; p1.save()
        // kayıt dosyasından reklamla ilgili alanları çıkar (eski sürümün yazdığı dosya gibi) ve sağlama toplamını yenile
        val file = java.io.File(dir, SaveManager.MAIN)
        val env = kotlinx.serialization.json.Json.parseToJsonElement(file.readText()) as kotlinx.serialization.json.JsonObject
        val body = kotlinx.serialization.json.Json.parseToJsonElement((env["data"] as kotlinx.serialization.json.JsonPrimitive).content) as kotlinx.serialization.json.JsonObject
        assertTrue("ads" in body && "pendingEndless" in body)
        val old = kotlinx.serialization.json.JsonObject(body.filterKeys { it != "ads" && it != "pendingEndless" }).toString()
        val rebuilt = kotlinx.serialization.json.JsonObject(mapOf(
            "v" to kotlinx.serialization.json.JsonPrimitive(1), "crc" to kotlinx.serialization.json.JsonPrimitive(SaveManager.checksum(old)), "data" to kotlinx.serialization.json.JsonPrimitive(old)))
        file.writeText(rebuilt.toString()); java.io.File(dir, SaveManager.BACKUP).delete()
        val p2 = ProfileService(SaveManager(FileSaveStorage(dir)), clock)
        assertEquals(777, p2.data.coins, "eski kayıt yüklenmeli (yedeğe ya da sıfır kayda düşmemeli)")
        assertEquals(-1, p2.data.ads.day); assertEquals(0, p2.data.ads.gemAdsToday); assertNull(p2.data.pendingEndless)
    }

    @DisplayName("Kayıt: reklam sayaçları kalıcıdır; reklamsız sürümün kaydı sorunsuz açılır")
    @Test fun adStatePersists() {
        val dir = Files.createTempDirectory("reklamkayit").toFile()
        val clock = FixedClock(20000)
        val p1 = ProfileService(SaveManager(FileSaveStorage(dir)), clock)
        val d1 = AdDirector(TestAds(), p1)
        d1.grantGemAd(); d1.grantGemAd()
        val p2 = ProfileService(SaveManager(FileSaveStorage(dir)), clock)
        assertEquals(2, p2.data.ads.gemAdsToday); assertEquals(20000, p2.data.ads.day)
        assertEquals(AdDirector.GEM_ADS_PER_DAY - 2, AdDirector(TestAds(), p2).gemAdsLeft())
        // eski (reklam alanı olmayan) kayıt: varsayılan değerlerle yüklenir
        assertEquals(0, SaveData().ads.gemAdsToday); assertEquals(-1, SaveData().ads.day)
    }
}
