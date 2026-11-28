# İçerik Derecelendirmesi ve Hedef Kitle

**Uygulama:** Gölgen Seni Yakalamasın / Doppelglow (geçici) — `com.golgen.oyun` — v1.1.0

## 1. IARC anketi — önerilen cevaplar

Kategori: **Game (Oyun)** — Arcade

| Soru başlığı | Önerilen cevap | Gerekçe |
|---|---|---|
| Şiddet (Violence) | **Hayır** | Soyut geometrik şekiller ve ışık efektleri; karakter, yaralanma veya öldürme yok. "Gölgeye yakalanma" yalnızca turun bitmesidir. |
| Kan / vahşet (Blood/Gore) | **Hayır** | |
| Korku unsurları (Fear) | **Hayır** | Ürkütücü görsel veya ses yok (gerekirse "gölge" teması gözden geçirilmeli) |
| Cinsellik / çıplaklık (Sexuality/Nudity) | **Hayır** | |
| Kaba dil (Language) | **Hayır** | |
| Uyuşturucu, alkol, tütün (Controlled substances) | **Hayır** | |
| Kumar / simüle kumar (Gambling) | **Hayır** | Ödüllü reklam şans unsuru içermez; sabit +1 can verir. Ganimet kutusu yok. |
| Ayrımcılık / nefret | **Hayır** | |
| Kullanıcı etkileşimi (Users interact / communicate) | **Hayır** | Sohbet, çok oyunculu mod veya kullanıcı içeriği yok |
| Kişisel bilgi paylaşımı (Shares personal info) | **Hayır** | |
| Konum paylaşımı (Shares location with other users) | **Hayır** | Konum izni yok |
| Dijital ürün satın alma (Digital purchases) | **Hayır** | Uygulama içi satın alma yok |
| Reklam içerir (Contains ads) | **Evet** | AdMob banner, geçiş ve isteğe bağlı ödüllü reklam |
| İnternet erişimi gereksiz/ sınırsız web | **Hayır** | Tarayıcı yok; internet yalnızca reklamlar için |

## 2. Beklenen derecelendirmeler (yalnızca beklenti)

| Sistem | Beklenen |
|---|---|
| PEGI (Avrupa) | **PEGI 3** |
| ESRB (Kuzey Amerika) | **Everyone** |
| USK (Almanya) | **USK 0** |
| Diğer (ClassInd, ACB, GRAC vb.) | En düşük / genel izleyici seviyesi |

> Bunlar **yalnızca beklentidir**. Kesin derecelendirmeyi IARC anketi sonucunda sistem belirler. Not: Bazı bölgelerde reklam içeriği nedeniyle (ör. reklam ağının gösterebileceği içerik) ek açıklama/derecelendirme notu çıkabilir.

## 3. Hedef kitle (Target audience and content)

**Önerilen yaş grupları:** **13-15, 16-17, 18+** (13 yaş altı seçilmemeli)

**Gerekçeler:**

- Oyun, Google Mobile Ads SDK ile **kişiselleştirilmiş reklam** gösterebilir ve reklam kimliği (AD_ID) kullanır. 13 yaş altı hedef kitle, bu yapılandırmayla uyumlu değildir.
- 13 yaş altı seçmek, uygulamayı Google Play **Families (Aileler) politikası** kapsamına sokar; bu durumda:
  - Yalnızca **Families sertifikalı reklam SDK'ları** ve uygun reklam ayarları kullanılmalı,
  - AdMob isteklerinde **`tagForChildDirectedTreatment`** (çocuklara yönelik işleme) ve gerekirse `tagForUnderAgeOfConsent` ayarlanmalı, maksimum reklam içerik derecelendirmesi (`G`) sınırlanmalı,
  - Kişiselleştirilmiş reklam ve reklam kimliği kullanımı kısıtlanmalı (AD_ID izni kaldırılmalı),
  - COPPA ve ilgili çocuk gizliliği gerekliliklerine uyulmalı, gizlilik politikası buna göre yeniden yazılmalı.
- Oyun içeriği çocuklar için zararlı değildir; ancak mevcut reklam yapılandırması nedeniyle hedef kitle 13+ olarak belirlenmiştir.

**"Appeals to children" (çocuklara hitap ediyor mu?) sorusu:** Görsel stil (parlak soyut şekiller) nedeniyle Google bu soruyu sorabilir. Mağaza görselleri ve metinlerinde çocuklara özel dil/karakter kullanılmamalı; cevap dikkatle verilmelidir.

## 4. Diğer beyanlar

| Field | Cevap |
|---|---|
| Contains ads | **Yes / Evet** |
| In-app purchases | **No / Hayır** |
| Government app / News app / COVID-19 / Financial features / Health | Hayır |
| Data safety | Bkz. `data_safety_form_draft.md` |

---
*Göndermeden önce hukuki inceleme önerilir.*
