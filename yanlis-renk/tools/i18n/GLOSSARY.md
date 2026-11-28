# Çeviri sözlüğü ve kurallar (Yanlış Renk)

Oyun: sevimli bir karakter kendiliğinden ileri koşar; ekrana dokununca rengi Kırmızı → Mavi → Yeşil → Sarı sırasıyla değişir.
"=" işaretli kapıdan kapıyla AYNI renkte, "≠" işaretli kapıdan FARKLI renkte geçilir. Hedef kitle: her yaştan gündelik oyuncu.

## Ton
- Samimi, kısa, oyunsu. Tekil "sen" hitabı: DE **du**, FR **tu**, IT **tu**, ES **tú**, EN "you".
- Butonlar ve başlıklar kısa olmalı (Almanca için mümkün olan en kısa doğal ifade).
- Büyük harfle yazılmış kaynak metinler (ör. "BÖLÜM TAMAM!", "BAŞLA") çeviride de BÜYÜK HARF olmalı.

## Teknik kurallar
- Anahtarları değiştirme; tüm anahtarlar çevrilmeli. `_comment` anahtarını kopyalama.
- `{0}`, `{1}` yer tutucularını aynen koru (sırası dilin doğal yapısına göre değişebilir).
- Bir sayı sayılabilir bir isimden önce geliyorsa ICU çoğul sözdizimi kullan:
  `{0,plural,one{# gate} other{# gates}}` (`#` sayının yerine geçer). Türkçe kaynakta çoğul yoktur.
- `\n` satır sonlarını aynı sayıda koru. Baştaki/sondaki boşlukları ve " · " ayırıcılarını koru (parça metinler birleştirilir).
- `[` ve `]` karakterlerini KULLANMA (renk etiketi sözdizimidir).
- Fransızcada ! ? : ; öncesinde kesintisiz boşluk (U+00A0) kullan. U+202F kullanma.
- Emoji ve yazı tipinde olmayan semboller (★ → ←) kullanma; "×", "·", "…", "–" serbest.
- Oyun adı "Yanlış Renk" geçen metinlerde adı AYNEN bırak (marka adı ayrıca işlenecek).

## Terimler
| Türkçe | EN | DE | IT | FR | ES |
|---|---|---|---|---|---|
| Bölüm | Level | Level | Livello | Niveau | Nivel |
| Alan | Area | Gebiet | Area | Zone | Zona |
| Dünya | World | Welt | Mondo | Monde | Mundo |
| Can | Life / Lives | Leben | Vita / Vite | Vie / Vies | Vida / Vidas |
| Altın (para) | Gold | Gold | Oro | Or | Oro |
| Elmas (para) | Diamonds | Diamanten | Diamanti | Diamants | Diamantes |
| Kapı | Gate | Tor | Porta | Porte | Puerta |
| Kombo | Combo | Combo | Combo | Combo | Combo |
| Kalkan | Shield | Schild | Scudo | Bouclier | Escudo |
| Sonsuz Mod | Endless Mode | Endlosmodus | Modalità infinita | Mode infini | Modo infinito |
| Ustalık (modu) | Mastery | Meisterschaft | Maestria | Maîtrise | Maestría |
| Haftalık Mücadele | Weekly Challenge | Wochen-Challenge | Sfida settimanale | Défi hebdo | Desafío semanal |
| Görevler | Missions | Missionen | Missioni | Missions | Misiones |
| Başarılar | Achievements | Erfolge | Obiettivi | Succès | Logros |
| Koleksiyon | Collection | Sammlung | Collezione | Collection | Colección |
| Market | Shop | Shop | Negozio | Boutique | Tienda |
| 2 Kişilik Kapışma | 2-Player Duel | 2-Spieler-Duell | Sfida a 2 | Duel à 2 | Duelo a 2 |
| Kırmızı / Mavi / Yeşil / Sarı | Red / Blue / Green / Yellow | Rot / Blau / Grün / Gelb | Rosso / Blu / Verde / Giallo | Rouge / Bleu / Vert / Jaune | Rojo / Azul / Verde / Amarillo |
| Yıldız | Star | Stern | Stella | Étoile | Estrella |
| Seviye (oyuncu) | Level (player) – "Lv." | Stufe | Livello giocatore | Niveau joueur | Nivel de jugador |
| XP | XP | EP | XP | XP | XP |

## Yaratıcı metinler
Karakter, alan, koleksiyon parçası, kostüm ve efekt adları kelimesi kelimesine değil, o dilde sevimli ve doğal gelecek
şekilde uyarlanabilir (ör. "Zıp Top" → "Bouncy Ball"). Kelime oyunu varsa hedef dilde benzerini bul. Anlamı ve oyun
mekaniğini (renk, kapı, hafıza, ayna vb.) koru.
