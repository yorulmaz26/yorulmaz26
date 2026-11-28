# RELEASE_PACKAGE — Prismway (Işık Yolu) 1.8.0

Magic Lumos Software · Google Play küresel yayın paketi · 28 Kasım 2026

**Önce oku:** `11_Final_Report/FINAL_RELEASE_REPORT.md` (ne yapıldı, ne test edildi, sende kalan işler).

| Klasör | İçerik |
|---|---|
| `01_Game_Build/APK` | Test APK'sı (2,86 MB, debug imzalı). Telefona kurulabilir. |
| `01_Game_Build/AAB` | Android App Bundle (debug imzalı). **Play'e yüklenmez.** Kendi yükleme anahtarınla `./gradlew :app:bundleRelease` (bkz. `01_Game_Build/README.md`). |
| `02_Game_Name_Research` | 31 İngilizce ad adayı, puanlar, pazar ve marka araştırması, seçilen ad: **Prismway** |
| `03_Logos` | Türkçe, İngilizce, iki dilli logo (koyu/açık/şeffaf) ve yazısız amblem |
| `04_PlayStore` | Simge 512, öne çıkan görsel TR/EN, 6 dilde 8'er telefon ekran görüntüsü, 6 dilde 7" ve 10" tablet görüntüleri |
| `05_Trailers` | 30 sn fragmanlar: dikey (Play/YouTube), sosyal medya (CTA'lı), yatay; TR/EN altyazı, kapaklar, düzenlenebilir kaynaklar |
| `06_Store_Descriptions` | 6 dilde mağaza/ASO metinleri. `<DİL>/` klasöründe Play Console'a yapıştırılacak dosyalar; `store_texts.<dil>.json` kaynak. |
| `07_Social_Media` | Türkçe ve İngilizce sosyal medya paketi (8 biçim) |
| `08_Test_Reports` | Test raporu, medya denetimi, Google Play gereksinimleri |
| `09_Privacy_And_Legal` | 6 dilde gizlilik politikası, veri güvenliği formu, lisanslar, içerik derecelendirmesi |
| `10_Release_Checklist` | Adım adım yayın kontrol listesi |
| `11_Final_Report` | Nihai rapor |
| `ZIP` | Görsel paketleri, her biri ≤ 30 MB |

İmza anahtarı, şifre ya da gizli API anahtarı bu pakette **yoktur**. Hiçbir şey bir mağazaya ya da sosyal medyaya
yüklenmedi.

Paketi baştan üretmek için: `tools/magaza/build_release_package.sh <Roboto klasörü>`.
