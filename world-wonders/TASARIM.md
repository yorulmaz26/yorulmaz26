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
| Parça sayısı | **12 parça / harika** (4×3), yani 60 bölüm/harika ve toplam 420 bölüm | Aşağıda alternatifler var |
| Özel güç isimleri | 4'lü: satır/sütun temizleyen taş (ok bantlı) · T/L: **Tunç Bomba** (3×3) · 5'li: **Güneş Diski** (aynı türün hepsi) | Arkeolojik ve tarihî temaya uygun |
| Kombinasyonlar | Çizgi+Çizgi = artı · Çizgi+Bomba = 3 sıra artı ("Kazı Haçı") · Bomba+Bomba = 5×5 · Disk+taş = o tür temizlenir · Disk+özel = o türün hepsi özele dönüşüp patlar · Disk+Disk = tüm tahta ("Güneş Tutulması") | Bilinen ve öğrenmesi kolay kalıp |
| Engel tipleri (3 genel mekanik, destinasyona göre görünüm) | **Blok** (yanında eşleşmeyle kırılır, 1–3 can) · **Katman** (üstündeki taş eşleşince temizlenir) · **Eser** (tahtanın en altına indirilince toplanır) | Az sayıda kural, çok sayıda görünüm. Kod ve öğrenme maliyeti düşük kalır |
| Destinasyon eşlemesi | Kolezyum: mermer blok, antik duvar · Petra: kaya, sert kaya, kum, Nebati vazosu · Tac Mahal: mermer engel, dekoratif taş, bahçe · Çin Seddi: ahşap bariyer, taş duvar · Machu Picchu: taş blok, sarmaşık, İnka heykelciği · Chichen Itza: taş mühür (3 can), Maya sembolü, yeşim maske · Kurtarıcı İsa: dağ kayası, granit, bulut, heykel parçası | Brief'teki öneriler |
| Yıldız | Kalan hamle oranına göre 1–3 yıldız | Basit ve anlaşılır |
| Hamle bitince | "+5 hamle" seçeneği (prototipte ücretsiz) | Canlı oyunda ödüllü reklam veya yumuşak para için doğal bir yer |

## 4. Eksik tasarım kararları (öncelik sırasıyla)

### Karar 1: Puzzle parça sayısı ve harika başına bölüm (EN YÜKSEK ÖNCELİK)
Ekonomi, içerik üretimi ve elde tutma bu sayıya bağlı.

| Seçenek | Bölüm/harika | Toplam (7) | Artı | Eksi |
|---|---|---|---|---|
| 9 parça (3×3) | 45 | 315 | Hızlı ilerleme hissi, ilk harika yaklaşık 1 haftada biter | İçerik çabuk tükenir, puzzle basit görünür |
| **12 parça (4×3)** önerilen | 60 | 420 | Dengeli. Görsel 4:3 mobil ekrana uyuyor. Lansmanda 420 bölüm sektör standardına yakın | Orta döngü uzun, ara ödül şart |
| 16 parça (4×4) | 80 | 560 | Uzun ömür | İlk harika çok uzun sürer, ilk hafta kaybı riski |
| **Kademeli** (9 → 12 → 16) | 45 / 60 / 80 | ~450 | İlk harika hızlı biter (ilk "vay" anı erken gelir), sonra derinleşir | Kural "her harika aynı" değil, ama oyuncu bunu fark etmez |

Benim tavsiyem **kademeli model**: Kolezyum 9, Petra ve Tac Mahal 12, geri kalanlar 16 parça. İlk harika tamamlama anı, oyuncunun oyunu bırakma ihtimalinin en yüksek olduğu ilk günlere düşmüş olur. Kodda bu, `PIECES_PER_WONDER` değerinin harika başına bir diziye dönüşmesi demek.

### Karar 2: Para birimi ve can (enerji) sistemi
Prototipte can yok, ücretsiz "+5 hamle" var. Belirlenmesi gerekenler:
- Can sistemi olacak mı (5 can, 30 dakikada yenilenme gibi klasik model), yoksa cansız bir model mi (reklamla devam)?
- Yumuşak para: tema önerisi **"Altın Drahmi"**. Sert para: **"Mücevher"**.
- Bölüm öncesi güçlendiriciler: **Arkeolog Fırçası** (tek hücre kırar), **Kazma** (satır kırar), **Kum Saati** (+3 hamle).

