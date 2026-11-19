# World Wonders: Unity üretim iskeleti

Karar 5 gereği oyunun üretim sürümü **Unity 6 LTS** ile yapılacak. Bu klasör, oyunun çekirdek mantığını Unity'ye hazır C# olarak içeriyor.

## Yapı
```
Assets/Scripts/Core/   Saf C# çekirdek (UnityEngine bağımlılığı yok, noEngineReferences)
  Rng.cs               Mulberry32: prototiple bit düzeyinde aynı rastgele sayı
  GameData.cs          Kesin sabitler (12 parça, 5 bölüm, 3 can, 20 dk) ve 7 harika verisi
  LevelGenerator.cs    420 bölümün tohumlu üretimi
  Board.cs             Match-3 motoru: eşleşme, özel güçler, kombinasyonlar, düşme,
                       eserler, karıştırma, imza mekanikleri ve görünüm için olay akışı
Assets/Scripts/View/
  BoardView.cs         En sade Unity görünümü (sprite + sürükleme). Unity içinde derlenir.
Tests/                 Unity olmadan çalışan doğrulama (.NET 8)
  export-fixtures.js   Web prototipinden karşılaştırma verisi üretir
  Fixtures/            420 bölüm + 105 bölümde 2.516 tahta durumu
  Program.cs           Testler
```

## Doğrulama
```bash
dotnet run --project Tests
```
Son çalıştırmanın sonucu:
- **420/420 bölüm** prototiple birebir aynı (hamle, hedef, engel, katman, eser, kilit, lotus)
- **105 bölümde 2.516 tahta durumu** hamle hamle birebir aynı (puan, hedef, düşme, özel güçler, kombinasyonlar, imza mekanikleri)
- 420 bölümün hepsinde başlangıç tahtası kuralları: hazır eşleşme yok, geçerli hamle var, boş hücre yok

Testin hassasiyeti, koda kasıtlı hatalar eklenerek kontrol edildi: bir puan sabitini değiştirmek 73 hata, engel canını değiştirmek 39 hata üretti.

Prototipte bir kural değişirse: `node Tests/export-fixtures.js` (Playwright gerekir) ile veriyi yeniden üretip C# tarafını testler yeşile dönene kadar güncelleyin.

## Unity'de açma
1. Unity Hub → Unity 6 LTS ile boş bir **2D (URP)** projesi oluşturun.
2. Bu klasördeki `Assets/Scripts` klasörünü projenin `Assets/` klasörüne kopyalayın.
3. Sahneye boş bir GameObject ekleyip `BoardView` bileşenini takın. Bir SpriteRenderer prefab'ı, 6 taş sprite'ı, disk, eser ve blok sprite'larını atayın.
4. Player Settings → Active Input Handling: **Both** (örnek betik eski `Input` sınıfını kullanıyor).

## Üretim mimarisi (öneri)
| Katman | İçerik |
|---|---|
| Core (bu klasör) | Kurallar, bölüm üretimi, kayıt formatı. Testlerle prototibe bağlı |
| View | Taş prefab'ları, parçacık efektleri, `Board.Events` ile animasyon sırası |
| Meta | Harita sahnesi (3D, URP Lit), puzzle ekranı, koleksiyon, can zamanlayıcısı |
| İçerik | Harika başına Addressables paketi: illüstrasyon, harita modeli, süsler, engel görselleri |
| Servis | Yerel kayıt (JSON); ileride bulut kayıt ve analitik |

Harita sahnesi ayrı yüklenir ve Match-3 sahnesinde bellekte tutulmaz. Düşük seviye Android cihazlarda bellek ve ısınmayı sınırlamanın ana yolu budur.
