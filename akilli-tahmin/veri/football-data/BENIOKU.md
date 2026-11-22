# football-data.co.uk dosya yansısı

Bu klasördeki CSV dosyaları [football-data.co.uk](https://www.football-data.co.uk) sitesinin ücretsiz
dosyalarının **değiştirilmemiş kopyalarıdır** (maç programı, sonuçlar ve maç öncesi oranlar). Bütün hakları
ve veri sorumluluğu kaynağa aittir.

Neden var: bazı ağlarda (ör. Türkiye'deki bazı internet sağlayıcıları) football-data.co.uk açılmıyor
(sertifika hatası / erişilemeyen ağ). Akıllı Tahmin bu durumda aynı dosyaları buradan alır
(`akilli_tahmin/data/football_data_feed.py`, `MIRROR_URL`).

Güncelleme: `python scripts/mirror_football_data.py` (günlük otomatik görevle çalıştırılır). Dosya yolları
kaynaktakiyle aynıdır: `mmz4281/<sezon>/<lig>.csv`, `fixtures.csv`, `new/<lig>.csv`,
`new_league_fixtures.csv`.
