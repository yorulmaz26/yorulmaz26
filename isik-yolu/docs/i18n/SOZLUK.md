# Yerelleştirme sözlüğü ve kuralları

Metinler `app/src/main/assets/i18n/<dil>.json` dosyalarındadır (düz anahtar → metin). Desteklenen diller:
`tr` (kaynak), `en` (yedek dil), `de`, `it`, `fr`, `es`. Yönetici sınıf: `app/.../i18n/L.java`.

## Kullanım
- `L.t("anahtar")`, yer tutuculu: `L.t("common.level_n", 12)` → "Bölüm 12" / "Level 12".
- Çoğul: değer `{"one": "...", "other": "..."}`; `L.n("common.crystals", n)` ({0} = n, sonrakiler {1}, {2}…).
  Türkçede sayıdan sonra isim tekil kalır ("3 can"), iki biçim de aynı yazılır.
- Sayılar `{0}` içinde dile göre biçimlenir (1.200 / 1,200). Bölüm numarası gibi kimlikler de sayı olarak verilebilir.
- Büyük harf: `toUpperCase(L.locale())` (Türkçede i → İ).
- Tarih: `java.text.DateFormat`/`SimpleDateFormat` ile `L.locale()`.

## Anahtar adları
`common.*` ortak, `lives.*` can, `menu.*`, `splash.*`, `settings.*`, `levels.*`, `game.*`, `tutorial.*`, `market.*`,
`item.<id>.name|desc`, `daily.*`, `ach.<id>.title|desc`, `achievements.*`, `world.<1-5>.name|level|intro`,
`chapter.<1-50>.name`, `topic.<1-10>`, `mechanic.<1-10>`, `color.<ad>`, `ads.*`, `privacy.*`, `dialog.*`.

## Terimler
| Türkçe | English | Deutsch | Italiano | Français | Español |
|---|---|---|---|---|---|
| ışık / ışın | light / beam | Licht / Strahl | luce / raggio | lumière / rayon | luz / rayo |
| ayna | mirror | Spiegel | specchio | miroir | espejo |
| prizma | prism | Prisma | prisma | prisme | prisma |
| renk süzgeci (filtre) | color filter | Farbfilter | filtro colorato | filtre de couleur | filtro de color |
| hedef | target | Ziel | bersaglio | cible | objetivo |
| ışık kaynağı | light source | Lichtquelle | sorgente di luce | source lumineuse | fuente de luz |
| blok / taş | block | Block | blocco | bloc | bloque |
| anahtar / kapı | key / door | Schlüssel / Tür | chiave / porta | clé / porte | llave / puerta |
| bölüm | level | Level | livello | niveau | nivel |
| grup (10 bölüm) | chapter | Kapitel | capitolo | chapitre | capítulo |
| dünya | world | Welt | mondo | monde | mundo |
| kristal | crystal | Kristall | cristallo | cristal | cristal |
| Prizma Taşı | Prism Gem | Prismenstein | Gemma Prisma | Gemme Prisme | Gema Prisma |
| can | life / lives | Leben | vita / vite | vie / vies | vida / vidas |
| Can Kalkanı | Life Shield | Lebensschild | Scudo Vita | Bouclier de vie | Escudo de vida |
| hamle (hakkı) | move(s) (left) | Züge (übrig) | mosse (rimaste) | coups (restants) | movimientos (restantes) |
| ipucu | hint | Hinweis | suggerimento | indice | pista |
| geri al | undo | Rückgängig | annulla | annuler | deshacer |
| günlük bulmaca | daily puzzle | Tagesrätsel | puzzle del giorno | puzzle du jour | puzle diario |
| başarı | achievement | Erfolg | obiettivo | succès | logro |
| market | shop | Shop | negozio | boutique | tienda |
| görünüm | skin / look | Design | aspetto | apparence | aspecto |
| ödüllü video | rewarded video | Video mit Belohnung | video con premio | vidéo récompensée | vídeo con recompensa |
| yıldız | star | Stern | stella | étoile | estrella |

## Üslup
- Oyuncuya "sen" diye hitap (tr), en: "you"; de: "du"; it: "tu"; fr: "tu"; es: "tú". Sıcak, kısa, net.
- Düğmeler kısa: mümkünse 1–3 kelime. Almanca uzun bileşik kelimelerden kaçınılır.
- Oyunda olmayan özellik, abartı ve "en iyi" türü iddia yok.
