# World Wonders: Aşama 1 Tasarım Analizi

Bu doküman iki şeyi içeriyor: mevcut konseptin analizi ve henüz verilmemiş tasarım kararlarının öncelik sırası. Oynanabilir prototip (`index.html`) kesinleşmiş kararların hepsini uyguluyor. Henüz kesinleşmemiş konularda bir **öneri** kullanıyor ve bu öneriler kodda değiştirilebilir sabitler olarak duruyor.

---

## 1. Kesinleşmiş kararlar (değiştirilmedi)

| Karar | Prototipte |
|---|---|
| 6 sabit taş: Altın sikke, Parşömen, Amfora, Skarabe, Safir pusula, Yakut taç | Her destinasyonda aynı. Her birinin farklı bir silueti var: daire, geniş rulo, uzun vazo, oval böcek, eşkenar dörtgen, taç |
| Sade, nötr Match-3 tahtası | Açık taş rengi dama zemin. Tahtanın arkasında yapı ya da manzara yok |
| Bölge atmosferi tahtanın dışında | Bölüm girişi illüstrasyonu, harita, engeller ve görev objeleri |
| Her 5 bölümde 1 puzzle parçası | `LEVELS_PER_PIECE = 5` |
| **Harika başına 12 parça** (aşama 2'de kararlaştırıldı) | `PIECES_PER_WONDER = 12`: 4×3 puzzle, 60 bölüm/harika, toplam 420 bölüm |
| **3 can, oyun içi para yok** (aşama 2'de kararlaştırıldı) | `MAX_LIVES = 3`. Kaybedilen bölüm 1 can götürür; para, mağaza ve satın alınan güçlendirici yok |
| Parça sadece mevcut destinasyona gider | İlerleme, destinasyon bloklarına bölünerek hesaplanıyor |
| Puzzle tamamlanınca harika 3D haritaya eklenir | Tamamlama animasyonu, ardından haritada "yükselme" ve sıradaki durağa kamera geçişi |
| Yeni Dünyanın 7 Harikası, belirtilen sırayla | Kolezyum → Petra → Tac Mahal → Çin Seddi → Machu Picchu → Chichen Itza → Kurtarıcı İsa |
| Özel güçler: 4'lü, T/L, 5'li ve kombinasyonlar | Tamamı uygulandı (bkz. §3) |

## 2. Konsept analizi

**Güçlü yanlar**
- **İki katmanlı motivasyon.** Kısa döngü bölüm kazanmak, orta döngü puzzle parçası (5 bölüm), uzun döngü de harika tamamlamak. Oyuncunun her an "bir sonraki ödüle ne kadar kaldı" sorusunun cevabını görebilmesi elde tutmanın temelidir. Prototip bunu oyun ekranında 5'li bir parça göstergesiyle sürekli gösteriyor.
- **Sabit taşlar.** Öğrenme yükünü sıfırlıyor ve sanat bütçesini taşlarda değil engellerde ve haritada harcamaya izin veriyor. Ticari açıdan da doğru bir tercih.
- **Evrensel tema.** Dünya harikaları dil ve kültür engeli olmadan tanınıyor. Yerelleştirmede metin yükü az.
- **Genişletilebilir içerik.** Antik Dünyanın 7 Harikası gibi her yeni "dünya" aynı sistemin yeni bir veri seti olarak eklenebilir.

**Riskler**
1. **Orta döngü çok uzun kalabilir.** 12 parçalık bir puzzle 60 bölüm demek. Bu sürede oyuncu tek bir harikada kalıyor ve haritada yeni bir şey görmüyor. Ara ödüllerle desteklenmesi gerekiyor (bkz. Karar 1 ve 4).
2. **Puzzle'ın kendisi pasif.** Parçalar otomatik yerine oturuyor. Oyuncunun parçayı kendisinin sürükleyip yerleştirmesi tatmini artırabilir ama mobilde sürtünme yaratır (bkz. Karar 3).
3. **Engel çeşitliliği taş sabitliğini telafi etmeli.** Taşlar hiç değişmediği için tazelik hissi engeller ve görevlerden gelmek zorunda.
4. **3D harita maliyeti.** Gerçek 3D (Unity/Unreal) harita, özellikle düşük seviye Android cihazlarda bellek ve ısınma sorunu çıkarabilir. Prototip bunun yerine CSS 3D eğimli bir düzlem ve "ayakta duran" 2.5D anıt ikonları kullanıyor. Bu yaklaşım hafif ve üretimde de geçerli bir seçenek olabilir.

## 3. Prototipte kullanılan öneriler (henüz kesin değil)

| Konu | Öneri | Gerekçe |
|---|---|---|
| Can yenilenme | **20 dakikada 1 can** (3 can 1 saatte dolar) | Bkz. Karar 2b |
| Bölümden çıkış | Hamle yapıldıysa 1 can gider | Kaybetmemek için çıkma açığını kapatır |
| Özel güç isimleri | 4'lü: satır/sütun temizleyen taş (ok bantlı) · T/L: **Tunç Bomba** (3×3) · 5'li: **Güneş Diski** (aynı türün hepsi) | Arkeolojik ve tarihî temaya uygun |
| Kombinasyonlar | Çizgi+Çizgi = artı · Çizgi+Bomba = 3 sıra artı ("Kazı Haçı") · Bomba+Bomba = 5×5 · Disk+taş = o tür temizlenir · Disk+özel = o türün hepsi özele dönüşüp patlar · Disk+Disk = tüm tahta ("Güneş Tutulması") | Bilinen ve öğrenmesi kolay kalıp |
| Engel tipleri (3 genel mekanik, destinasyona göre görünüm) | **Blok** (yanında eşleşmeyle kırılır, 1–3 can) · **Katman** (üstündeki taş eşleşince temizlenir) · **Eser** (tahtanın en altına indirilince toplanır) | Az sayıda kural, çok sayıda görünüm. Kod ve öğrenme maliyeti düşük kalır |
| Destinasyon eşlemesi | Kolezyum: mermer blok, antik duvar · Petra: kaya, sert kaya, kum, Nebati vazosu · Tac Mahal: mermer engel, dekoratif taş, bahçe · Çin Seddi: ahşap bariyer, taş duvar · Machu Picchu: taş blok, sarmaşık, İnka heykelciği · Chichen Itza: taş mühür (3 can), Maya sembolü, yeşim maske · Kurtarıcı İsa: dağ kayası, granit, bulut, heykel parçası | Brief'teki öneriler |
| Yıldız | Kalan hamle oranına göre 1–3 yıldız | Basit ve anlaşılır |
| Hamle bitince | Ek hamle yok, 1 can gider; can varsa "Tekrar Dene" | Para olmadığı için ek hamle de kaldırıldı |

## 4. Eksik tasarım kararları (öncelik sırasıyla)

### ✅ Karar 1: Puzzle parça sayısı (KARARLAŞTIRILDI)
Her harika **12 parça** (4×3). 60 bölüm/harika, toplam 420 bölüm. Kademeli model uygulanmayacak.
Not: "60 bölüm boyunca aynı harika" riski hâlâ geçerli. Karar 4'teki ara ödüller bu yüzden önemini koruyor.

### ✅ Karar 2: Can ve para (KARARLAŞTIRILDI)
**3 can** olacak, **oyun içi para olmayacak**. Prototipte uygulandı: kaybedilen bölüm 1 can götürüyor, can bitince bölüme girilemiyor ve geri sayım gösteriliyor. Ek hamle ve mağaza kaldırıldı.

#### ✅ Karar 2b: Can yenilenmesi (KARARLAŞTIRILDI)
- **20 dakikada 1 can.** 3 can 1 saatte dolar.
- **Puzzle parçası kazanınca canlar tamamen dolar.** Para gerektirmiyor, puzzle ödülünü daha değerli kılıyor ve her 5 bölümde bir doğal "nefes alma" anı yaratıyor. Zorlanan oyuncuyu tam parçaya yaklaştığı anda kaybetmeyi de engelliyor.

#### ✅ Karar 2c: Güçlendiriciler (KARARLAŞTIRILDI)
**Bölüm öncesi güçlendirici olmayacak.** Para olmadığı için satın alınamıyorlar; ödül olarak verilmeleri de yeni bir ekonomi katmanı (stok, denge, arayüz) açar. Oyunun derinliği tahtada oluşan özel güçlerden (çizgi, Tunç Bomba, Güneş Diski ve kombinasyonları) geliyor. Gerekirse ileride ödül olarak eklenebilir.

#### ✅ Gelir modeli (KARARLAŞTIRILDI: şimdilik yok)
Bu aşamada gelir modeli tasarlanmayacak. Can sistemi ileride ödüllü reklam veya tek seferlik satın alma eklenecek olursa onları taşıyabilecek şekilde bağımsız tutuldu.

### ✅ Karar 3: Puzzle etkileşimi (KARARLAŞTIRILDI: hibrit)
Yeni parça ekranın ortasında büyük ve süzülerek belirir ve "Parçaya dokun, yerine oturt" yazısı çıkar. Oyuncu dokununca parça yay çizerek uçar ve yerine oturur; ardından ses, konfeti ve puzzle tablasının hafif zıplaması gelir. Tek dokunuşla sahiplenme hissi veriyor, sürükle-bırak kadar sürtünme yaratmıyor ve 1–2 saniyede bitiyor.

### ✅ Karar 4: Ara ödüller (KARARLAŞTIRILDI: harita süsleri)
- Her puzzle parçası, haritada o harikanın çevresinde **bir süs** açar. 12 parça = 12 süs.
- Süsler harikanın kültürüne göre seçildi: Kolezyum'da selvi ve antik sütun, Petra'da deve ve Bedevi çadırı, Çin Seddi'nde fener, Machu Picchu'da lama, Chichen Itza'da kaktüs ve meşale, Rio'da kayık ve palmiye gibi. Toplam 15 süs tipi var.
- Mevcut harikanın açılmamış süs yerleri haritada **kesik çizgili halkalarla** görünür, böylece kalan ilerleme göz önünde olur.
- Haritaya dönüldüğünde yeni süs zıplayarak belirir, konfeti çıkar ve "Yeni süs: Fener · Kolezyum" bildirimi görünür.
- Puzzle parçası kazanınca canlar da dolduğu için 5 bölümde bir üç ödül birlikte gelir: **parça + süs + dolu can.**

### ✅ Karar 5: Teknoloji ve platform (KARARLAŞTIRILDI: Unity 6 LTS)
**Neden Unity:**
- 3D harita ile 2D Match-3'ü aynı motorda en rahat taşıyan seçenek.
- iOS ve Android'e tek koddan çıkıyor.
- Addressables ile her harika ayrı bir içerik paketi olarak indirilebiliyor; Antik Dünya gibi genişlemeler için önemli.
- Casual oyun pazarında en çok deneyimli geliştirici bu motorda.

**Elenenler:**
- Cocos: 3D harita zayıf.
- Unreal: mobil casual için ağır.
- Web + paketleme: 3D harita ve düşük seviye Android performansı riskli.

**Yapılan iş:** `unity/` klasöründe oyunun kurallarını içeren saf C# çekirdek hazır: bölüm üretimi ve tahta motoru, imza mekanikleri dahil.
- Çekirdek, web prototipiyle **420/420 bölümde** ve **2.516 hamle adımında birebir aynı** sonucu veriyor; bu, otomatik testle kanıtlanıyor.
- Prototip, kuralların "doğru kaynağı" olarak kalıyor. Unity tarafı aynı kuralları kullanan bir sunum katmanı olacak.
- Ayrıntılar `unity/README.md` dosyasında.

### ✅ Karar 6: Bölüm tasarımı ve zorluk eğrisi (KARARLAŞTIRILDI)
**Yaklaşım:** Bölümler tohumlu prosedürel üretimle oluşuyor (her oyuncuda aynı bölüm). Üretim aşamasında her destinasyonun ilk birkaç bölümü elle cilalanabilir.

**Testere dişi eğri:** Her 5 bölümlük parça döngüsünde:
| Sıra | Rol | Ayar |
|---|---|---|
| 1. | Nefes bölümü | +3 hamle |
| 2.–4. | Normal | Destinasyon içinde zorluk yavaşça artar |
| 5. | **Zor bölüm** (parçayı verir) | −2 hamle, toplama hedefi %15 fazla; girişte ve oyun ekranında "ZOR · PARÇA" etiketi |

Ek kurallar: ilk 10 bölüm öğrenme dönemi (cömert hamle), her destinasyonun ilk 3 bölümü yumuşak giriş (+2 hamle), yeni engeller her destinasyonun başında tek tek tanıtılır.

**Bot ile doğrulama.** Açgözlü bir bot (her hamlede en iyi görünen eşleşmeyi seçer, ileriyi düşünmez) her bölümü 3 kez oynadı (ilk harikada her bölüm, diğerlerinde her 3. bölüm; toplam 180 bölüm, 540 oyun). Kazanma oranları:

| Ölçüm | Bot kazanma oranı |
|---|---|
| İlk 10 bölüm | %100 |
| Harika 1 → 7 | %89 · %80 · %78 · %73 · %68 · %72 · %67 |
| Döngü 1. (nefes) | %92 |
| Döngü 2.–4. | %73–81 |
| Döngü 5. (zor) | %66 |
| Hedef tipi | Taş %86 · Blok %81 · Katman %68 · Eser %58 (ayardan sonra, 5'er oyunla) |

Gerçek oyuncu, ileriyi düşündüğü ve eserleri bilerek aşağı indirdiği için bottan biraz daha başarılı olur. Bu oranlar, casual Match-3 için hedeflenen "ilk denemede çoğunlukla kazanılır, parça bölümleri biraz terletir" hissine uygun. Sayılar ilk gerçek oyuncu testinde yeniden ayarlanmalı.

### ✅ Karar 7: Destinasyona özgü imza mekanikleri (KARARLAŞTIRILDI ve UYGULANDI)
Her harika, **21. bölümünde** kendine özgü bir kural tanıtır. Bu kural sonraki bölümlerin yaklaşık yarısında tekrar çıkar. Hepsi mevcut sistemlerin (blok, katman, özel güç) üzerine kurulu; yeni bir kavram yükü getirmiyor.

| Harika | Mekanik | Kural | Bot kazanma oranı |
|---|---|---|---|
| Kolezyum | Gladyatör Kalkanı | 3'lü eşleşmeyle kırılmaz; yanında 4'lü veya daha büyük eşleşme ya da özel güç gerekir. Büyük eşleşme yapmayı öğretir | %65 |
| Petra | Kum Fırtınası | Her 4 hamlede temizlenen kum, bir komşu hücreye geri savrulur | %54 (mekaniksiz %67) |
| Tac Mahal | Lotus Havuzu | Lotus üzerindeki taşı eşleştirmek +1 hamle verir (olumlu mekanik) | %89 |
| Çin Seddi | Akıncı Saldırısı | Her 4 hamlede üst sıralara yeni ahşap bariyer kurulur (en fazla 4). Brief'teki "savunma" fikri | %79 |
| Machu Picchu | Sarmaşık Kilidi | Kilitli taş kaydırılamaz ve düşmez; eşleştirilerek kurtarılır | %53 |
| Chichen Itza | Kukulkan Işığı | Her 5 hamlede güneş bir taşı çizgi gücüne çevirir (olumlu mekanik) | %79 |
| Kurtarıcı İsa | Rüzgâr | Her 3 hamlede bulutlar bir sütun sağa kayar | %70 |

Periyodik mekaniklerde oyun ekranında geri sayım görünür ("Kum Fırtınası: 2 hamle"). İlk karşılaşmada bölüm girişinde "YENİ MEKANİK" kartı çıkar. Olumsuz ve olumlu mekanikler bilinçli olarak dönüşümlü sıralandı.

### Karar 8: Sosyal ve canlı operasyon (lansman sonrası)
Liderlik tablosu, takımlar ve etkinlikler ("Haftanın Harikası" gibi). Bunlar şimdilik kapsam dışı ama kayıt yapısını etkiler (sunucu tarafı ilerleme).

### ✅ Karar 9: Erişilebilirlik (KARARLAŞTIRILDI ve UYGULANDI)
- **Renk körü modu** (Ayarlar): her taşın köşesine ayrı bir sembol eklenir: ● sikke, ■ parşömen, ▲ amfora, ✚ skarabe, ◆ pusula, ★ taç. Taşların siluetleri zaten farklı; sembol ikinci bir güvence. Hedef simgelerine de yansır.
- **Titreşim** (Ayarlar, varsayılan açık): özel güç, patlama ve puzzle parçasında kısa titreşim (destekleyen cihazlarda).
- **Azaltılmış hareket:** cihazın "hareketi azalt" ayarı dikkate alınır.

---

## 5. Oyuncu testi (HAZIR)
Oyunun içinde bir test altyapısı var. Her deneme anonim olarak kaydediliyor; 10. bölümden sonra kısa bir anket çıkıyor. Testçilere yazma izni verilirse sonuçlar otomatik olarak toplanıyor ve oyunun sahibi "Tüm Testçiler" ekranında toplu sonuçları görüyor. Plan: `TEST_PLANI.md`.

## 6. Sonraki adım
Bütün tasarım kararları verildi. Bundan sonrası:
1. **Oyuncu testini yapmak** (5–10 kişi, 2 gün) ve sonuçlara göre zorluğu ayarlamak.
2. **Unity'de görsel üretim:** taş, engel ve harita modelleri; görünüm katmanının `Board.Events` ile canlandırılması.
3. **Antik Dünyanın 7 Harikası** (ikinci dünya) için içerik planı.