### Karar 3: Puzzle etkileşimi
- (a) Otomatik yerleşme (prototip). Sıfır sürtünme, sinematik.
- (b) Oyuncu parçayı sürükleyip yerine bırakır. Daha tatmin edici ama her ödülde 3–5 saniye ek etkileşim gerektirir.
- (c) Hibrit: parça ekranın ortasında belirir, oyuncu dokununca uçup yerine oturur.
**Öneri: (c).** Tek dokunuşla sahiplenme hissi verir, akışı bozmaz.

### Karar 4: Ara ödüller (5 bölümlük döngünün içi)
60 bölümlük bir harika döngüsünde oyuncuya sık sık küçük ödüller vermek gerekiyor. Seçenekler: her bölüm sonunda yıldız → yıldız sandığı (her 20 yıldızda bir), harika ilerlemesine bağlı "tarih kartları" (bilgi koleksiyonu), haritada her parçada anıtın etrafında küçük bir çevre detayının açılması (ağaç, kuş, ışık).
**Öneri:** Harita çevre detayları. 3D harita sistemini her parçada tekrar ziyaret ettirir ve koleksiyon hissini güçlendirir.

### Karar 5: Teknoloji ve platform
- Unity (C#): sektör standardı, 3D harita için güçlü, build boyutu büyük.
- Cocos Creator / Defold: hafif, 2D Match-3 için verimli, 3D harita sınırlı.
- Web tabanlı (bu prototip) + Capacitor ile paketleme: en hızlı yineleme, düşük maliyet, performans sınırlı.
**Öneri:** Mekanik ve ekonomi testleri bu web prototipiyle yapılsın. Üretim için Unity kullanılsın, 3D harita ayrı bir sahne olsun ve yalnızca harita ekranında yüklensin.

### Karar 6: Bölüm tasarımı yaklaşımı
Prototip bölümleri tohumlu prosedürel üretimle oluşturuyor (her oyuncuda aynı). Üretimde 420 bölümün elle tasarlanması mı, prosedürel üretim + elle ayar mı, yoksa her destinasyonun ilk 10 bölümünün elle tasarlanıp gerisinin prosedürel olması mı tercih edilecek? Zorluk eğrisi (testere dişi: her 5 bölümün 5.si "zor bölüm" olabilir ve parça ödülünü taşıyabilir) burada belirlenmeli.

### Karar 7: Destinasyona özgü "imza" mekaniği
Prototip 3 genel engel tipini farklı görünümlerle kullanıyor. Her harikaya bir tane benzersiz mekanik eklemek tazeliği artırır ama karmaşıklık da getirir. Örnek: Çin Seddi için "savunma" (her 3 hamlede bir ilerleyen ve durdurulması gereken bir engel), Chichen Itza için "gölge yılanı" (merdiven deseninde katman).
**Öneri:** Lansmanda genel 3 tip yeterli. İmza mekanikleri canlı operasyon güncellemeleri için saklanabilir.

### Karar 8: Sosyal ve canlı operasyon (lansman sonrası)
Liderlik tablosu, takımlar ve etkinlikler ("Haftanın Harikası" gibi). Bunlar şimdilik kapsam dışı ama kayıt yapısını etkiler (sunucu tarafı ilerleme).

### Karar 9: Erişilebilirlik ayrıntıları
Siluetler zaten ayrışıyor. Ek olarak renk körü modu (taş üzerinde küçük sembol), titreşim ayarları ve animasyon azaltma seçeneği. Prototip `prefers-reduced-motion` ayarını dikkate alıyor.

---

## 5. Önerilen sonraki adım

**Karar 1 (parça sayısı)** ve **Karar 2 (ekonomi ve can)** birlikte konuşulmalı, çünkü ikisi aynı denge tablosunu oluşturuyor. Bu ikisi netleştiğinde prototipte kademeli parça sayısını ve basit bir can/para döngüsünü uygulayıp oynanış süresi hesaplarını (bölüm başı ortalama 2–3 dk) somut rakamlarla çıkarabiliriz.
