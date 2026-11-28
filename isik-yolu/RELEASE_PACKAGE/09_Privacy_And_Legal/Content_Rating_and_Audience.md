# İçerik derecelendirmesi, hedef kitle ve beyanlar

Bu yanıtlar Play Console'da **senin hesabınla** doldurulmalıdır. Aşağıdakiler oyunun gerçek içeriğine göre
önerilen yanıtlardır.

## IARC içerik derecelendirmesi anketi

- **Kategori:** Oyun.
- **İçerikte bulunmayanlar:**
  - şiddet, kan, korku,
  - cinsel içerik, çıplaklık,
  - küfür ya da kaba dil,
  - uyuşturucu, alkol, tütün,
  - kumar ya da benzetimi, gerçek parayla şans oyunu.
- **Kullanıcı etkileşimi:** Kullanıcılar arası iletişim yok, kullanıcı içeriği paylaşımı yok, konum paylaşımı yok.
- **Dijital satın alma:** Yok. Oyun içi kristaller yalnızca oynayarak kazanılır.
- **Reklam:** Var. Reklam içeriği uygulamada "PG" ile sınırlı.
- **Beklenen sonuç:** Tüm bölgelerde en düşük yaş derecesi (ör. PEGI 3 / ESRB Everyone / USK 0). Kesin sonucu
  IARC verir.

## Hedef kitle ve içerik (Uygulama içeriği › Hedef kitle)

- **Önerilen hedef yaş grupları:** 13–15, 16–17, 18+.
  - Oyun her yaşa uygun bir bulmaca olsa da reklam SDK'sı kişiselleştirilmiş reklam ve reklam kimliği kullanıyor.
  - Bu yüzden "Aileler" programı (Designed for Families) ve 13 yaş altı hedef kitle **seçilmemelidir**.
  - 13 yaş altını hedeflemek, AdMob'un çocuklara yönelik ayarlarını (`tagForChildDirectedTreatment`), Families
    politikalarına uygun reklam sertifikasını ve ek beyanları gerektirir.
- **"Uygulama çocukların ilgisini çekebilir mi?"** Görseller sade ve renkli. Play bu soruyu sorarsa dürüstçe yanıtla.
  Mağaza görsellerinde ve metinlerinde çocuklara yönelik dil kullanılmadı.

## Diğer beyanlar

| Beyan | Önerilen yanıt |
|---|---|
| Reklam içerir | Evet |
| Reklam kimliği | Evet (reklam, analiz, dolandırıcılığı önleme) |
| Uygulama içi satın alma | Hayır |
| Kamu kurumu / haber / sağlık / finans uygulaması | Hayır |
| Ön plan hizmeti (FGS) beyanı | Gerekmiyor görünüyor. Birleşik manifestte yalnızca WorkManager'ın türü belirtilmemiş `SystemForegroundService` hizmeti ve `FOREGROUND_SERVICE` izni var (AdMob → WorkManager). Oyun ön plan hizmeti başlatmaz, tür izni (ör. `FOREGROUND_SERVICE_DATA_SYNC`) yok. Play Console yine de sorarsa "kullanılmıyor" de. **Manuel kontrol gerekli.** |
| Veri güvenliği | `Data_Safety_Form.md` |
| Gizlilik politikası adresi | `privacy_policy_<dil>.md` dosyalarını herkese açık bir adreste yayımla. Adresi Play Console'a ve derlemede `privacyPolicyUrl=` özelliğine yaz. **Erişim gerekli:** yayın adresi henüz yok. |
