# İkinci Dünya: Antik Dünyanın 7 Harikası (içerik planı)

> Durum: **plan**. Kesinleşmiş kararlar değişmez: 6 sabit taş, sade tahta, her 5 bölümde 1 parça, harika başına 12 parça, 3 can, para yok. Bu dünya, ilk dünyanın sistemlerine yalnızca yeni **veri** ekler; yeni kural sistemi gerekmez.

## Neden bu dünya
- Brief'te ikinci sezon olarak zaten öngörülmüş.
- **İki harika Türkiye'de**: Efes'teki Artemis Tapınağı (İzmir, Selçuk) ve Halikarnas Mozolesi (Muğla, Bodrum). Türkiye pazarı için güçlü bir yerel bağ ve tanıtım fırsatı.
- Harikaların altısı bugün yok ya da yıkıntı. Oyunun vaadi "**kayıp harikaları yeniden inşa et**" olabilir: puzzle tamamlandığında haritada yıkıntının yerinde harikanın kendisi yükselir. Bu, ilk dünyadan farklı bir duygu yaratır.

## Yapı
- 7 harika × 60 bölüm = **420 bölüm** (421–840).
- Harita: Akdeniz ve Orta Doğu'ya yakınlaşan ayrı bir 3D harita. İlk dünyadaki küresel harita gibi, ama daha yakından ve antik çağ kıyı çizgileriyle.
- Açılma: ilk dünyanın 7. harikası (Kurtarıcı İsa) tamamlanınca.
- Önerilen sıra coğrafi bir yolculuk izler: Mısır → Mezopotamya → Anadolu → Ege → Yunanistan → İskenderiye.

## Harikalar

| # | Harika | Konum | Engeller (blok / katman / eser) | İmza mekaniği (21. bölüm) |
|---|---|---|---|---|
| 1 | **Keops Piramidi** | Gize, Mısır | Kireçtaşı blok, granit blok (2 can) / kum / **fayans muska** | **Piramit Basamakları:** bloklar piramit dizilimiyle gelir; en üstteki kırılmadan alttakiler kırılmaz |
| 2 | **Babil'in Asma Bahçeleri** | Babil, Irak | Kerpiç duvar / **sarmaşık-bahçe** / **çivi yazılı tablet** | **Su Kanalı:** her 4 hamlede su bir sıra aşağı akar ve geçtiği hücrelerdeki bahçe katmanını sular (temizler). Olumlu mekanik |
| 3 | **Artemis Tapınağı** | Efes, Türkiye | İyon sütunu (2 can) / mermer tozu / **Artemis heykelciği** | **Sütunları Diz:** yıkık sütun parçaları (eser) yan yana getirilince sütun ayağa kalkar ve bir sırayı temizler |
| 4 | **Halikarnas Mozolesi** | Bodrum, Türkiye | Mermer kabartma (2 can), mühür taşı (3 can) / deniz tuzu / **frizdeki aslan heykeli** | **Kabartma Frizi:** bir sıradaki tüm bloklar aynı turda kırılırsa frizdeki hikâye açılır ve +3 hamle verir |
| 5 | **Rodos Heykeli** | Rodos, Yunanistan | Bronz levha / **deniz köpüğü** / **heykel parçası** | **Liman Rüzgârı:** her 3 hamlede köpük katmanı bir sütun kayar (Rüzgâr'ın deniz versiyonu) |
| 6 | **Olimpiya Zeus Heykeli** | Olympia, Yunanistan | Altın-fildişi kaplama (2 can) / zeytin yaprağı / **olimpiyat madalyası** | **Şimşek:** her 5 hamlede bir sütuna şimşek düşer; o sütundaki engellere 1 hasar verir. Olumlu mekanik |
| 7 | **İskenderiye Feneri** | İskenderiye, Mısır | Liman taşı, fener kulesi taşı (2 can) / **deniz sisi** / **papirüs** | **Fener Işığı:** her 4 hamlede dönen ışık bir sırayı aydınlatır; o sıradaki sis katmanları temizlenir. Olumlu mekanik |

Olumlu ve olumsuz mekanikler ilk dünyadaki gibi dönüşümlü dağıtıldı.

## Puzzle görselleri ve harita
- Her harikanın puzzle görseli, harikayı **kendi döneminde ve sağlam hâliyle** gösterir: Feneri gece ateşiyle, Asma Bahçeleri akan sularıyla.
- Haritada harika tamamlanana kadar **yıkıntısı** durur. Son parçayla yıkıntı yerine harika yükselir; ilk dünyadaki "yükselme" animasyonunun güçlü bir versiyonu.
- Süsler (12'şer): palmiye, dikilitaş, sfenks, çivi yazılı stel, zeytin ağacı, amfora yığını, trireme gemisi, fener ateşi, papirüs sazlığı, Efes sütunu, Bodrum kalesi silueti gibi.

## Tarih kartları (her harikada 3, yıldızla açılır)
İlk dünyadaki sistemin aynısı (30 / 90 / 150 yıldız). Her kart doğrulanmış tek bir bilgi içerir. Örnekler:
- Keops: yaklaşık 3.800 yıl boyunca dünyanın en yüksek insan yapısıydı.
- Artemis: MÖ 356'da Herostratos tarafından yakıldı; efsaneye göre o gece Büyük İskender doğdu.
- Mozole: "mozole" kelimesi, burada gömülü Kral Mausolos'un adından gelir.
- Rodos: yalnızca 54 yıl ayakta kaldı; bir depremde yıkıldı.
- Fener: "fener" anlamına gelen pek çok dildeki kelime (Fransızca *phare*, İtalyanca *faro*) yapının bulunduğu Pharos adasından gelir.

Kartlardaki bilgiler yayından önce bir tarih danışmanına kontrol ettirilmeli.

## Uygulama notu
Kodda bu dünya, `WONDERS` / `GameData.Wonders` dizisine 7 yeni kayıt, `SIGS` için 7 yeni tanım ve yeni engel/katman görselleri olarak eklenir. İmza mekaniklerinin çoğu mevcut kalıpların çeşitlemesi:
- Su Kanalı ve Fener Işığı: periyodik katman temizleme
- Şimşek: periyodik blok hasarı
- Liman Rüzgârı: mevcut Rüzgâr mekaniği

Yeni kod gerektiren iki mekanik var: **Piramit Basamakları** ve **Sütunları Diz**. Prototipte denenip bot testinden geçmeleri gerekir.
