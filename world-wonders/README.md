# World Wonders: oynanabilir prototip

Match-3 + Jigsaw Puzzle + 3D dünya haritası. Tek dosyalık bir web prototipi. Harici görsel kullanmıyor: taşlar, engeller ve harika illüstrasyonlarının hepsi kodla (Canvas) çiziliyor.

## Çalıştırma
`index.html` dosyasını bir tarayıcıda açmak yeterli (mobilde de çalışır). İlerleme tarayıcıya (`localStorage`) kaydediliyor.

## İçerik
- **Match-3:** 8×8 tahta, 6 sabit tarihî taş, sürükleme veya dokunarak takas, ipucu, otomatik karıştırma
- **Özel güçler:** 4'lü (satır/sütun), T/L (Tunç Bomba, 3×3), 5'li (Güneş Diski) ve 6 farklı kombinasyon
- **Engeller:** blok (1–3 can), katman, en alta indirilecek eser. Her harikada farklı görünüm ve isim
- **Hedefler:** taş toplama, engel kırma, katman temizleme, eser indirme, puan
- **Puzzle:** her 5 bölümde 1 parça. Parça ortada belirir, oyuncu dokununca uçup yerine oturur; son parçada tamamlama animasyonu oynar
- **Ara ödül:** her parça haritada harikanın çevresinde bir süs açar (12 süs/harika)
- **Zorluk:** testere dişi eğri. Döngünün 1. bölümü nefes, 5. bölümü "ZOR · PARÇA" bölümü; bot testleriyle ayarlandı (bkz. TASARIM.md)
- **3D Harita:** eğimli dünya düzlemi, ayakta duran anıtlar, rota, sürükleyerek gezinme, tamamlanan harikanın "yükselme" animasyonu
- **Can:** 3 can, para ve güçlendirici yok. Kaybedilen bölüm 1 can götürür, 20 dakikada 1 can yenilenir, puzzle parçası kazanınca hepsi dolar
- **Koleksiyon:** 7 harika ve "Antik Dünya: yakında" kartı

## Test modu
Ayarlar (⚙) → **Test modu** açıldığında oyun ekranına "Bölümü Kazan" düğmesi, ayarlara da "parçaya atla" ve "canları doldur" seçenekleri ekleniyor.

## Tasarım
Konsept analizi ve öncelikli eksik kararlar: [`TASARIM.md`](TASARIM.md)
