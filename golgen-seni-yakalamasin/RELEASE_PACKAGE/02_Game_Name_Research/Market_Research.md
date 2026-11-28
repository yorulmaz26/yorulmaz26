# Pazar ve marka araştırması

- **Tarih:** 27 Kasım 2026
- **Yöntem:** Her aday için mağaza, web, sosyal medya ve marka veritabanlarında tam ad + oyun bağlamında arama. Web arama aracı ve doğrudan sayfa okuma kullanıldı; erişilemeyen kaynaklar aşağıda ayrıca listelendi.
- **Önemli sınırlama:** Bu çalışma ortamından Google Play, App Store, Steam ve itch.io sayfalarına doğrudan bağlanılamadı (DNS/ağ engeli). Bu kaynaklar yalnızca arama motoru dizini üzerinden tarandı. **USPTO, WIPO Global Brand Database, EUIPO ve TÜRKPATENT veritabanlarının hiçbirine erişilemedi** (JavaScript, oturum veya ağ engeli). Son doğrulama turu, oturumun web arama kotası (200 arama) dolduğu için yapılamadı.
- **Sonuç:** Benzer isim bulunmaması, ismin serbest olduğu veya tescil edilebileceği anlamına **gelmez**. Seçilen isim **geçici adaydır**; tescil ve yayın öncesi marka avukatı veya resmî veritabanlarıyla manuel kontrol gereklidir.

## Kaynak erişim özeti (tüm adaylar)

| Kaynak | Erişilen sorgu | Erişilemeyen |
|---|---|---|
| Apple App Store | 0 | 16 |
| Apple App Store (iTunes Search API) | 0 | 8 |
| Apple App Store (web search) | 7 | 1 |
| Bing | 0 | 20 |
| CIPO Canada (supplementary) | 1 | 0 |
| EUIPO | 0 | 12 |
| EUIPO eSearch | 0 | 12 |
| General web search (WebSearch tool; engine not identified, used in place of Google) | 4 | 0 |
| GitHub (repo README) | 0 | 1 |
| Google | 0 | 8 |
| Google (general web search via WebSearch tool) | 4 | 0 |
| Google (general web search) | 4 | 0 |
| Google / Bing general web | 4 | 0 |
| Google Play | 4 | 8 |
| Google Play (direct fetch) | 0 | 12 |
| Google Play (search index) | 4 | 0 |
| Google Play (web search) | 7 | 1 |
| Instagram | 10 | 10 |
| Instagram / TikTok | 4 | 0 |
| Steam | 0 | 17 |
| Steam (store search API) | 0 | 3 |
| Steam (storesearch API) | 0 | 4 |
| Steam (web search) | 7 | 0 |
| TURKPATENT | 0 | 12 |
| TikTok | 10 | 10 |
| Trademark aggregators (web search) | 2 | 2 |
| TÜRKPATENT | 0 | 12 |
| UK IPO (supplementary) | 1 | 0 |
| USPTO | 0 | 20 |
| USPTO (via indexed aggregators) | 0 | 4 |
| WIPO GBD | 0 | 8 |
| WIPO Global Brand Database | 0 | 16 |
| YouTube | 15 | 9 |

## Aday bazında bulgular

### Doppelglow — çakışma riski: low

- Aynı adlı oyun bulundu mu: hayır (erişilen kaynaklarda); benzer oyun: evet
- Marka kaydı: No DOPPELGLOW mark found in US aggregator results (indirect only). Nearby DOPPEL marks belong to Doppel Inc. (98021948, 99007489), and there is a DOPPELGÄNGER fireworks mark. WIPO, EUIPO and TURKPATENT were not checked.
- Kültürel/dilsel: de: 'Doppel' means double; the doppelgänger pun reads naturally in German, and the German-English mix is fine. en: the doppelgänger idea is widely understood and fits the shadow-double mechanic. tr: 'Doppel' means nothing in Turkish and may be hard to spell or pronounce ('dopelglov'). it/fr/es: no negative meanings, though less intuitive. No offensive readings found in en/tr/de/it/fr/es.
- Not: 10 characters. This is the most distinctive candidate: zero web, store or social hits for the exact string in the sources searched. 'Similar' only covers doppelgänger-themed titles (e.g. Steam 'The Doppel', reflection-chase) and non-game 'Doppel' apps or marks. The risk rating is provisional because the iTunes/Steam APIs, the Play Store page and all four trademark offices could not be queried directly. Two blockers: the session network allowlist (ENOTFOUND on itunes.apple.com and store.steampowered.com) and the exhausted web search budget (limit 200 per turn). WIPO, EUIPO and TURKPATENT should be checked manually before filing.

| Kaynak | Yöntem | Erişildi | Bulgular |
|---|---|---|---|
| Google Play | WebSearch "Doppelglow" site:play.google.com. Direct store fetch not attempted (network block). | evet | No identical match in the results. Nearest was 'Dopples World' by TutoTOONS, plus developer pages such as Doppamo. |
| Apple App Store | WebFetch https://itunes.apple.com/search?term=Doppelglow&entity=software&limit=25 ; WebSearch "Doppelglow" OR "Doppel Glow" site:apps.apple.com | **hayır** | iTunes API: error 'getaddrinfo ENOTFOUND itunes.apple.com'. The site: search found no identical match. Similar non-game apps: DoppelG (AI lookalike finder, MaxQ LLC, https://apps.apple.com/ca/app/doppelg/id6746659494), Doppel - AI Focus Companion (id6755030867), Deepglow (id6748624385), DuoGlow (https://apps.apple.com/gb/app/duoglow/id6769582160). |
| Steam | WebFetch https://store.steampowered.com/api/storesearch/?term=Doppelglow&cc=US&l=en ; WebSearch "Doppelglow" site:store.steampowered.com | **hayır** | API: error 'getaddrinfo ENOTFOUND store.steampowered.com'. The site: search found no identical title. Similar: Doppelgänger (app 2687600, visual novel), Doppelganger (app 3923690, space tactics), The Doppel (app 3711330, a 2D action game about fleeing your own reflection, conceptually close). |
| Google (general web search via WebSearch tool) | "Doppelglow" (standard); "Doppelglow" OR "Doppel Glow" game app (extended) | evet | No results anywhere for 'Doppelglow'. Only doppelgänger topics (Wikipedia; an Astronomy magazine article on a 'doppelganger effect' glowing shadow, https://www.astronomy.com/observing/the-doppelganger-effect/) and DuoGlow. |
| Bing | Not run separately (no engine choice in WebSearch; bing.com fetch not attempted because of the network block). | **hayır** | No Bing-specific results. |
| YouTube | WebSearch "Doppelglow" site:youtube.com | evet | No identical match in the results; only 'Doppelgänger' music videos. |
| Instagram | WebSearch "Doppelglow" site:instagram.com | evet | No identical match in the results. |
| TikTok | WebSearch "Doppelglow" site:tiktok.com | evet | No identical match in the results. |
| USPTO | WebSearch "DOPPELGLOW" trademark OR brand (aggregators justia/trademarkelite/markinton). Direct tmsearch.uspto.gov not attempted (network block). | **hayır** | Indirect only: no DOPPELGLOW mark found. Nearby: DOPPEL serial 98021948 (Doppel Inc., ex parte appeal pending, https://trademarks.justia.com/980/21/doppel-98021948.html), DOPPEL serial 99007489 (statement of use review complete), DOPPELGÄNGER (fireworks, class 13). |
| WIPO Global Brand Database | Not attempted (network block); site: search refused (search budget exhausted). | **hayır** | Not checked. |
| EUIPO | Not attempted (network block); site: search refused (search budget exhausted). | **hayır** | Not checked. |
| TURKPATENT | Not attempted (network block); site: search refused (search budget exhausted). | **hayır** | Not checked. |

### Ghostrail — çakışma riski: low

- Aynı adlı oyun bulundu mu: hayır (erişilen kaynaklarda); benzer oyun: evet
- Marka kaydı: None found in web/aggregator searches (closest: GHRAIL, Class 25 clothing; various GHOST-formative marks). The ghostrail.org crypto privacy protocol is an unregistered software use that could later be filed in Classes 9/36/42. Official databases were not checked directly.
- Kültürel/dilsel: en: a coined compound ('ghost' + 'rail'). It suggests ghost trains or rails, which do not match the drag-and-dodge mechanic. It can also be misread as 'Ghos-trail' (Ghost Trail), which fits the shadow-trail idea but causes confusion with existing 'Ghost Trail' titles. Minor slang note: 'rail' can mean a line of cocaine in English slang (low risk). tr/de/fr/es/it: no meaning and no negative associations; 'ghost' is widely understood. Mild horror connotation, acceptable for 13+.
- Not: The most distinctive of the four. No game, app or registered mark named Ghostrail was found. Existing uses are an academic paper, a niche crypto protocol (ghostrail.org) and a fiction story. Watch the similar 'Ghost Trail' titles (iOS by Gosti World; itch.io by aoyama11 with a ghost-path mechanic). Search ranking should be easy. Before adoption, run official USPTO/EUIPO/WIPO/TÜRKPATENT searches in Classes 9/28/41, since the direct lookups here were blocked by the session network allowlist (itunes.apple.com and store.steampowered.com ENOTFOUND; bash proxy CONNECT 403).

| Kaynak | Yöntem | Erişildi | Bulgular |
|---|---|---|---|
| Google Play (direct fetch) | Not attempted: https://play.google.com/store/search?q=Ghostrail&c=apps (network allowlist). | **hayır** | Access blocked by the session network allowlist. |
| Google Play (web search) | WebSearch: "Ghostrail" site:play.google.com ; "Ghostrail" app OR game Android Google Play | evet | No identical match in results. Unrelated similar names: GhostBuzz, GhostMap, Ghost Dash, developer 'Ghostr' https://play.google.com/store/apps/developer?id=Ghostr |
| Apple App Store (iTunes Search API) | WebFetch https://itunes.apple.com/search?term=Ghostrail&entity=software&limit=50, plus curl via Bash | **hayır** | WebFetch: 'getaddrinfo ENOTFOUND itunes.apple.com'. curl: CONNECT tunnel failed, response 403. |
| Apple App Store (web search) | WebSearch: "Ghostrail" OR "Ghost Rail" site:apps.apple.com ; "Ghost Trail" game Gosti World | evet | No exact 'Ghostrail'. Similar game: 'Ghost Trail' (Gosti World, iPhone, free strategy/reflex game where you leave a colored trail) https://apps.apple.com/pa/app/ghost-trail/id6738310929. Similar non-games: GhostTrail: Sensor Logbook (id6771639973), Ghostr: Running Competition https://apps.apple.com/app/id6744453903. Train-themed: Ghost Train, Ghost Engine. |
| Steam (storesearch API) | curl via Bash https://store.steampowered.com/api/storesearch/?term=Ghostrail&cc=US&l=en | **hayır** | curl: HTTP 000 (proxy CONNECT refused). WebFetch: ENOTFOUND. |
| Steam (web search) | WebSearch: "Ghostrail" OR "Ghost Rail" site:store.steampowered.com | evet | No identical match in results. Similar: GrimRail (upcoming horror, train) ; Frostrail https://store.steampowered.com/app/3517740 ; Ghost Engine https://store.steampowered.com/app/4440730 ; Spectral Trail https://store.steampowered.com/app/2512280 |
| Google / Bing general web | WebSearch: "Ghostrail" (extended) ; "Ghostrail" game ; "Ghost Rail" game | evet | No game named Ghostrail found. Non-game uses: 'Ghostrail: Ad Hoc Control-Flow Integrity for Web Applications' (2014 security paper) https://link.springer.com/chapter/10.1007/978-3-642-55415-5_22 ; Ghostrail crypto privacy protocol for Robinhood Chain https://ghostrail.org/ ; FictionPress story https://www.fictionpress.com/s/3373348/1/Ghostrail. Similar games: Ghost Trail (itch.io, aoyama11; ghosts |
| YouTube | WebSearch: "Ghostrail" site:youtube.com OR site:tiktok.com OR site:instagram.com | evet | No identical match in results. |
| Instagram / TikTok | WebSearch: "Ghostrail" site:youtube.com OR site:tiktok.com OR site:instagram.com | evet | No identical match in results (only generic 'ghost' trend pages). Not conclusive for handle availability. |
| USPTO | Direct tmsearch.uspto.gov not attempted (network allowlist). WebSearch: "GHOSTRAIL" trademark | **hayır** | Direct database access blocked. Aggregator results showed no GHOSTRAIL mark. Nearest: GHRAIL (Class 25 clothing), GHOSTRIDGE, GHOSTTRACE IR, GHOST RACING, GHOSTRAGE, and many GHOST-formative marks. Absence is not confirmed. |
| WIPO Global Brand Database | Direct branddb.wipo.int not attempted (network allowlist, JS app). | **hayır** | Database not reachable. Unverified. |
| EUIPO eSearch | Direct euipo.europa.eu not attempted (network allowlist, JS app). | **hayır** | Database not reachable. Unverified. |
| TÜRKPATENT | Direct turkpatent.gov.tr not attempted (network allowlist). WebSearch: combined marka TÜRKPATENT query | **hayır** | Database not reachable. No Turkish record surfaced. Unverified. |

### Umbrix — çakışma riski: medium

- Aynı adlı oyun bulundu mu: hayır (erişilen kaynaklarda); benzer oyun: evet
- Marka kaydı: Exact: Indian application 'Umbrix' No. 8054430, Class 18 (umbrellas), owner MOHD ZAKIR, seen only on a third-party aggregator (registerkaro.in) and unverified. It is not a game or software class. Near: UMBRIXIA, US 99710344, Class 7, pending; UMBREX, AU, Class 35; UMBREL, CA, Class 9. Official WIPO, EUIPO, USPTO and TÜRKPATENT databases were not accessed (network block), so Classes 9 and 41 are not cleared.
- Kültürel/dilsel: Comes from Latin 'umbra' (shadow), which fits the shadow mechanic. The meaning is easy to guess in Romance languages: it. ombra, es. sombra/umbrío, fr. ombre. In tr and de it reads as an invented word. The '-ix' ending strongly recalls Asterix/Obelix-style names in fr/de/it/es (playful, not negative). It can also suggest Umbria (the Italian region) or 'umbrella'. No offensive or negative meaning found in en/tr/de/it/fr/es (own linguistic assessment, not a native-speaker review). Easy to pronounce ('UM-briks') in all six languages.
- Not: No game or app is titled Umbrix on Play, App Store or Steam (web-search level only; direct store APIs were blocked). Weaker points: an Umbrix boss in the Roblox game Tower Defense X, several small software uses (Discord bot, umbrix.dev cyber platform, an open-source VPN in design), a beer, and a crowded 'Umbra' namespace on Steam and the App Store. None appears to be a registered game or software mark in the results seen. The name is short (6 chars), which leaves room in the 30-char title for a subtitle (e.g. 'Umbrix: Shadow Dodge'). Before adopting it, verify on the official registries for Classes 9, 28 and 41.

| Kaynak | Yöntem | Erişildi | Bulgular |
|---|---|---|---|
| Google Play (web search) | WebSearch standard: "Umbrix" site:play.google.com | evet | No identical match in results: no app or developer named Umbrix. Only similar developer pages came up: Umbrava (play.google.com/store/apps/developer?id=Umbrava), UMBERA, UMITRON, UMMIS, Umy. |
| Google Play (direct fetch) | WebFetch https://play.google.com/store/search?q=Umbrix&c=apps | **hayır** | Error: 'getaddrinfo ENOTFOUND play.google.com. In this cloud session a host fails to resolve like this when it is outside the session's allowed network domains'. play.google.com has to be allowed in the session's network settings. |
| Apple App Store (web search) | WebSearch standard: "Umbrix" site:apps.apple.com | evet | No identical match in results. Similar names: 'Umbrex: Music Player for Plex' (https://apps.apple.com/app/id6792601467), 'Umbric: Private Web Browser' (id6742196922, seen via chrome-stats.com), Umbra Browser (id6761460876), Umbras (id6504777613), Umbroll (id1442189648), Umbrella Drop (id1183233831). |
| Apple App Store (iTunes Search API) | WebFetch https://itunes.apple.com/search?term=Umbrix&entity=software&limit=25 ; also curl via Bash | **hayır** | WebFetch error: 'getaddrinfo ENOTFOUND itunes.apple.com' (host is outside the session's allowed network domains). curl error: 'curl: (56) CONNECT tunnel failed, response 403'. |
| Steam (web search) | WebSearch standard: "Umbrix" site:store.steampowered.com | evet | No identical match in results. Similar titles all use 'Umbra': UMBRA (https://store.steampowered.com/app/4331360), Umbra: Veins of Vengeance (app/2414850), Umbra: Shadow of Death (app/421180, delisted), Umbra: Journey Home (app/1711720). |
| Steam (store search API) | WebFetch https://store.steampowered.com/api/storesearch/?term=Umbrix&cc=US&l=en ; also curl via Bash | **hayır** | WebFetch error: 'getaddrinfo ENOTFOUND store.steampowered.com' (outside allowed network domains). curl error: 'curl: (56) CONNECT tunnel failed, response 403'. |
| Google (general web search) | WebSearch standard: "Umbrix" game ; WebSearch extended: "Umbrix" ; WebSearch standard: "Umbrix" app OR software OR studio OR brand | evet | No game is titled Umbrix. Other uses of the exact name: (1) a boss character called Umbrix in the Roblox game Tower Defense X (https://tdx.fandom.com/wiki/Umbrix, https://villains.fandom.com/wiki/Umbrix); (2) a planet in a fan wiki (the-neuvoh-system.fandom.com/wiki/Umbrix); (3) Mike Hess Brewing 'Umbrix' imperial stout (untappd.com/b/mike-hess-brewing-umbrix/12540, beeradvocate); (4) a Discord lo |
| Bing | Not run separately. The only search tool available (WebSearch) is one backend, and fetching bing.com directly was not tried because WebFetch reported the network allowlist block. | **hayır** | No Bing-specific results. Coverage depends on the Google-style WebSearch results above. |
| YouTube | WebSearch standard: "Umbrix" site:youtube.com OR site:instagram.com OR site:tiktok.com | evet | No identical match in results. The search engine found no YouTube video or channel named Umbrix. Results were Umbra, Umbro and Umbrella brands. |
| Instagram | Same combined WebSearch: "Umbrix" site:youtube.com OR site:instagram.com OR site:tiktok.com | evet | No identical match in results. Only Umbra home-goods posts came up, via particl.com. |
| TikTok | Same combined WebSearch: "Umbrix" site:youtube.com OR site:instagram.com OR site:tiktok.com | evet | No identical match in results. |
| Trademark aggregators (web search) | WebSearch standard: "Umbrix" trademark | evet | One exact hit, from a third party only: registerkaro.in (https://www.registerkaro.in/trademark-search/umbrix-8054430) lists Indian application 'Umbrix' No. 8054430, Class 18 (umbrellas), owner MOHD ZAKIR, status 'Ready for Examination'. Not verified on IP India. Near marks: UMBRIXIA, US serial 99710344, Class 7, pending, filed 2026-03-18 (trademarks.justia.com/997/10/umbrixia-99710344.html); UMBRE |
| WIPO Global Brand Database | Intended: https://branddb.wipo.int/en/similarname (search 'Umbrix'). Not fetched. | **hayır** | Not accessed. WebFetch could not resolve 3 hosts (play.google.com, itunes.apple.com, store.steampowered.com: 'getaddrinfo ENOTFOUND ... outside the session's allowed network domains'), and the tool said to stop trying more sites. branddb.wipo.int would need to be allowed in network settings, and it also needs JavaScript. |
| EUIPO eSearch | Intended: https://euipo.europa.eu/eSearch/ (search 'Umbrix'). Not fetched. | **hayır** | Not accessed. Same network allowlist block as above (hosts outside allowed domains return ENOTFOUND). |
| USPTO | Intended: https://tmsearch.uspto.gov/search/search-results (search 'Umbrix'). Not fetched. | **hayır** | Not accessed. Same network allowlist block. Aggregator results above showed no exact US UMBRIX record, but this is not an official check. |
| TÜRKPATENT | Intended: https://www.turkpatent.gov.tr/arastirma-yap (search 'Umbrix'). Not fetched. | **hayır** | Not accessed. Same network allowlist block. The site also needs e-Devlet login or JavaScript for searches. |

### Ombrix — çakışma riski: unknown

- Aynı adlı oyun bulundu mu: hayır (erişilen kaynaklarda); benzer oyun: hayır
- Marka kaydı: Not checked. All trademark databases were blocked by the egress proxy (403). No trademark status can be stated.
- Kültürel/dilsel: Linguistic review from general knowledge only, not checked against the web. The name is FR 'ombre' / IT 'ombra' (shadow) plus '-ix'. FR: reads naturally, and the '-ix' ending recalls Asterix-style Gaulish names, which is a playful and positive association. IT: the root 'ombra' is clear. ES: the sound is close to 'hombre' (man) and 'sombra' (shadow); harmless. TR: no meaning and easy to pronounce ('om-briks'). DE: no problem known. EN: short and pronounceable. No profanity known. Caveat: short coined '-ix' names are common in software, tech, pharma and cosmetics, so the chance of earlier registrations is higher than for the other three names. This could not be verified.
- Not: No source could be reached: the egress proxy blocked every host and the WebSearch budget was used up. Nothing can be said about existing games or trademarks, and the booleans mean 'not determined'. Do not treat the name as free. Because of the short '-ix' pattern, check this name in trademark databases with priority once access is restored (classes 9, 28, 41). It is 6 characters.

| Kaynak | Yöntem | Erişildi | Bulgular |
|---|---|---|---|
| Google Play | Planned: WebSearch '"Ombrix" site:play.google.com' and fetch https://play.google.com/store/search?q=Ombrix&c=apps | **hayır** | Access error: the play.google.com host is refused by the egress proxy ('CONNECT tunnel failed, response 403', confirmed with the queries for Umbrisk and Glintail). WebSearch is unavailable because the per-turn budget of 200 is used up. No results seen. |
| Apple App Store | WebFetch https://itunes.apple.com/search?term=Ombrix&entity=software&limit=25 and curl of the same URL | **hayır** | Access error: WebFetch 'getaddrinfo ENOTFOUND itunes.apple.com'. curl: proxy connect_rejected HTTP 403. No results seen. |
| Steam | curl https://store.steampowered.com/api/storesearch/?term=Ombrix&cc=US&l=en | **hayır** | Access error: proxy connect_rejected HTTP 403 for store.steampowered.com:443. No results seen. |
| Google | WebSearch query: "Ombrix" (mode standard) | **hayır** | Search not performed: the web search budget for this turn is used up (limit 200, shared). No results seen. |
| Bing | Planned: https://www.bing.com/search?q=%22Ombrix%22+game | **hayır** | Access error: bing.com is refused by the egress proxy (HTTP code 000 in the Umbrisk test). No results seen. |
| YouTube | Planned: site:youtube.com "Ombrix" / https://www.youtube.com/results?search_query=Ombrix | **hayır** | Access error: youtube.com gave 'CONNECT tunnel failed, response 403'. Search budget used up. |
| Instagram | Planned: site:instagram.com "Ombrix" | **hayır** | Access error: instagram.com gave 'CONNECT tunnel failed, response 403'. Search budget used up. |
| TikTok | Planned: site:tiktok.com "Ombrix" | **hayır** | Access error: tiktok.com gave 'CONNECT tunnel failed, response 403'. Search budget used up. |
| WIPO GBD | https://branddb.wipo.int/ (and the api.branddb.wipo.int host) | **hayır** | Access error: proxy refused (000 / 403). Not searched. |
| EUIPO | https://euipo.europa.eu/eSearch/ ; https://www.tmdn.org/tmview/ | **hayır** | Access error: proxy refused (403 / 000). Not searched. |
| USPTO | https://tmsearch.uspto.gov/ | **hayır** | Access error: 'CONNECT tunnel failed, response 403'. Not searched. |
| TURKPATENT | https://www.turkpatent.gov.tr/ | **hayır** | Access error: 'CONNECT tunnel failed, response 403'. Not searched. |

### Echoveer — çakışma riski: low

- Aynı adlı oyun bulundu mu: hayır (erişilen kaynaklarda); benzer oyun: evet
- Marka kaydı: Unknown. The trademark web search could not run (search budget used up), and the official WIPO, EUIPO, USPTO and TÜRKPATENT databases were not accessed (network block). No trademark for 'Echoveer' appeared in any of the general results.
- Kültürel/dilsel: 'Echo' is understood in all six languages (tr. eko, de. Echo, it./es. eco, fr. écho). 'Veer' (English: to swerve suddenly) suits dodging, but most non-English players will not know it. Pronunciation and spelling are unclear: Germans read v as f ('Echo-feer'); Turkish, Spanish and Italian players may say 'eko-ver', and 'ch' may be read as 'ç' in Turkish. Players may misspell it as Echover, Echovere or Echo Veer, which hurts search and word of mouth. In es/it 'ver/vere' carries harmless senses ('to see', 'true'). Dutch 'veer' means feather or spring (not a target language, harmless). No offensive meaning found (own linguistic assessment, not a native-speaker review).
- Not: This is the most distinctive candidate. No exact match on Google Play, the App Store or Steam (web-search level), or in the general extended web search. The 'Echo' prefix is crowded (EchoVerse, Echovo, Echoveil, Echovore, many 'Echo ...' games), so it is distinctive only as a whole word. The check is incomplete: social media (YouTube, Instagram, TikTok) and trademark searches did not run because the WebSearch budget ran out, and direct store and registry fetches were blocked by the network allowlist. 'Low' therefore rests on the sources that were checked; verify on WIPO, EUIPO, USPTO and TÜRKPATENT before adopting. Fits the 30-char limit (8 chars).

| Kaynak | Yöntem | Erişildi | Bulgular |
|---|---|---|---|
| Google Play (web search) | WebSearch standard: "Echoveer" site:play.google.com | evet | No identical match in results. Similar developer pages: Echora, EchoPM, Echonym, Echio, EchoAI; and Echovon Inc on AppBrain. |
| Google Play (direct fetch) | Not attempted for this name: https://play.google.com/store/search?q=Echoveer&c=apps | **hayır** | The host already failed with 'getaddrinfo ENOTFOUND play.google.com' (outside allowed network domains). |
| Apple App Store (web search) | WebSearch standard: "Echoveer" site:apps.apple.com | evet | No identical match in results. Similar: EchoVerse (https://apps.apple.com/il/app/echoverse/id6743813329), Echovo - TTS & Voice Cloning (id6758773035), EchoVis Game (id1377664103), Echo Matches (id6769743962). |
| Apple App Store (iTunes Search API) | WebFetch https://itunes.apple.com/search?term=Echoveer&entity=software&limit=25 | **hayır** | Error: 'getaddrinfo ENOTFOUND itunes.apple.com' (outside the session's allowed network domains). |
| Steam (web search) | WebSearch standard: "Echoveer" site:store.steampowered.com | evet | No identical match in results. Similar: Echo Storm (app/2282200), Echo Seekers (app/5190100), Echo Core (app/3956590), Echo Stalker (app/3215800), Pioneer Venture: Echo (app/5314900). |
| Steam (store search API) | Not attempted for this name: https://store.steampowered.com/api/storesearch/?term=Echoveer&cc=US&l=en | **hayır** | The host already failed with 'getaddrinfo ENOTFOUND store.steampowered.com'. curl also got 'CONNECT tunnel failed, response 403'. |
| Google (general web search) | WebSearch extended: "Echoveer" ; WebSearch standard: "Echoveer" game | evet | No exact match for 'Echoveer' anywhere in the results. Close names: Echovore, a horror TTRPG (https://theraspyraven.itch.io/echovore); Echoveil: The Last Guest, a PC game released 2025-07-06 (https://gamefaqs.gamespot.com/pc/542868-echoveil-the-last-guest/data); Echo Weaver, a 2D platformer; Echovibes, a multiplayer music game (Product Hunt). Similar mechanic, different name: Echo (2017, Ultra Ult |
| Bing | Not run separately (single WebSearch backend; direct fetch not attempted because of the network allowlist block). | **hayır** | No Bing-specific results. |
| YouTube | Planned: WebSearch "Echoveer" site:youtube.com OR site:instagram.com OR site:tiktok.com | **hayır** | Not performed. Error: 'Web search was not performed: this turn's web search budget is used up (limit: 200 WebSearch calls per turn, shared by every agent in it).' The general extended search showed no YouTube result for Echoveer. |
| Instagram | Planned: same combined site: query | **hayır** | Not performed (WebSearch budget exhausted, same error as above). |
| TikTok | Planned: same combined site: query | **hayır** | Not performed (WebSearch budget exhausted, same error as above). |
| Trademark aggregators (web search) | Planned: WebSearch "Echoveer" trademark OR "echoveer" brand | **hayır** | Not performed. Error: 'Web search was not performed: this turn's web search budget is used up (limit: 200 WebSearch calls per turn...)'. |
| WIPO Global Brand Database | Intended: https://branddb.wipo.int/en/similarname (search 'Echoveer'). Not fetched. | **hayır** | Not accessed. Network allowlist block (ENOTFOUND for hosts outside allowed domains; the tool said to stop trying more sites). |
| EUIPO eSearch | Intended: https://euipo.europa.eu/eSearch/ (search 'Echoveer'). Not fetched. | **hayır** | Not accessed. Network allowlist block. |
| USPTO | Intended: https://tmsearch.uspto.gov/search/search-results (search 'Echoveer'). Not fetched. | **hayır** | Not accessed. Network allowlist block. |
| TÜRKPATENT | Intended: https://www.turkpatent.gov.tr/arastirma-yap (search 'Echoveer'). Not fetched. | **hayır** | Not accessed. Network allowlist block. |

### Phantrail — çakışma riski: unknown

- Aynı adlı oyun bulundu mu: hayır (erişilen kaynaklarda); benzer oyun: hayır
- Marka kaydı: Not checked: no trademark database could be reached. Unknown.
- Kültürel/dilsel: From linguistic knowledge only: phantom + trail fits the shadow-replay mechanic. There is no negative meaning in en/tr/de/it/fr/es. Pronunciation risk: Turkish has no 'ph' digraph, so readers may say 'p-han-trail'. Spanish and Italian spell the sound with 'f', so the spelling looks foreign but readable. Spoken aloud it sounds like 'Fantrail', so people may misspell it when searching. 'Phan' is a common Vietnamese surname and a fandom slang term; both are harmless.
- Not: NO SEARCH WAS COMPLETED; the false booleans mean 'not determined'. Variants to check later: 'Phantom Trail(s)', 'Fantrail', 'Phantrails'. 'Phantom' is heavily used in games. At 9 characters the name fits the Play limit.

| Kaynak | Yöntem | Erişildi | Bulgular |
|---|---|---|---|
| Google Play | planned WebFetch https://play.google.com/store/search?q=Phantrail&c=apps and '"Phantrail" site:play.google.com' | **hayır** | Not run: play.google.com fails with 'getaddrinfo ENOTFOUND play.google.com'; WebSearch budget exhausted. |
| Apple App Store | WebFetch https://itunes.apple.com/search?term=Phantrail&entity=software&limit=25 ; Bash curl of same URL | **hayır** | WebFetch error: 'getaddrinfo ENOTFOUND itunes.apple.com'. curl returned HTTP 000 with empty body. No results seen. |
| Steam | Bash curl https://store.steampowered.com/api/storesearch/?term=Phantrail&cc=US&l=en | **hayır** | '403 Forbidden (agent proxy: no rule or network setting allows this host)'. No results seen. |
| Google | WebSearch '"Phantrail"' (mode standard) | **hayır** | 'Web search was not performed: this turn's web search budget is used up (limit: 200 WebSearch calls per turn, shared by every agent in it)'. No results seen. |
| Bing | planned '"Phantrail" game' | **hayır** | Not run: WebSearch budget exhausted. |
| YouTube | planned '"Phantrail" site:youtube.com' | **hayır** | Not run: WebSearch budget exhausted. |
| Instagram | planned '"Phantrail" site:instagram.com' | **hayır** | Not run: WebSearch budget exhausted. |
| TikTok | planned '"Phantrail" site:tiktok.com' | **hayır** | Not run: WebSearch budget exhausted. |
| WIPO GBD | planned branddb.wipo.int for PHANTRAIL / PHANTOM TRAIL | **hayır** | Not attempted: session network blocks external hosts; no WebSearch budget. |
| EUIPO | planned eSearch for PHANTRAIL | **hayır** | Not attempted: same network block; no WebSearch budget. |
| USPTO | planned tmsearch.uspto.gov for PHANTRAIL | **hayır** | Not attempted: same network block; no WebSearch budget. |
| TURKPATENT | planned turkpatent.gov.tr for PHANTRAIL | **hayır** | Not attempted: same network block; no WebSearch budget. |

### Neon Mimic — çakışma riski: low

- Aynı adlı oyun bulundu mu: hayır (erişilen kaynaklarda); benzer oyun: evet
- Marka kaydı: No exact NEON MIMIC mark found in US aggregator results (indirect only). Separate MIMIC marks exist: US 79408135 (mimic robotics AG, classes 7/9/42, pending), 88419298 (Atomic, ski boots) and 87057528 (abandoned). WIPO, EUIPO and TURKPATENT were not checked.
- Kültürel/dilsel: en: 'mimic' means to imitate, which fits the shadow replaying your moves. It is also a well-known game monster type (D&D chest mimic, Neon White 'Mimic' enemy, FNAF: Secret of the Mimic). tr: reads as 'Neon Mimik', and 'mimik' means facial expression; neutral, easy to say. de: 'Mimik' means facial expression; neutral. it/fr/es: 'mimica'/'mimique'/'mímica' relate to mime or gesture; neutral. No negative or offensive meanings found.
- Not: 10 characters, fits the 30-character limit. Both words are generic and crowded in games ('Mimic' especially), so the name is weakly distinctive and hard to rank for in ASO. 'Neon Copy' (a jam game about enemy copies of yourself) is conceptually close but has a different name. Store APIs and trademark offices could not be queried directly (network allowlist), so 'low' is provisional.

| Kaynak | Yöntem | Erişildi | Bulgular |
|---|---|---|---|
| Google Play | WebSearch "Neon Mimic" site:play.google.com (standard). Direct fetch of https://play.google.com/store/search?q=Neon%20Mimic&c=apps was NOT attempted. | evet | No identical match in the results, which were 13 developer pages and neon-themed apps (e.g. https://play.google.com/store/apps/details?id=com.Neon_Run). The direct store fetch was blocked: after itunes.apple.com and store.steampowered.com both failed with 'getaddrinfo ENOTFOUND (outside the session's allowed network domains)', the tool said to stop trying more hosts. |
| Apple App Store | WebFetch https://itunes.apple.com/search?term=Neon%20Mimic&entity=software&limit=25 ; WebSearch "Neon Mimic" site:apps.apple.com | **hayır** | iTunes API: error 'getaddrinfo ENOTFOUND itunes.apple.com' (session network allowlist). The site: search worked and found no identical match, only Neon Effects (id1355026332), the Neon step sequencer (id6471043963), Neonizer and similar apps. Separately, 'Mimic: The Game' (Simon clone) is at https://apps.apple.com/cz/app/mimic-the-game/id975080790. |
| Steam | WebFetch https://store.steampowered.com/api/storesearch/?term=Neon%20Mimic&cc=US&l=en ; WebSearch "Neon Mimic" site:store.steampowered.com | **hayır** | API: error 'getaddrinfo ENOTFOUND store.steampowered.com'. The site: search found no identical title, only similar ones: Mimicry (app 4137450, prop-hunt), The Mimic (app 624990, puzzle platformer), Advent NEON (app 1528260), Mimic Alert. |
| Google (general web search via WebSearch tool) | "Neon Mimic" game (standard); "Neon Mimic" (extended) | evet | No game titled exactly 'Neon Mimic'. The phrase mostly turns up the 'Mimic' enemy, level and achievement in the game Neon White (https://neonwhite.fandom.com/wiki/Mimic, https://www.trueachievements.com/a437819/mimic-achievement) and a Minecraft skin pack 'Neon Mobs by Mimic'. A similar concept exists: 'Neon Copy' (https://rawg.io/games/neon-copy), a jam puzzle platformer about 'enemy copies of yo |
| Bing | Not run separately. The WebSearch tool does not let me pick an engine, and a direct bing.com fetch was not attempted because of the network block. | **hayır** | No Bing-specific results. Access blocked by the session network policy and tool limits. |
| YouTube | WebSearch "Neon Mimic" site:youtube.com | evet | No identical match in the results (no YouTube video or channel named Neon Mimic). The index appears to cover youtube.com poorly. |
| Instagram | WebSearch "Neon Mimic" site:instagram.com | evet | No identical match in the results; only unrelated neon design pages. |
| TikTok | WebSearch "Neon Mimic" site:tiktok.com | evet | No identical match. There is a general TikTok '#mimic' trend tied to FNAF: Secret of the Mimic (https://dailydot.com/secret-of-the-mimic-tiktok) and a 'Neon Shadow Effect' filter (https://knowyourmeme.com/memes/neon-shadow-effect). |
| USPTO | WebSearch "NEON MIMIC" trademark (third-party aggregators justia/trademarkelite/gleanmark). Direct tmsearch.uspto.gov fetch not attempted because of the network block. | **hayır** | Indirect only: no exact NEON MIMIC mark in the aggregator results. Separate MIMIC marks exist: serial 79408135 (mimic robotics AG, pending, classes 7/9/42, https://gleanmark.com/trademark/uspto-79408135), serial 88419298 (Atomic Austria, ski boots, registered) and serial 87057528 (abandoned). There are also many NEON marks, including ARM's NEON. |
| WIPO Global Brand Database | Direct branddb.wipo.int not attempted (network block). A site:branddb.wipo.int search was refused because the web search budget ran out. | **hayır** | Not checked: network allowlist block, then search budget exhausted. |
| EUIPO | Direct euipo.europa.eu eSearch not attempted (network block). The site: search was refused (search budget exhausted). | **hayır** | Not checked. |
| TURKPATENT | Direct turkpatent.gov.tr not attempted (network block). The site: search was refused (search budget exhausted). | **hayır** | Not checked. |

### Lumbra — çakışma riski: high

- Aynı adlı oyun bulundu mu: evet; benzer oyun: evet
- Marka kaydı: Possible US LUMBRA application, serial 99917047, by Lumbra, Inc. in Classes 9 and 42, pending as of 2026-11-06 per a Trademarkia-sourced summary. It could not be re-verified. Class 9 overlaps with game software. The company is well funded ($25M Series A, Nov 2026). EUIPO, WIPO and TÜRKPATENT could not be queried directly.
- Kültürel/dilsel: (1) Brazilian Portuguese slang 'lombra' (a near-homophone) means a marijuana high, or something boring. (2) In English, Spanish and Italian it recalls 'lumbar' (lower back), and an existing back-pain app called Lumbra reinforces that. (3) The Spanish 'lumbre' (fire) and 'alumbrar' (to light), and the Italian 'ombra' (shadow), give it a positive 'light + shadow' reading. No meaning in Turkish, German or French. It is very close to 'Umbra', a crowded name for games and also a brand.
- Not: Not recommended. LUMBRA is a live Google Play game (app.morphstudios.lumbra) and an itch.io rhythm platformer with a glowing-silhouette look. It has been updated actively through Nov 2026, so the store name collides directly in the same category and aesthetic. Separately, 'Lumbra' is used by a back-pain app on iOS and Play, and by a well-funded US AI company that may have a pending Class 9/42 USPTO application. The Portuguese slang near-homophone is an added minor risk.

| Kaynak | Yöntem | Erişildi | Bulgular |
|---|---|---|---|
| Google Play (search index) | WebSearch "Lumbra" site:play.google.com ; "Lumbra" app OR brand OR company (extended) ; "LUMBRA" rhythm platformer android "Google Play" (extended) | evet | Found an identical-name GAME on Google Play. 'LUMBRA - Rhythm Platformer' (https://play.google.com/store/apps/details?id=app.morphstudios.lumbra), developer listed as MauricePutinas, is a one-touch rhythm platformer with 26 worlds, a DASH mode and a level editor. It shows 1K+ downloads and contains ads, and it launched on Play in Nov 2026. A non-game app also uses the name: 'Lumbra' back-pain work |
| Google Play (direct fetch) | https://play.google.com/store/search?q=Lumbra&c=apps | **hayır** | Not fetched because of the session network policy (WebFetch 'getaddrinfo ENOTFOUND <host>'; curl proxy 403). |
| Apple App Store | WebFetch https://itunes.apple.com/search?term=Lumbra&entity=software&limit=25 ; WebSearch "Lumbra" site:apps.apple.com | **hayır** | The API fetch failed with 'getaddrinfo ENOTFOUND itunes.apple.com'. The site: search found an identical non-game app: 'Lumbra' back-pain app (id6753948306, Emma Media d.o.o., Croatia, © 2025, https://apps.apple.com/ca/app/lumbra/id6753948306). No Lumbra game was found on iOS. Near names: Lumra, Lume, LUMINS. |
| Steam | WebFetch https://store.steampowered.com/api/storesearch/?term=Lumbra&cc=US&l=en ; WebSearch "Lumbra" site:store.steampowered.com | **hayır** | The API fetch failed with 'getaddrinfo ENOTFOUND store.steampowered.com'. The site: search found no Lumbra game on Steam. The only near match was 'Lumara' (app 3537580, a glowing-anglerfish puzzle game, coming soon; demo 4064840). |
| General web search (WebSearch tool; engine not identified, used in place of Google) | "Lumbra" game ; "Lumbra" video game OR indie game OR itch.io (extended) ; "Lumbra" app OR brand OR company (extended) | evet | Found an identical-name game that is under active development. LUMBRA by Lumbra-Rhythm (https://lumbra-rhythm.itch.io/lumbra) is a free browser and Android rhythm platformer with black silhouettes and glowing hazards, which is visually close to our neon/shadow theme. It has had frequent updates: 2026-10-22, 2026-11-07 (Google Play release), 2026-11-19, and a 2.0 'DASH ENDLESS' release. It was feat |
| Bing | No Bing tool; direct fetch blocked | **hayır** | Not accessible (session network policy). |
| YouTube | WebSearch "Lumbra" site:youtube.com | evet | No identical match in results. Only 'Lumbre' (a Buenos Aires studio), Lumina and similar names came back. Not conclusive. |
| Instagram | WebSearch "lumbra" site:instagram.com | evet | No identical match in results. Near name: @alumbralab. Not conclusive. |
| TikTok | WebSearch "lumbra" site:tiktok.com | evet | No identical match in results. Not conclusive. |
| USPTO (via indexed aggregators) | Direct tmsearch.uspto.gov not fetched (network policy). WebSearch "LUMBRA" trademark (extended); "LUMBRA" "99917047"; trademarkia "LUMBRA" Lumbra, Inc. | **hayır** | Direct USPTO access is blocked. One search summary, citing Trademarkia (https://www.trademarkia.com/astrolabe-99917113 and its docket report), reported a pending LUMBRA application, serial 99917047, by Lumbra, Inc. (Arlington, VA). It listed Classes 009 and 042 with status 'RESPONSE AFTER NON-FINAL ACTION - ENTERED' on 2026-11-06, plus a sister mark ASTROLABE 99917113. Two follow-up searches could |
| WIPO Global Brand Database | https://branddb.wipo.int (not fetched: network policy); WebSearch "Lumbra" EUIPO OR "EU trade mark" OR WIPO OR "Madrid" | **hayır** | Direct access is blocked. No indexed WIPO record found. Unverified. |
| EUIPO eSearch | https://euipo.europa.eu/eSearch (not fetched: network policy) | **hayır** | Direct access is blocked. No indexed EUTM found. Unverified. Emma Media d.o.o. is an EU (Croatian) company and could hold EU rights; not checked. |
| TÜRKPATENT | https://www.turkpatent.gov.tr (not fetched: network policy) | **hayır** | Direct access is blocked. No indexed record found. Unverified. |

### Umbrisk — çakışma riski: unknown

- Aynı adlı oyun bulundu mu: hayır (erişilen kaynaklarda); benzer oyun: hayır
- Marka kaydı: Not checked. WIPO GBD, EUIPO/TMview, USPTO and TURKPATENT were all blocked by the session egress proxy (403). No trademark status can be stated.
- Kültürel/dilsel: Linguistic review from general knowledge only, not checked against the web. The name is Latin 'umbra' (shadow) plus 'risk'/'brisk', which fits the shadow mechanic. EN: people may read it as 'um-BRISK' or 'UMB-risk', so pronunciation is unclear. TR: 'risk' is also a Turkish word; no negative meaning found. DE: no problem known ('Umbra' is understood). IT: close to 'ombra' and the region 'Umbria'; harmless. FR: no problem known (close to 'ombre'). ES: close to 'umbral' (threshold) and 'umbrío' (shady); harmless. No profanity or offensive meaning known in any of the six languages.
- Not: No source could be reached. Direct fetches of every store, social and trademark host were refused by the egress proxy (HTTP 403 / ENOTFOUND), and the shared WebSearch budget was already used up when this agent started. The identicalGameFound and similarGameFound values are 'not determined', not 'none found'. The name must NOT be treated as free. To finish this check, a follow-up run needs either a fresh web search budget (send a follow-up message or raise CLAUDE_CODE_MAX_WEB_SEARCHES_PER_SESSION) or these hosts allowed in the environment network settings: play.google.com, itunes.apple.com, store.steampowered.com, branddb.wipo.int, euipo.europa.eu, tmsearch.uspto.gov, turkpatent.gov.tr. It is 7 characters, well under the 30-character Google Play limit.

| Kaynak | Yöntem | Erişildi | Bulgular |
|---|---|---|---|
| Google Play | curl https://play.google.com/store/search?q=Umbrisk&c=apps (Bash, through the session egress proxy); WebSearch '"Umbrisk"' (site: searches could not be run) | **hayır** | Access error: curl returned HTTP code 000. The egress proxy refused play.google.com (the same host gave 'curl: (56) CONNECT tunnel failed, response 403' on a second test). WebSearch was refused because the per-turn web search budget (200 calls, shared by all agents) was used up. No results seen. |
| Apple App Store | WebFetch https://itunes.apple.com/search?term=Umbrisk&entity=software&limit=25 and curl of the same URL | **hayır** | Access error: WebFetch 'getaddrinfo ENOTFOUND itunes.apple.com' (host outside the session's allowed network domains). curl: agent proxy connect_rejected HTTP 403 for itunes.apple.com:443. No results seen. |
| Steam | curl https://store.steampowered.com/api/storesearch/?term=Umbrisk&cc=US&l=en | **hayır** | Access error: agent proxy connect_rejected HTTP 403 for store.steampowered.com:443. No results seen. |
| Google | WebSearch query: "Umbrisk" (mode standard) | **hayır** | Search not performed: 'this turn's web search budget is used up (limit: 200 WebSearch calls per turn, shared by every agent in it)'. No results seen. |
| Bing | curl https://www.bing.com/search?q=%22Umbrisk%22 | **hayır** | Access error: curl returned HTTP code 000 (no response; the egress proxy refused the connection). No results seen. |
| YouTube | curl https://www.youtube.com/results?search_query=Umbrisk | **hayır** | Access error: curl returned HTTP code 000. youtube.com gave 'CONNECT tunnel failed, response 403' (proxy connect_rejected). site:youtube.com search not possible (search budget used up). |
| Instagram | curl https://www.instagram.com/ ; planned site:instagram.com "Umbrisk" | **hayır** | Access error: 'curl: (56) CONNECT tunnel failed, response 403' (proxy connect_rejected). Search budget used up. No results seen. |
| TikTok | curl https://www.tiktok.com/ ; planned site:tiktok.com "Umbrisk" | **hayır** | Access error: 'curl: (56) CONNECT tunnel failed, response 403' (proxy connect_rejected). Search budget used up. No results seen. |
| WIPO GBD | curl https://branddb.wipo.int/ and https://api.branddb.wipo.int/ | **hayır** | Access error: branddb.wipo.int gave HTTP code 000. api.branddb.wipo.int gave 'CONNECT tunnel failed, response 403'. Not searched. |
| EUIPO | curl https://euipo.europa.eu/eSearch/ and https://www.tmdn.org/tmview/ | **hayır** | Access error: euipo.europa.eu gave 'CONNECT tunnel failed, response 403'. tmdn.org gave HTTP code 000. Not searched. |
| USPTO | curl https://tmsearch.uspto.gov/ | **hayır** | Access error: 'CONNECT tunnel failed, response 403' (proxy connect_rejected). Not searched. |
| TURKPATENT | curl https://www.turkpatent.gov.tr/arastirma-yap and https://www.turkpatent.gov.tr/ | **hayır** | Access error: /arastirma-yap gave HTTP code 000. The root URL gave 'CONNECT tunnel failed, response 403'. Not searched. |

### Trailtwin — çakışma riski: unknown

- Aynı adlı oyun bulundu mu: hayır (erişilen kaynaklarda); benzer oyun: hayır
- Marka kaydı: Not checked: no trademark database could be reached in this session. Unknown.
- Kültürel/dilsel: From linguistic knowledge only, not web-verified: 'trail' + 'twin' has no vulgar or negative meaning in en/tr/de/it/fr/es. It fits the mechanic well (a twin that follows your trail). Possible problems: the 'tw' cluster and the English vowels are hard for Turkish, Italian and Spanish speakers (Turkish readers may say 'tra-il-tvin'). 'Trail' is strongly tied to hiking and outdoor apps (AllTrails, Trailforks), which could blur the category. Both parts are common English words, so similar compounds such as 'Twin Trail(s)' probably exist for outdoor businesses; this is unverified.
- Not: NO SEARCH WAS COMPLETED. identicalGameFound and similarGameFound are false only because nothing could be checked, not because a search came back empty. Do not treat this name as free. Blockers: (1) the WebSearch budget (200 calls per turn, shared by all agents) was already used up before this agent started; (2) itunes.apple.com, store.steampowered.com and play.google.com fail to resolve (they need to be allowed in the session network settings), and the trademark databases are presumably blocked too. Re-run in a follow-up turn or with a larger budget. At 9 characters the name leaves room under the 30-character Play title limit (e.g. 'Trailtwin: Shadow Dodge').

| Kaynak | Yöntem | Erişildi | Bulgular |
|---|---|---|---|
| Google Play | WebFetch https://play.google.com/store/search?q=Trailtwin&c=apps ; planned WebSearch '"Trailtwin" site:play.google.com' not run | **hayır** | WebFetch error: 'getaddrinfo ENOTFOUND play.google.com' (host outside the session's allowed network domains). Site search not run because WebSearch budget was exhausted. No results seen. |
| Apple App Store | WebFetch https://itunes.apple.com/search?term=Trailtwin&entity=software&limit=25 ; Bash curl of same URL | **hayır** | WebFetch error: 'getaddrinfo ENOTFOUND itunes.apple.com'. curl via agent proxy returned HTTP 000 with empty body. No results seen. |
| Steam | WebFetch https://store.steampowered.com/api/storesearch/?term=Trailtwin&cc=US&l=en ; Bash curl of same URL | **hayır** | WebFetch error: 'getaddrinfo ENOTFOUND store.steampowered.com'. curl: 'HTTP/1.1 403 Forbidden (agent proxy: no rule or network setting allows this host)'. No results seen. |
| Google | WebSearch '"Trailtwin"' (mode standard) | **hayır** | 'Web search was not performed: this turn's web search budget is used up (limit: 200 WebSearch calls per turn, shared by every agent in it)'. No results seen. |
| Bing | planned '"Trailtwin" game' | **hayır** | Not run: WebSearch budget exhausted (same error as above); fetching search engines directly is not allowed as a workaround. |
| YouTube | planned '"Trailtwin" site:youtube.com' | **hayır** | Not run: WebSearch budget exhausted. |
| Instagram | planned '"Trailtwin" site:instagram.com' | **hayır** | Not run: WebSearch budget exhausted. |
| TikTok | planned '"Trailtwin" site:tiktok.com' | **hayır** | Not run: WebSearch budget exhausted. |
| WIPO GBD | planned https://branddb.wipo.int search for TRAILTWIN | **hayır** | Not attempted: the session network blocked every external host tried (play.google.com, itunes.apple.com, store.steampowered.com: ENOTFOUND / proxy 403), and the tool said to stop trying more sites. No site: search possible because the WebSearch budget was exhausted. |
| EUIPO | planned euipo.europa.eu eSearch for TRAILTWIN | **hayır** | Not attempted: same network block as WIPO; no WebSearch budget. |
| USPTO | planned https://tmsearch.uspto.gov search for TRAILTWIN | **hayır** | Not attempted: same network block; no WebSearch budget. |
| TURKPATENT | planned turkpatent.gov.tr marka arastirma for TRAILTWIN | **hayır** | Not attempted: same network block; no WebSearch budget. |

### Glintail — çakışma riski: unknown

- Aynı adlı oyun bulundu mu: hayır (erişilen kaynaklarda); benzer oyun: hayır
- Marka kaydı: Not checked. All trademark databases were blocked by the egress proxy (403). No trademark status can be stated.
- Kültürel/dilsel: Linguistic review from general knowledge only, not checked against the web. The name is EN 'glint' plus 'tail' (a glowing trail), which fits the neon glow look but does not suggest the shadow mechanic. Spelling is ambiguous: users may type 'Glinttail' or 'Glint Tail'. FR: it can be split as 'Glint-ail', and 'ail' means garlic; harmless and a little comic. TR: no meaning; the 'gl-' cluster is fine; 'tail' is not understood by non-English speakers. DE, IT, ES: no problem known. It sounds like a creature or pet name, so it may collide with names in monster-collecting games (Pokemon-likes, Temtem, Palworld and similar); this could not be verified. No profanity known.
- Not: No source could be reached: the egress proxy blocked every host and the WebSearch budget was used up. The booleans mean 'not determined'. Do not treat the name as free. Main concerns, untested: possible use as a creature or character name in other games, and the double-'t' spelling ambiguity. It is 8 characters.

| Kaynak | Yöntem | Erişildi | Bulgular |
|---|---|---|---|
| Google Play | curl https://play.google.com/store/search?q=Glintail&c=apps ; WebSearch '"Glintail"' | **hayır** | Access error: 'curl: (56) CONNECT tunnel failed, response 403' (agent proxy connect_rejected). WebSearch refused because the per-turn budget (200, shared) was used up. No results seen. |
| Apple App Store | WebFetch https://itunes.apple.com/search?term=Glintail&entity=software&limit=25 and curl of the same URL | **hayır** | Access error: WebFetch 'getaddrinfo ENOTFOUND itunes.apple.com'. curl: proxy connect_rejected HTTP 403. No results seen. |
| Steam | curl https://store.steampowered.com/api/storesearch/?term=Glintail&cc=US&l=en | **hayır** | Access error: 'curl: (56) CONNECT tunnel failed, response 403'. No results seen. |
| Google | WebSearch query: "Glintail" (mode standard) | **hayır** | Search not performed: the web search budget for this turn is used up (limit 200, shared). No results seen. |
| Bing | Planned: https://www.bing.com/search?q=%22Glintail%22+game | **hayır** | Access error: bing.com is refused by the egress proxy (HTTP code 000). No results seen. |
| YouTube | Planned: site:youtube.com "Glintail" | **hayır** | Access error: youtube.com gave 'CONNECT tunnel failed, response 403'. Search budget used up. |
| Instagram | Planned: site:instagram.com "Glintail" | **hayır** | Access error: instagram.com gave 'CONNECT tunnel failed, response 403'. Search budget used up. |
| TikTok | Planned: site:tiktok.com "Glintail" | **hayır** | Access error: tiktok.com gave 'CONNECT tunnel failed, response 403'. Search budget used up. |
| WIPO GBD | https://api.branddb.wipo.int/ ; https://branddb.wipo.int/ | **hayır** | Access error: 'CONNECT tunnel failed, response 403' / HTTP code 000. Not searched. |
| EUIPO | https://euipo.europa.eu/eSearch/ | **hayır** | Access error: 'CONNECT tunnel failed, response 403'. Not searched. |
| USPTO | https://tmsearch.uspto.gov/ | **hayır** | Access error: 'CONNECT tunnel failed, response 403'. Not searched. |
| TURKPATENT | https://www.turkpatent.gov.tr/ | **hayır** | Access error: 'CONNECT tunnel failed, response 403'. Not searched. |

### Umbravoid — çakışma riski: unknown

- Aynı adlı oyun bulundu mu: hayır (erişilen kaynaklarda); benzer oyun: hayır
- Marka kaydı: Not checked: no trademark database could be reached. Background knowledge, unverified: 'UMBRA' is an established brand in several fields, including Umbra home goods and the Umbra occlusion-culling middleware once sold to game developers by a Finnish company. This needs a class 9/41 check for UMBRA-prefixed marks.
- Kültürel/dilsel: From linguistic knowledge only: 'umbra' is Latin for shadow and an astronomy term for the darkest part of a shadow. It reads well in Italian (ombra), Spanish (sombra) and French (ombre); in Italian 'umbra' is also the feminine form of 'umbro' (Umbrian), which is harmless. 'Void' fits the black-hole map. There is no negative meaning in en/tr/de/it/fr/es. Turkish speakers may read 'void' as 'vo-id'. The tone is darker, closer to horror, than the casual neon style, but acceptable for 13+.
- Not: NO SEARCH WAS COMPLETED; the false booleans mean 'not determined'. Expect a crowded field: 'Umbra' and 'Void' are each very common in game titles, so similar names are likely even if the exact compound is unused. Re-check once WebSearch budget and network access are available. At 9 characters the name fits the Play limit.

| Kaynak | Yöntem | Erişildi | Bulgular |
|---|---|---|---|
| Google Play | planned WebFetch https://play.google.com/store/search?q=Umbravoid&c=apps and '"Umbravoid" site:play.google.com' | **hayır** | Not run: play.google.com already failed with 'getaddrinfo ENOTFOUND play.google.com' (Trailtwin attempt) and the tool said not to retry; WebSearch budget exhausted. |
| Apple App Store | WebFetch https://itunes.apple.com/search?term=Umbravoid&entity=software&limit=25 ; Bash curl of same URL | **hayır** | WebFetch error: 'getaddrinfo ENOTFOUND itunes.apple.com'. curl returned HTTP 000 with empty body. No results seen. |
| Steam | Bash curl https://store.steampowered.com/api/storesearch/?term=Umbravoid&cc=US&l=en | **hayır** | Agent proxy blocked store.steampowered.com ('403 Forbidden (agent proxy: no rule or network setting allows this host)'); the WebFetch attempt for Trailtwin got ENOTFOUND. No results seen. |
| Google | WebSearch '"Umbravoid"' (mode standard) | **hayır** | 'Web search was not performed: this turn's web search budget is used up (limit: 200 WebSearch calls per turn, shared by every agent in it)'. No results seen. |
| Bing | planned '"Umbravoid" game' | **hayır** | Not run: WebSearch budget exhausted. |
| YouTube | planned '"Umbravoid" site:youtube.com' | **hayır** | Not run: WebSearch budget exhausted. |
| Instagram | planned '"Umbravoid" site:instagram.com' | **hayır** | Not run: WebSearch budget exhausted. |
| TikTok | planned '"Umbravoid" site:tiktok.com' | **hayır** | Not run: WebSearch budget exhausted. |
| WIPO GBD | planned branddb.wipo.int search for UMBRAVOID and UMBRA in classes 9/41 | **hayır** | Not attempted: the session network blocks external hosts (ENOTFOUND / proxy 403 on every host tried); no WebSearch budget. |
| EUIPO | planned eSearch for UMBRAVOID | **hayır** | Not attempted: same network block; no WebSearch budget. |
| USPTO | planned tmsearch.uspto.gov for UMBRAVOID | **hayır** | Not attempted: same network block; no WebSearch budget. |
| TURKPATENT | planned turkpatent.gov.tr for UMBRAVOID | **hayır** | Not attempted: same network block; no WebSearch budget. |

### Tracewisp — çakışma riski: unknown

- Aynı adlı oyun bulundu mu: hayır (erişilen kaynaklarda); benzer oyun: hayır
- Marka kaydı: Not checked: no trademark database could be reached. Unknown.
- Kültürel/dilsel: From linguistic knowledge only: trace + wisp (as in will-o'-the-wisp) suits a glowing character that leaves a trail. There is no negative meaning in en/tr/de/it/fr/es. 'Wisp' is an English-only word that most non-native players will not know. The initial 'w' and the final 'sp' are hard for Turkish, Spanish and Italian speakers, and Turkish readers may say 'tra-ce-visp'. Background knowledge, unverified: 'Wisp(s)' is a well-known game term (Sonic Colors' Wisps, Ori and the Will of the Wisps), so the second half shares an element with famous titles. The full compound is still distinct.
- Not: NO SEARCH WAS COMPLETED; the false booleans mean 'not determined'. At 9 characters the name fits the Play limit. To finish this research: start a new turn so the WebSearch budget resets, or raise CLAUDE_CODE_MAX_WEB_SEARCHES_PER_SESSION, and allow these hosts in the session network settings: play.google.com, itunes.apple.com, store.steampowered.com, branddb.wipo.int, euipo.europa.eu, tmsearch.uspto.gov, turkpatent.gov.tr.

| Kaynak | Yöntem | Erişildi | Bulgular |
|---|---|---|---|
| Google Play | planned WebFetch https://play.google.com/store/search?q=Tracewisp&c=apps and '"Tracewisp" site:play.google.com' | **hayır** | Not run: play.google.com fails with 'getaddrinfo ENOTFOUND play.google.com'; WebSearch budget exhausted. |
| Apple App Store | WebFetch https://itunes.apple.com/search?term=Tracewisp&entity=software&limit=25 ; Bash curl of same URL | **hayır** | WebFetch error: 'getaddrinfo ENOTFOUND itunes.apple.com'. curl returned HTTP 000 with empty body. No results seen. |
| Steam | Bash curl https://store.steampowered.com/api/storesearch/?term=Tracewisp&cc=US&l=en | **hayır** | '403 Forbidden (agent proxy: no rule or network setting allows this host)'. No results seen. |
| Google | WebSearch '"Tracewisp"' (mode standard) | **hayır** | 'Web search was not performed: this turn's web search budget is used up (limit: 200 WebSearch calls per turn, shared by every agent in it)'. No results seen. |
| Bing | planned '"Tracewisp" game' | **hayır** | Not run: WebSearch budget exhausted. |
| YouTube | planned '"Tracewisp" site:youtube.com' | **hayır** | Not run: WebSearch budget exhausted. |
| Instagram | planned '"Tracewisp" site:instagram.com' | **hayır** | Not run: WebSearch budget exhausted. |
| TikTok | planned '"Tracewisp" site:tiktok.com' | **hayır** | Not run: WebSearch budget exhausted. |
| WIPO GBD | planned branddb.wipo.int for TRACEWISP | **hayır** | Not attempted: session network blocks external hosts; no WebSearch budget. |
| EUIPO | planned eSearch for TRACEWISP | **hayır** | Not attempted: same network block; no WebSearch budget. |
| USPTO | planned tmsearch.uspto.gov for TRACEWISP | **hayır** | Not attempted: same network block; no WebSearch budget. |
| TURKPATENT | planned turkpatent.gov.tr for TRACEWISP | **hayır** | Not attempted: same network block; no WebSearch budget. |

### Echotrail — çakışma riski: high

- Aynı adlı oyun bulundu mu: evet; benzer oyun: evet
- Marka kaydı: US ECHOTRAIL serial 99633662 (Zhangshu Guanliantong Trading Co., Class 18 leather goods, pending, per markinton.com). No game or software class hit found in the indexed data. USPTO, EUIPO, WIPO and TÜRKPATENT could not be queried directly.
- Kültürel/dilsel: No negative meaning found in en, tr, de, it, fr or es. 'Echo' is understood everywhere (tr eko, de Echo, it/es eco, fr écho). Turkish speakers may spell it phonetically, which is a minor issue.
- Not: Not recommended. 'Echo Trail' (com.sjunx.echotrail) is already on Google Play as a one-hit arcade game where you dodge your own past trajectory while collecting stars, with meteors and black holes. That is the same core mechanic as our game. The name is phonetically identical and differs only by a space, so the risk of user confusion, ASO collision and a passing-off complaint is high. The itch.io games 'Echo Trail' (SoGHO) and 'Echo Trails' (RichPock) also use the echo-of-past-self idea.

| Kaynak | Yöntem | Erişildi | Bulgular |
|---|---|---|---|
| Google Play (search index) | WebSearch "Echotrail" site:play.google.com; "Echotrail" app OR software OR brand (extended); "Echo Trail" Google Play arcade "past movement" stars spaceship skins (extended) | evet | Found an existing GAME with the same name and almost the same mechanic. "Echo Trail" (com.sjunx.echotrail) at https://play.google.com/store/apps/details?id=com.sjunx.echotrail&hl=en_US is a one-hit space arcade game where your ship's past trajectory (the 'echo') is the hazard; touching it ends the run. You collect stars and coins, and later rounds add electric fences, meteors and black holes. It h |
| Google Play (direct fetch) | https://play.google.com/store/search?q=Echotrail&c=apps | **hayır** | Not fetched. The session network policy blocks direct fetches. WebFetch returned 'getaddrinfo ENOTFOUND itunes.apple.com' and then 'getaddrinfo ENOTFOUND store.steampowered.com', and the tool said to stop trying more hosts. Bash curl returned 'HTTP/1.1 403 Forbidden (agent proxy: no rule or network setting allows this host)'. |
| Apple App Store | WebFetch https://itunes.apple.com/search?term=Echotrail&entity=software&limit=25 ; WebSearch "Echotrail" site:apps.apple.com | **hayır** | The iTunes API fetch failed with 'getaddrinfo ENOTFOUND itunes.apple.com' (blocked by the session network allowlist; curl returned proxy 403). A site: search through the search index worked. It found no exact 'Echotrail' app. Near matches: 'ECHO Trails' (id1599963755, Christopher Brind, a heritage-trail companion app, https://apps.apple.com/gb/app/echo-trails/id1599963755) and TrailEcho (id6748895 |
| Steam | WebFetch https://store.steampowered.com/api/storesearch/?term=Echotrail&cc=US&l=en ; WebSearch "Echotrail" OR "Echo Trail" site:store.steampowered.com | **hayır** | The API fetch failed with 'getaddrinfo ENOTFOUND store.steampowered.com' (network policy). The site: search found no Echotrail or Echo Trail game on Steam. Only other Echo titles appeared, such as ECHO RAILWAY (app 3657150), ECHO (4566340) and Echo Stalker (3215800). |
| General web search (WebSearch tool; engine not identified, used in place of Google) | "Echotrail" game ; "Echotrail" OR "Echo Trail" itch.io OR indie game OR video game (extended) ; "Echo Trail" game mobile | evet | Several games share the name. 'Echo Trail' by SoGHO (https://sogho.itch.io/echo-trail) is a 2.5D puzzle-platformer where static echoes of your character appear every few seconds. 'Echo Trails' by RichPock (https://richpock.itch.io/echo-trails) is a minimalist 2D skill game against your own earlier runs, with a light-point character. serhaton/echo-trail (https://github.com/serhaton/echo-trail) is a |
| Bing | No Bing tool available; direct fetch of bing.com not possible | **hayır** | Not accessible. WebFetch to external hosts is blocked by the session network policy ('getaddrinfo ENOTFOUND <host>'). No separate Bing results. |
| YouTube | WebSearch "Echotrail" site:youtube.com | evet | No identical match in results. The site: filter returned non-YouTube pages, so this is not conclusive. A general search surfaced an 'Echo Trails' YouTube playlist for a solo indie action-adventure project (https://www.youtube.com/playlist?list=PLN9kdLNAUCB2PEC-OoclXdy-gwOHKakz8). |
| Instagram | WebSearch "echotrail" site:instagram.com | evet | No identical match in results. No instagram.com pages were returned, so the index does not seem to cover Instagram well. Not conclusive. |
| TikTok | WebSearch "echotrail" site:tiktok.com | evet | No identical match in results. Only EchoTik (a TikTok analytics tool) came back. Not conclusive. |
| USPTO (via indexed aggregators) | Direct tmsearch.uspto.gov not fetched (network policy). WebSearch "ECHOTRAIL" trademark (extended); "ECHO TRAIL" OR "ECHOTRAIL" trademark USPTO serial | **hayır** | Direct USPTO access is blocked. The aggregator https://markinton.com/trademark/echotrail-99633662 lists ECHOTRAIL, US serial 99633662, owned by Zhangshu Guanliantong Trading Co., Ltd. (Jiangxi, China). It was filed 2026-02-04 in Class 18 (leather goods) and is pending. No Class 9/28/41 ECHOTRAIL filing was found in indexed results. ECOTRAIL 77339611 (footwear) is abandoned. |
| WIPO Global Brand Database | https://branddb.wipo.int (not fetched: network policy); WebSearch "Echotrail" ... EUIPO/WIPO | **hayır** | Direct access is blocked by the session network policy. Indexed web results showed no WIPO record. |
| EUIPO eSearch | https://euipo.europa.eu/eSearch (not fetched: network policy); WebSearch "Echotrail" OR ... EUIPO trade mark | **hayır** | Direct access is blocked. Indexed web results showed no EUTM record. Unverified. |
| TÜRKPATENT | https://www.turkpatent.gov.tr (not fetched: network policy); WebSearch "Echotrail" ... marka TÜRKPATENT | **hayır** | Direct access is blocked, and the database requires an interactive or e-Devlet session. Indexed web results showed no record. Unverified. |

### Glowtrace — çakışma riski: medium

- Aynı adlı oyun bulundu mu: evet; benzer oyun: evet
- Marka kaydı: US GLOWTRACE serial 99102977, LKRB Industries LLC, Class 11 (decorative LED light strips), filed 2025-03-25 and reported registered 2025-11-11 (per Trademarkia, unverified). This is a different class from games (9/28/41). EUIPO, WIPO and TÜRKPATENT could not be queried directly.
- Kültürel/dilsel: No negative meaning found in en, tr, de, it, fr or es. 'Trace' is a native word in French. 'Glow' is less intuitive for Turkish, Italian and Spanish speakers to spell and pronounce. The name is descriptive and weakly distinctive, and searches for it are swamped by 'glow' beauty and skincare results, which hurts discoverability.
- Not: Two identical-name games were found, both small: 'GlowTrace' by adeeltech33, a Play game reported removed in July 2026 with 1K+ installs, and 'Glowtrace - Clear the Path', an itch.io puzzle by Can Games. Neither shares our mechanic. The identical US mark is registered for LED strips, an unrelated class. The risk is lower than for the other three names, but the name is not free, and the one-word spelling is already used for games. If chosen, a distinctive subtitle would help, and a professional search in Classes 9/28/41 (EUIPO, WIPO, TÜRKPATENT) is needed first.

| Kaynak | Yöntem | Erişildi | Bulgular |
|---|---|---|---|
| Google Play (search index) | WebSearch "Glowtrace" site:play.google.com ; "Glowtrace" app OR software OR brand (extended) ; "Glowtrace" OR "Glow Trace" Google Play game 2026 (extended) | evet | Found an identical-name game. 'GlowTrace' by adeeltech33 (com.abcdglowtrace.mnpqrstreaction, https://www.appbrain.com/app/glowtrace/com.abcdglowtrace.mnpqrstreaction) is a casual game with 1,000+ installs where you trace light pulses across a 5x5 grid. AppBrain says it is no longer on Google Play, last seen 2026-07-30. No current Play listing named Glowtrace was found. Other names seen: GlowTrack  |
| Google Play (direct fetch) | https://play.google.com/store/search?q=Glowtrace&c=apps | **hayır** | Not fetched because of the session network policy (WebFetch 'getaddrinfo ENOTFOUND <host>'; curl proxy 403 'no rule or network setting allows this host'). |
| Apple App Store | WebFetch https://itunes.apple.com/search?term=Glowtrace&entity=software&limit=25 ; WebSearch "Glowtrace" site:apps.apple.com ; "Glow Trace" game app | **hayır** | The API fetch failed with 'getaddrinfo ENOTFOUND itunes.apple.com'. The site: search found no exact 'Glowtrace'. A similar game, 'Glow Trace Tune' (id6779635253), is a puzzle game about rebuilding curves with Fourier-style spinning circles, released 2026-06-16 per mwm.ai (https://mwm.ai/apps/glow-trace-tune/6779635253). Other near names: Glow Draw!, Trace, Glow Course. |
| Steam | WebFetch https://store.steampowered.com/api/storesearch/?term=Glowtrace&cc=US&l=en ; WebSearch "Glowtrace" OR "Glow Trace" site:store.steampowered.com | **hayır** | The API fetch failed with 'getaddrinfo ENOTFOUND store.steampowered.com'. The site: search found no Glowtrace game on Steam. Nearby names: Glow (491830), Glowkeeper (3410660), Glow Journey (3608390). |
| General web search (WebSearch tool; engine not identified, used in place of Google) | "Glowtrace" game ; "Glowtrace" app OR software OR brand (extended) ; "Glowtrace" itch.io game "Can Games" (extended) | evet | Found an identical-name game. 'Glowtrace - Clear the Path' by Can Games (https://can-games.itch.io/glowtrace-clear-the-path) is an HTML5 one-line puzzle with RPG progression, 200+ levels and 9 regions; it has one rating of 1.0/5 and is AI-assisted. A non-game tool also uses the name: GitHub RafaelZamberlan/glowtrace (https://github.com/RafaelZamberlan/glowtrace), an API flow-tracing dev tool. A 'G |
| Bing | No Bing tool; direct fetch blocked | **hayır** | Not accessible (session network policy). |
| YouTube | WebSearch "Glowtrace" site:youtube.com | evet | No identical match in results. Only 'glow tracer' effect tutorials and plugins came back. Not conclusive. |
| Instagram | WebSearch "glowtrace" site:instagram.com | evet | No identical match in results. Results were dominated by 'glow' beauty brands (Glow Recipe), which also shows how noisy the term is to search. |
| TikTok | WebSearch "glowtrace" site:tiktok.com | evet | No identical match in results. Only glow-up and beauty content came back. |
| USPTO (via indexed aggregators) | Direct tmsearch.uspto.gov not fetched (network policy). WebSearch "GLOWTRACE" trademark (extended) | **hayır** | Direct USPTO access is blocked. Indexed records show GLOWTRACE, US serial 99102977, owned by LKRB Industries LLC (https://trademarks.justia.com/991/02/glowtrace-99102977.html). It is a standard-character mark filed 2025-03-25 in Class 11 for decorative LED light strips. Trademarkia (https://www.trademarkia.com/owners/lkrb-industries-llc) reports it registered on 2025-11-11, while Justia shows an o |
| WIPO Global Brand Database | https://branddb.wipo.int (not fetched: network policy) | **hayır** | Direct access is blocked. No indexed WIPO record found. Unverified. |
| EUIPO eSearch | https://euipo.europa.eu/eSearch (not fetched: network policy) | **hayır** | Direct access is blocked. No indexed EUTM found. Unverified. |
| TÜRKPATENT | https://www.turkpatent.gov.tr (not fetched: network policy) | **hayır** | Direct access is blocked. No indexed record found. Unverified. |

### Glowstep — çakışma riski: medium

- Aynı adlı oyun bulundu mu: evet; benzer oyun: evet
- Marka kaydı: US: GLOWSTEP REVOLUTION reg 6022059 (Torklift, vehicle steps, class 12) and GLOWSTEP reg 8256761 (Mei Mengna, class 10 medical), both from indirect aggregator results. No mark in classes 9/28/41 surfaced. The EU/CZ status of the Prague 'GlowStep' game-floor brand is unknown. WIPO, EUIPO and TURKPATENT were not checked directly.
- Kültürel/dilsel: en: 'glow' + 'step' is clear, but it suggests stepping, walking or dance (and 'glowstep' sounds like a dubstep or EDM subgenre) rather than a shadow-chase mechanic. tr: no meaning; 'glow' is not a familiar word for many Turkish users. de/it/fr/es: no negative meanings; 'glow' is a known English loanword in German. No offensive readings found.
- Not: 8 characters. identicalGameFound is set true conservatively: the Prague company GlowStep sells interactive LED-floor 'pixel games' under the identical name. That is a physical venue, not a mobile app. Registered US GLOWSTEP marks exist in unrelated classes. No mobile or PC game named Glowstep was found, but EU and Turkish registers were not verified. The name also fits the shadow-replay mechanic only loosely.

| Kaynak | Yöntem | Erişildi | Bulgular |
|---|---|---|---|
| Google Play | WebSearch "Glowstep" site:play.google.com. Direct store fetch not attempted (network block). | evet | No identical match in the results; nearest was 'GlowBeat: LED Speaker Lights' (https://play.google.com/store/apps/details?id=com.ledspeaker) plus developer pages such as 'lumstep'. |
| Apple App Store | WebFetch https://itunes.apple.com/search?term=Glowstep&entity=software&limit=25 ; WebSearch "Glowstep" OR "Glow Step" site:apps.apple.com | **hayır** | iTunes API: error 'getaddrinfo ENOTFOUND itunes.apple.com'. The site: search found no identical match (GlowHealth, GlowStrong, Stepio, Glow Space). |
| Steam | WebFetch https://store.steampowered.com/api/storesearch/?term=Glowstep&cc=US&l=en ; WebSearch "Glowstep" site:store.steampowered.com | **hayır** | API: error 'getaddrinfo ENOTFOUND store.steampowered.com'. The site: search found no identical title; similar ones are Glowkeeper (app 3410660, luminescent puzzle game) and Glowstick (app 2508360, delisted tool). Also 'Will Glow the Wisp' (app 640890). |
| Google (general web search via WebSearch tool) | "Glowstep" game (standard); "Glowstep" (extended); glowstep.cz interaktivní podlaha GlowStep s.r.o. | evet | No video game or app named Glowstep. Two existing brands use the name: (1) Torklift GlowStep(TM) RV and camper entry steps (https://torkliftcentral.com/rving-and-towing/truck-campers/truck-camper/glowstep-step-system, https://instructions.torklift.com/rv/glowstep); (2) GlowStep, Prague, Czech Republic: an interactive LED-floor 'pixel games' venue for 2-6 players (https://glowstep.cz/, https://www. |
| Bing | Not run separately (no engine choice in WebSearch; bing.com fetch not attempted because of the network block). | **hayır** | No Bing-specific results. |
| YouTube | WebSearch "Glowstep" site:youtube.com | evet | No identical match in the results. |
| Instagram | WebSearch "Glowstep" site:instagram.com | evet | No identical match in the results. |
| TikTok | WebSearch "Glowstep" site:tiktok.com | evet | No identical match on tiktok.com. One low-quality page (tools.highaltitudescience.com) calls 'Glowstep' a trend, but nothing confirms it. |
| USPTO | WebSearch "Torklift GlowStep trademark registration"; "GLOWSTEP trademark class 9 OR class 28 OR class 41 game" (aggregators justia/markinton/trademarkdir). Direct tmsearch.uspto.g | **hayır** | Indirect hits: GLOWSTEP REVOLUTION, serial 88382260 / reg 6022059, Tork Lift Central Welding of Kent, registered 2020-03-31, vehicle steps; Section 8 due 2026-03-31, status not verified (https://trademarks.justia.com/883/82/glowstep-88382260.html). GLOWSTEP, serial 98820613 / reg 8256761, Mei Mengna (Chongqing), class 10 medical (https://markinton.com/trademark/glowstep-98820613). No GLOWSTEP mark |
| EUIPO | WebSearch GLOWSTEP EUIPO trade mark OR "EUTM" OR "Unionsmarke" glowstep. Direct eSearch not attempted (network block). | **hayır** | Indirect only: no GLOWSTEP EUTM in the web results (only EUIPO guideline pages). The EUIPO database itself was not queried, and the Czech GlowStep business could hold an EU or CZ mark. |
| WIPO Global Brand Database | Not attempted (network block); site: search refused (search budget exhausted). | **hayır** | Not checked. |
| TURKPATENT | Not attempted (network block); site: search refused (search budget exhausted). | **hayır** | Not checked. |

### Shadowloop — çakışma riski: high

- Aynı adlı oyun bulundu mu: evet; benzer oyun: evet
- Marka kaydı: SHADOW LOOP, LISTAN GmbH (be quiet!): US serial 79385892, Class 9, reported registered (reg. 7602500), a Madrid designation, so a WIPO international registration exists; Canada 2300632, registration published 2025-04-02. Class 9 also covers downloadable game software. EUIPO, WIPO and TÜRKPATENT could not be queried directly.
- Kültürel/dilsel: No negative meaning found in en, tr, de, it, fr or es. It is a generic English compound with weak distinctiveness, and 'shadow loop' already reads as a common jam-game phrase (many itch.io time-loop entries). In Turkish it is pronounceable and has no bad connotations.
- Not: Not recommended. Several games already use Shadowloop or Shadow Loop, including the Android shooter by mFk Games (May 2026) and the SharmaJi2211 itch.io platformer with a shadow-retraces-your-path mechanic. A live iOS app 'ShadowLoop: Fluent English' and possibly a Play developer named 'ShadowLoop Games' (unconfirmed) also exist. On top of that, LISTAN GmbH holds a Class 9 SHADOW LOOP mark in the US and Canada, likely through a Madrid international registration.

| Kaynak | Yöntem | Erişildi | Bulgular |
|---|---|---|---|
| Google Play (search index) | WebSearch "Shadowloop" site:play.google.com ; "Shadow Loop" mFk Games android com.mfk.shadowloop ; "Shadowloop" Google Play apps details id (extended) | evet | Found a game with the same name. 'Shadow Loop' by mFk Games (com.mfk.shadowloop, per https://appgoblin.info/apps/com.mfk.shadowloop) was released in May 2026 and updated 2026-05-21. It is a triangle shooter, playable offline. The official Play page itself was not seen. One search result also showed 'ShadowLoop Games' as the developer of 'BlockRush: Neon Block Puzzle' (https://play.google.com/store |
| Google Play (direct fetch) | https://play.google.com/store/search?q=Shadowloop&c=apps | **hayır** | Not fetched because of the session network policy (WebFetch 'getaddrinfo ENOTFOUND' on itunes.apple.com and store.steampowered.com; curl 'HTTP/1.1 403 Forbidden (agent proxy: no rule or network setting allows this host)'). |
| Apple App Store | WebFetch https://itunes.apple.com/search?term=Shadowloop&entity=software&limit=25 ; WebSearch "Shadowloop" OR "Shadow Loop" site:apps.apple.com | **hayır** | The API fetch failed with 'getaddrinfo ENOTFOUND itunes.apple.com'. A site: search found an identical non-game app: 'ShadowLoop: Fluent English' (https://apps.apple.com/app/id6789892047), an English-speaking practice app with subscriptions, released 2026-07-29 per mwm.ai (site https://shadow-loop.app/). Similar game names: Shadow Echo (id6760043764), Shadow Trick, Shadow Sync. |
| Steam | WebFetch https://store.steampowered.com/api/storesearch/?term=Shadowloop&cc=US&l=en ; WebSearch "Shadowloop" OR "Shadow Loop" site:store.steampowered.com | **hayır** | The API fetch failed with 'getaddrinfo ENOTFOUND store.steampowered.com'. The site: search found no Shadowloop game on Steam. Nearby names: DEATHLOOP, Shadowbound (3950950), Shadown (4499100). |
| General web search (WebSearch tool; engine not identified, used in place of Google) | "Shadowloop" game ; "Shadowloop" app OR software OR brand OR studio (extended) ; ShadowLoop GreyzzX itch.io platformer | evet | Several games use the exact name. 'Shadowloop' by Digital Bros Game Academy (https://digital-bros-game-academy.itch.io/shadowloop) is a first-person puzzle horror game for Windows (Nov 2024). 'ShadowLoop' by GreyzzX (https://greyzzx.itch.io/shadowloop) is an HTML5 platformer. Games named 'Shadow Loop' on itch.io: oneDEV-Studio (https://onedev-studio.itch.io/shadow-loop); SharmaJi2211 (https://shar |
| Bing | No Bing tool; direct fetch blocked | **hayır** | Not accessible (session network policy blocks WebFetch to external hosts). |
| YouTube | WebSearch "Shadowloop" site:youtube.com | evet | No YouTube page was returned for the site: query. A trademark query surfaced a YouTube video of 'Lori Landay - ShadowLoop Interactive Installation' (https://www.youtube.com/watch?v=HzmHBdkVOOU). No game channel found. |
| Instagram | WebSearch "shadowloop" site:instagram.com | evet | No identical match in results. No instagram.com pages were returned, so this is not conclusive. A Facebook page 'ShadowLoop' (art installation, https://www.facebook.com/shadowloop/) exists. |
| TikTok | WebSearch "shadowloop" site:tiktok.com | evet | No identical match in results. Not conclusive. |
| USPTO (via indexed aggregators) | Direct tmsearch.uspto.gov not fetched (network policy). WebSearch "SHADOWLOOP" trademark (extended); "SHADOW LOOP" trademark USPTO OR EUIPO OR WIPO | **hayır** | Direct USPTO access is blocked. The aggregator https://trademarkdir.com/trademark/shadow-loop-79385892 lists SHADOW LOOP, US serial 79385892, owned by LISTAN GmbH (owner of the PC-hardware brand be quiet!). It was filed 2023-11-08 in Class 9 and is reported as registered (reg. 7602500); this was not verified on TSDR. Canada application 2300632 'Shadow Loop' (LISTAN GmbH) shows 'Registration publis |
| WIPO Global Brand Database | https://branddb.wipo.int (not fetched: network policy) | **hayır** | Direct access is blocked. A US serial starting with 79 is a Madrid Protocol designation, so a WIPO international registration for SHADOW LOOP (LISTAN GmbH) very likely exists. Its base mark (probably DE or EU) and the designated countries were not verified. |
| EUIPO eSearch | https://euipo.europa.eu/eSearch (not fetched: network policy) | **hayır** | Direct access is blocked. No EUTM appeared in indexed results, but an EU or DE base mark for the LISTAN Madrid filing is likely. Unverified. |
| TÜRKPATENT | https://www.turkpatent.gov.tr (not fetched: network policy); WebSearch ... marka TÜRKPATENT | **hayır** | Direct access is blocked. No indexed record found. Unverified. Türkiye may be among the designations of LISTAN's Madrid registration; this could not be checked. |

### Twinshade — çakışma riski: high

- Aynı adlı oyun bulundu mu: evet; benzer oyun: evet
- Marka kaydı: No live TWINSHADE mark found in US aggregator results (indirect only). Two TWIN SHADE applications (class 20 window shades, 77913889 and 77622546) are abandoned. WIPO, EUIPO and TURKPATENT were not checked.
- Kültürel/dilsel: en: 'shade' means shadow or ghost and fits the theme, but 'throwing shade' is slang for insulting someone. That is mild and not offensive. tr: no meaning; Turkish speakers may mispronounce it ('tvinşeyd'). de/it/fr/es: no negative meanings, but it is not intuitive to non-English speakers. No offensive readings found.
- Not: 9 characters. The exact name is already used by a shadow-themed mobile runner (GitHub metur100/Twinshade, 'physical self vs your shadow') and by a spell in the commercial game Witchfire. A near-identical one-touch mobile game, 'Twin Shades', is on the iOS App Store. That makes confusion and ASO collision likely even without a registered mark. Not recommended.

| Kaynak | Yöntem | Erişildi | Bulgular |
|---|---|---|---|
| Google Play | WebSearch "Twinshade" site:play.google.com. Direct store fetch not attempted (network block). | evet | No identical match among the Play results (developer pages such as Twinr, UTwin, Twinvel). The search surfaced an iOS near-match instead: 'Twin Shades' (https://apps.apple.com/la/app/twin-shades/id1443296852). |
| Apple App Store | WebFetch https://itunes.apple.com/search?term=Twinshade&entity=software&limit=25 ; WebSearch "Twinshade" OR "Twin Shade" site:apps.apple.com | **hayır** | iTunes API: error 'getaddrinfo ENOTFOUND itunes.apple.com'. The site: search found a near-identical game: 'Twin Shades' by Upendra Attarde, a one-touch game with light and dark modes, rated 4+ (https://apps.apple.com/la/app/twin-shades/id1443296852). No exact 'Twinshade'. |
| Steam | WebFetch https://store.steampowered.com/api/storesearch/?term=Twinshade&cc=US&l=en ; WebSearch "Twinshade" site:store.steampowered.com | **hayır** | API: error 'getaddrinfo ENOTFOUND store.steampowered.com'. No identical title in the site: results (Twins of the Sun 4018910, Twinland 1796500). Twinshade is, however, an in-game Light Spell in the Steam game Witchfire (The Astronauts): https://witchfire.wiki.gg/wiki/Twinshade |
| Google (general web search via WebSearch tool) | "Twinshade" game (standard); "Twinshade" (extended); Twinshade "Shadow Runner" mobile game shadow metur100 | evet | Identical-name game project: GitHub repo metur100/Twinshade, 'Shadow Runner - a two-world mobile runner: switch between your physical self and your shadow' (TypeScript/Phaser 3/Expo), https://github.com/metur100/Twinshade. It is shadow-themed and close to our concept; no store listing confirmed. Other uses: the Witchfire spell 'Twinshade'; a Jarlings ghost-toy character (https://www.jarlings.co.uk |
| GitHub (repo README) | mcp__github__get_file_contents owner=metur100 repo=Twinshade path=README.md | **hayır** | Error: 'Access denied: repository "metur100/twinshade" is not configured for this session.' The details come from the search snippet only. |
| Bing | Not run separately (no engine choice in WebSearch; bing.com fetch not attempted because of the network block). | **hayır** | No Bing-specific results. |
| YouTube | WebSearch "Twinshade" site:youtube.com | evet | No identical match in the results. |
| Instagram | WebSearch "Twinshade" site:instagram.com | evet | No identical match in the results. |
| TikTok | WebSearch "Twinshade" site:tiktok.com | evet | No identical match in the results. |
| USPTO | WebSearch "TWINSHADE trademark" (aggregators markinton/justia). Direct tmsearch.uspto.gov not attempted (network block). | **hayır** | Indirect only: no one-word TWINSHADE mark found. TWIN SHADE serial 77913889 (Outlook Window Fashion, class 20, abandoned 2010) and 77622546 (Roger Tipton, class 20, abandoned 2009): https://markinton.com/trademark/twin-shade-77913889. TWIST SHADE (reg 1759749) was cancelled. |
| WIPO Global Brand Database | Not attempted (network block); site: search refused (search budget exhausted). | **hayır** | Not checked. |
| EUIPO | Not attempted (network block); site: search refused (search budget exhausted). | **hayır** | Not checked. |
| TURKPATENT | Not attempted (network block); site: search refused (search budget exhausted). | **hayır** | Not checked. |

### Pastpulse — çakışma riski: unknown

- Aynı adlı oyun bulundu mu: hayır (erişilen kaynaklarda); benzer oyun: hayır
- Marka kaydı: Not checked. All trademark databases were blocked by the egress proxy (403). No trademark status can be stated.
- Kültürel/dilsel: Linguistic review from general knowledge only, not checked against the web. The name is EN 'past' plus 'pulse', which describes the replay-your-past mechanic, but it is fairly descriptive and generic. The 'stp' consonant cluster is hard to say for TR, ES and IT speakers ('Past-pulse' tends to become 'Pas-pulse'). TR: 'past' is not a Turkish word; people may associate it with 'pasta' (cake) or 'pas' (rust); not offensive. DE: 'Past' sounds like 'passt' ('fits'), 'Puls' means pulse; neutral. IT/ES: may evoke 'pasta'/'pasto'; mildly comic, not offensive. FR: no problem known. Users may type it with a space or hyphen ('Past Pulse', 'Past-Pulse'), which splits search traffic. 'Pulse' appears in many health and tech trademarks, so the 'pulse' part alone is crowded (not verified).
- Not: No source could be reached: the egress proxy blocked every host and the WebSearch budget was used up. The booleans mean 'not determined'. Do not treat the name as free. Of the four names, this one is the most descriptive (weaker for distinctiveness and trademark) and the hardest to pronounce for the Turkish, Spanish and Italian audiences. It is 9 characters.

| Kaynak | Yöntem | Erişildi | Bulgular |
|---|---|---|---|
| Google Play | Planned: WebSearch '"Pastpulse" site:play.google.com' and fetch https://play.google.com/store/search?q=Pastpulse&c=apps | **hayır** | Access error: play.google.com is refused by the egress proxy ('CONNECT tunnel failed, response 403'). WebSearch budget used up. No results seen. |
| Apple App Store | WebFetch https://itunes.apple.com/search?term=Pastpulse&entity=software&limit=25 and curl of the same URL | **hayır** | Access error: WebFetch 'getaddrinfo ENOTFOUND itunes.apple.com'. curl: proxy connect_rejected HTTP 403. No results seen. |
| Steam | curl https://store.steampowered.com/api/storesearch/?term=Pastpulse&cc=US&l=en | **hayır** | Access error: proxy connect_rejected HTTP 403. No results seen. |
| Google | WebSearch query: "Pastpulse" (mode standard) | **hayır** | Search not performed: the web search budget for this turn is used up (limit 200, shared). No results seen. |
| Bing | Planned: https://www.bing.com/search?q=%22Pastpulse%22+game | **hayır** | Access error: bing.com is refused by the egress proxy (HTTP code 000). No results seen. |
| YouTube | Planned: site:youtube.com "Pastpulse" | **hayır** | Access error: youtube.com gave 'CONNECT tunnel failed, response 403'. Search budget used up. |
| Instagram | Planned: site:instagram.com "Pastpulse" | **hayır** | Access error: instagram.com gave 'CONNECT tunnel failed, response 403'. Search budget used up. |
| TikTok | Planned: site:tiktok.com "Pastpulse" | **hayır** | Access error: tiktok.com gave 'CONNECT tunnel failed, response 403'. Search budget used up. |
| WIPO GBD | https://branddb.wipo.int/ | **hayır** | Access error: proxy refused (000 / 403). Not searched. |
| EUIPO | https://euipo.europa.eu/eSearch/ ; https://www.tmdn.org/tmview/ | **hayır** | Access error: proxy refused (403 / 000). Not searched. |
| USPTO | https://tmsearch.uspto.gov/ | **hayır** | Access error: 'CONNECT tunnel failed, response 403'. Not searched. |
| TURKPATENT | https://www.turkpatent.gov.tr/ | **hayır** | Access error: 'CONNECT tunnel failed, response 403'. Not searched. |

### Neon Rewind — çakışma riski: high

- Aynı adlı oyun bulundu mu: evet; benzer oyun: evet
- Marka kaydı: No exact NEON REWIND mark appeared in aggregator (Justia/TrademarkDir) results. The separate words NEON and REWIND are heavily registered by many owners, including a NEON mark held by Neon Games, LLC. Official databases were not accessed. Two plain English words make the name weak and hard to protect.
- Kültürel/dilsel: Plain English words that are widely understood. 'Neon' is the same in all six languages, and 'rewind' (tr. geri sar, de. zurückspulen, fr. rembobiner, it. riavvolgere, es. rebobinar) is familiar to young players. No offensive or negative meaning. Problems: it is generic and has low distinctiveness in a very crowded 'Neon ...' store space, and it slightly misdescribes the game. The mechanic is a delayed shadow replay, not the player rewinding time, while an existing itch.io 'Neon Rewind' does use an actual rewind mechanic.
- Not: At least two itch.io games already use this exact title. One (bekrylmaz.itch.io/neon-rewind) is a neon arena reflex game with a rewind mechanic that is also playable on mobile, which is a direct genre overlap. A FiveM server, a synthwave album, YouTube videos and neon-rewind.com also use it. Unless that itch.io page belongs to the same developer, avoid this name. It fits the 30-char limit (11 chars).

| Kaynak | Yöntem | Erişildi | Bulgular |
|---|---|---|---|
| Google Play (web search) | WebSearch standard: "Neon Rewind" site:play.google.com | evet | No identical match in results. The 'Neon' reflex/arcade space is crowded: developer pages show Neon Pulse (ATORK), Neon Bounce: Gravity Shift (FrmApp) and Neon Dodge: Arcade Reflex (K'z) (e.g. play.google.com/store/apps/developer?id=K%27z). |
| Google Play (direct fetch) | Not attempted for this name: https://play.google.com/store/search?q=Neon%20Rewind&c=apps | **hayır** | The same host already failed for Umbrix with 'getaddrinfo ENOTFOUND play.google.com' (outside allowed network domains). |
| Apple App Store (web search) | WebSearch standard: "Neon Rewind" site:apps.apple.com | evet | No identical match in results. Similar: Neon Descent, an arcade space-dodge game (https://apps.apple.com/us/app/-/id6756826649); Neon - Action Jump and Run (id6651816964); Neon Engine (id6758552019); Neon Effects and Neon Style photo apps. |
| Apple App Store (iTunes Search API) | WebFetch https://itunes.apple.com/search?term=Neon+Rewind&entity=software&limit=25 | **hayır** | Error: 'getaddrinfo ENOTFOUND itunes.apple.com' (outside the session's allowed network domains). |
| Steam (web search) | WebSearch standard: "Neon Rewind" site:store.steampowered.com | evet | No identical match in results. Similar: Neon Shift (app/2671600), Neon Noir (app/2184870), Neon Drive (app/433910), Neon Inferno (app/2957720). |
| Steam (store search API) | Not attempted for this name: https://store.steampowered.com/api/storesearch/?term=Neon%20Rewind&cc=US&l=en | **hayır** | The same host already failed with 'getaddrinfo ENOTFOUND store.steampowered.com'. curl also got 'CONNECT tunnel failed, response 403'. |
| Google (general web search) | WebSearch standard: "Neon Rewind" game ; WebSearch extended: "Neon Rewind" ; WebSearch: neon-rewind.com Neon Rewind ; WebSearch: "Neon Rewind" itch.io bekrylmaz rewind arena | evet | Two games with the identical name: (1) 'Neon Rewind' by Dhafo et al. / Rogapol Interactive / Humber Polytechnic, a 3D platformer prototype for Windows (https://dhafo.itch.io/neon-rewind); (2) 'Neon Rewind' by bekrylmaz (https://bekrylmaz.itch.io/neon-rewind), described in the search snippet as a fast neon arena game with a time-rewind mechanic, weekly leaderboard and daily challenges, playable in  |
| Bing | Not run separately (single WebSearch backend; direct fetch of bing.com not attempted because of the network allowlist block). | **hayır** | No Bing-specific results. |
| YouTube | WebSearch standard: "Neon Rewind" site:youtube.com ; plus the extended general search | evet | The site: query returned no YouTube hits. The extended general search found identically named videos: 'Neon Rewind Teaser' (https://www.youtube.com/watch?v=P8OQHr_hSAM) and 'Synthwave Highway 1985: Neon Rewind' (https://www.youtube.com/watch?v=uuZqsQpl05g). |
| Instagram | WebSearch standard: "Neon Rewind" site:instagram.com OR site:tiktok.com | evet | No identical match in results. |
| TikTok | Same combined WebSearch: "Neon Rewind" site:instagram.com OR site:tiktok.com | evet | No identical match in results. Only the unrelated 'Neon Shadow' effect and 'Retro Rewind' templates came up. |
| Trademark aggregators (web search) | WebSearch standard: "Neon Rewind" trademark | evet | No exact 'NEON REWIND' mark in the results. Many separate NEON marks exist, including Neon Games, LLC (trademark.justia.com/owners/neon-games-llc-2258813), and many REWIND marks: US serial 99572105, a new application (trademarks.justia.com/995/72/rewind-99572105.html), and Rewind Co.'s REWIND in Classes 38/42. |
| WIPO Global Brand Database | Intended: https://branddb.wipo.int/en/similarname (search 'Neon Rewind'). Not fetched. | **hayır** | Not accessed. Network allowlist block (WebFetch returned ENOTFOUND for hosts outside allowed domains, and the tool said to stop trying more sites). |
| EUIPO eSearch | Intended: https://euipo.europa.eu/eSearch/ (search 'Neon Rewind'). Not fetched. | **hayır** | Not accessed. Network allowlist block. |
| USPTO | Intended: https://tmsearch.uspto.gov/search/search-results (search 'Neon Rewind'). Not fetched. | **hayır** | Not accessed. Network allowlist block. |
| TÜRKPATENT | Intended: https://www.turkpatent.gov.tr/arastirma-yap (search 'Neon Rewind'). Not fetched. | **hayır** | Not accessed. Network allowlist block; the site also needs login or JavaScript. |

### Echo Escape — çakışma riski: high

- Aynı adlı oyun bulundu mu: evet; benzer oyun: evet
- Marka kaydı: Amazon Technologies, Inc. owns ECHO ESCAPE in Canada (app. 1889638, registration published 2021, Class 28 games plus 9/41/42/45). Amazon also has a broad, actively enforced ECHO family in the US (Classes 9/38/41/42). Seen through aggregators, not the official databases.
- Kültürel/dilsel: en: 'Echo' strongly evokes Amazon Echo/Alexa. 'Escape' signals the escape-room genre, which misrepresents a reflex arcade game and puts it against escape-room titles in search. es: 'escape' also means a car exhaust; eco/eco/écho cognates in es/it/fr are neutral. tr/de: no negative meaning; easy to pronounce. Fits the mechanic (a past-self echo) but is semantically generic.
- Not: Amazon used 'Echo Escape' publicly (NYCC 2017) and holds it as a Canadian trademark covering games, and it polices ECHO marks. Small identical-name games also exist (itch.io prototype, 'The Echo Escape' on RAWG/DekuDeals, 'Echo's Escape'), along with many 'Echo' escape or maze games. Not recommended. Official databases were not reachable.

| Kaynak | Yöntem | Erişildi | Bulgular |
|---|---|---|---|
| Google Play (direct fetch) | Not attempted: https://play.google.com/store/search?q=Echo%20Escape&c=apps (network allowlist). | **hayır** | Access blocked by the session network allowlist. |
| Google Play (web search) | WebSearch: "Echo Escape" site:play.google.com ; "Echo Escape" app iOS OR Android mobile game | evet | No Play title exactly 'Echo Escape'. Similar: 'Echo' by 341 Games (stealth maze: escape monsters that hunt by sound) https://play.google.com/store/apps/details?id=echo.games ; 'Echo: Time Loop Puzzles' https://play.google.com/store/apps/details?id=com.devrocks.echo (a time-loop concept close to this game's mechanic) ; Echoes - Interactive Thriller https://play.google.com/store/apps/details?id=com. |
| Apple App Store (iTunes Search API) | WebFetch https://itunes.apple.com/search?term=Echo%20Escape&entity=software&limit=50, plus curl via Bash | **hayır** | WebFetch: 'getaddrinfo ENOTFOUND itunes.apple.com'. curl: CONNECT tunnel failed, response 403. |
| Apple App Store (web search) | WebSearch: "Echo Escape" site:store.steampowered.com OR site:apps.apple.com ; "Echo Escape" app iOS OR Android mobile game | evet | No exact match. Similar: 'Echoes: Escape the Facility 3d' https://apps.apple.com/us/app/id6751839773 ; 'Echoes: Escape Room Puzzle 3D' https://apps.apple.com/us/app/-/id6751839773 ; Echo Maze - Roguelite https://apps.apple.com/us/app/id6759973204 ; Dark Echo https://apps.apple.com/gb/app/dark-echo/id951177560 |
| Steam (storesearch API) | curl via Bash https://store.steampowered.com/api/storesearch/?term=Echo%20Escape&cc=US&l=en | **hayır** | curl: HTTP 000 (proxy CONNECT refused). WebFetch: ENOTFOUND. |
| Steam (web search) | WebSearch: "Echo Escape" site:store.steampowered.com OR site:apps.apple.com ; "Echo Escape" game | evet | No Steam title exactly 'Echo Escape'. Similar: ECHO (precision puzzle-platformer where you place echoes and dash through lasers, planned for Oct 2026) https://store.steampowered.com/app/4881280 ; Echo Protocol: Escape Room (2025) https://rawg.io/games/1007148 ; Echoes https://store.steampowered.com/app/2978820. Steam app 3787520 appeared in the results, but its title could not be confirmed. |
| Google / Bing general web | WebSearch: "Echo Escape" game ; "The Echo Escape" Nintendo Switch OR Steam developer | evet | Identical name used by Amazon: 'Amazon Echo Escape', an Alexa-powered escape room at New York Comic-Con 2017 promoting Jack Ryan and Echo devices https://www.tomsguide.com/us/amazon-echo-escape-nycc,news-25949.html. Identical small games: 'Echo Escape (Prototype)' https://ricdev.itch.io/echo-escape ; 'The Echo Escape' https://rawg.io/games/the-echo-escape , https://www.dekudeals.com/items/the-echo |
| YouTube | WebSearch: "Echo Escape" site:youtube.com | evet | No youtube.com pages returned. The results were Amazon Echo Escape coverage and itch.io 'Echo's Escape'. |
| Instagram / TikTok | WebSearch: "Echo Escape" site:tiktok.com OR site:instagram.com | evet | No relevant TikTok or Instagram pages (only Snapchat/Lemon8 noise). Not conclusive. |
| USPTO | Direct tmsearch.uspto.gov not attempted (network allowlist). WebSearch: "ECHO ESCAPE" trademark ; "ECHO ESCAPE" trademark Amazon Technologies USPTO OR EUIPO (extended) | **hayır** | Direct database access blocked. No US 'ECHO ESCAPE' record surfaced. Amazon holds many US ECHO marks in Classes 9/38/41/42 (e.g., ECHO Serial 88125078, Reg. 7883376, Reg. 6746937) https://www.docketalarm.com/trademarks/88125078/ECHO/ and actively files TTAB oppositions based on ECHO (e.g., 2026 opposition https://ttabvue.uspto.gov/ttabvue/ttabvue-91301032-OPP-1.pdf). |
| CIPO Canada (supplementary) | WebSearch: "ECHO ESCAPE" trademark Canada 1889638 owner Amazon Technologies | evet | Identical trademark: ECHO ESCAPE, Canadian application 1889638, Amazon Technologies, Inc., filed 22 Mar 2018 with priority from Jamaica, registration published 22 Sep 2021. Covers Class 28 games (strategy games, treasure-hunt games) plus Classes 9/41/42/45: https://trademarkelite.com/canada/trademark/trademark-detail/1889638/ECHO-ESCAPE ; news coverage https://mobilesyrup.com/2018/04/30/amazon-fil |
| WIPO Global Brand Database | Direct branddb.wipo.int not attempted (network allowlist, JS app). | **hayır** | Database not reachable. The Jamaican priority filing suggests ECHO ESCAPE may also exist in other offices. Needs GBD check. |
| EUIPO eSearch | Direct euipo.europa.eu not attempted (network allowlist). WebSearch (extended) returned no EUIPO record. | **hayır** | Database not reachable. Amazon's ECHO family is very likely registered in the EU. Unverified. |
| TÜRKPATENT | Direct turkpatent.gov.tr not attempted (network allowlist). WebSearch combined query. | **hayır** | Database not reachable. No Turkish record surfaced. Unverified. |

### Ghostlap — çakışma riski: high

- Aynı adlı oyun bulundu mu: evet; benzer oyun: evet
- Marka kaydı: Unknown. No trademark source could be checked (search budget used up; official databases blocked by the network). GhostLap (ghostlap.ai) is an active, growing software company with industry partnerships (IMSA/Oracle, USF Pro) that may hold or be seeking a mark in software classes. Verify before any use.
- Kültürel/dilsel: 'Ghost' and 'lap' are English. 'Lap' means a circuit lap, so the name signals a racing game and misdescribes a one-finger dodge arcade. 'Ghost lap' is also an established racing-game term (ghost car or time-trial replay), so it is not distinctive. In Turkish 'lap' is a colloquial or onomatopoeic word ('lap diye' = suddenly/plop; 'lap lap' = eating noisily), which is mildly odd but not offensive. Nothing negative in de/it/fr/es (own linguistic assessment).
- Not: The general search alone shows strong conflicts. A game with the exact title 'Ghostlap' was just released on itch.io (a racer set over Istanbul, so it may also be visible to Turkish players). An active motorsport software company named GhostLap has a YouTube channel and press coverage in October 2026. There is also a GitHub ghost-lap racer. The store, social and trademark checks did not run (search budget used up, network block), but the conflicts already found are enough to rule this name out.

| Kaynak | Yöntem | Erişildi | Bulgular |
|---|---|---|---|
| Google (general web search) | WebSearch extended: "Ghostlap" | evet | Identical uses: (1) GhostLap, an active motorsport video and data analysis company (https://ghostlap.ai/; founder Brian Boly). In October 2026 IMSA Labs picked it as the first Oracle Cloud Innovation Studio startup (https://racer.com/2026/10/28/imsa-labs-announces-ghostlap-as-first-oracle-cloud-innovation-studio-startup, https://www.imsa.com/news/2026/10/28/...). It became the official driver deve |
| YouTube | Found through the WebSearch extended "Ghostlap" results (a separate site: query was not run) | evet | Identical: the YouTube channel 'GhostLap' (https://www.youtube.com/@ghostlapai), belonging to the motorsport analytics company. |
| Google Play (web search) | Planned: WebSearch "Ghostlap" site:play.google.com | **hayır** | Not performed. Error: 'Web search was not performed: this turn's web search budget is used up (limit: 200 WebSearch calls per turn, shared by every agent in it).' |
| Google Play (direct fetch) | Not attempted: https://play.google.com/store/search?q=Ghostlap&c=apps | **hayır** | The host already failed with 'getaddrinfo ENOTFOUND play.google.com' (outside allowed network domains). |
| Apple App Store (iTunes Search API) | WebFetch https://itunes.apple.com/search?term=Ghostlap&entity=software&limit=25 | **hayır** | Error: 'getaddrinfo ENOTFOUND itunes.apple.com' (outside the session's allowed network domains). |
| Apple App Store (web search) | Planned: WebSearch "Ghostlap" site:apps.apple.com | **hayır** | Not performed (WebSearch budget exhausted). |
| Steam | Planned: WebSearch "Ghostlap" site:store.steampowered.com and https://store.steampowered.com/api/storesearch/?term=Ghostlap&cc=US&l=en | **hayır** | Not performed. WebSearch budget exhausted, and the host was already failing with ENOTFOUND (WebFetch) and 'CONNECT tunnel failed, response 403' (curl). |
| Bing | Not run separately (single WebSearch backend). | **hayır** | No Bing-specific results. |
| Instagram | Planned: WebSearch "Ghostlap" site:instagram.com | **hayır** | Not performed (WebSearch budget exhausted). |
| TikTok | Planned: WebSearch "Ghostlap" site:tiktok.com | **hayır** | Not performed (WebSearch budget exhausted). |
| Trademark aggregators (web search) | Planned: WebSearch "Ghostlap" trademark | **hayır** | Not performed (WebSearch budget exhausted). |
| WIPO Global Brand Database | Intended: https://branddb.wipo.int/en/similarname (search 'Ghostlap'). Not fetched. | **hayır** | Not accessed. Network allowlist block (ENOTFOUND for hosts outside allowed domains). |
| EUIPO eSearch | Intended: https://euipo.europa.eu/eSearch/ (search 'Ghostlap'). Not fetched. | **hayır** | Not accessed. Network allowlist block. |
| USPTO | Intended: https://tmsearch.uspto.gov/search/search-results (search 'Ghostlap'). Not fetched. | **hayır** | Not accessed. Network allowlist block. |
| TÜRKPATENT | Intended: https://www.turkpatent.gov.tr/arastirma-yap (search 'Ghostlap'). Not fetched. | **hayır** | Not accessed. Network allowlist block. |

### Retrace — çakışma riski: high

- Aynı adlı oyun bulundu mu: evet; benzer oyun: evet
- Marka kaydı: US RETRACE Reg. 6154624 (Code Walker LLC, Class 9 software, live). UK 'Retrace' UK00004388486 (Rewind Software Ltd, Classes 9/38/41/42 covering video game footage and gameplay software, filed May 2026). US RETRACE Reg. 6903541 (Retrace Group, Classes 1-2, unrelated). Plus the established Stackify/Netreo 'Retrace' software brand. All found through aggregators or the UK journal, not the official databases.
- Kültürel/dilsel: en: 'retrace' means to go back over a path. That fits the replay mechanic but is a common dictionary word, which makes it a weak mark and hard to rank. fr 'retracer' (to retrace or recount) and it 'ritracciare' are neutral. es: not a word; possible faint association with 'retraso' (delay). de/tr: no meaning; Turkish speakers may mispronounce it ('re-tra-dje'). No offensive meanings found.
- Not: Crowded name. An identical-title commercial game is on Steam, Xbox, PlayStation and Switch (Spider Lily/Eastasiasoft). There is also Retrace the Light (Steam/Epic), RE-TRACE (iOS arcade/memory game with leaderboards), several itch.io 'Retrace' games, and a Google Play developer account named 'Retrace'. Live software trademarks exist in Class 9, and a 2026 UK filing covers game-related software. Not recommended. Direct store and trademark lookups were blocked by the session network allowlist, so they need manual confirmation.

| Kaynak | Yöntem | Erişildi | Bulgular |
|---|---|---|---|
| Google Play (direct fetch) | Not attempted: https://play.google.com/store/search?q=Retrace&c=apps. After itunes.apple.com and store.steampowered.com both failed with ENOTFOUND (blocked by the session network a | **hayır** | Access blocked: the session network allowlist stops WebFetch from reaching external store hosts (see the App Store and Steam errors). |
| Google Play (web search) | WebSearch: "Retrace" game site:play.google.com ; "Retrace" game Android mobile app | evet | A Play developer account named 'Retrace' exists, linked to Retrace.world: https://play.google.com/store/apps/developer?id=Retrace. Results showed no Play game page titled exactly 'Retrace'. Related Android items: Retrace (puzzle game for Win/Mac/Linux/Android, APK on itch.io) https://tekiteesy.itch.io/retrace ; Retrace AR (GPS tracing app) https://worldsapps.com/download-retrace-ar |
| Apple App Store (iTunes Search API) | WebFetch https://itunes.apple.com/search?term=Retrace&entity=software&limit=50, plus curl via Bash | **hayır** | WebFetch: 'getaddrinfo ENOTFOUND itunes.apple.com' (outside the session's allowed network domains). curl: '(56) CONNECT tunnel failed, response 403'. |
| Apple App Store (web search) | WebSearch: "Retrace" site:apps.apple.com game | evet | Near-identical game: RE-TRACE by Stewart Thomson, a memory/arcade game in the Games category since 2017 with Game Center leaderboards: https://apps.apple.com/us/app/re-trace/id1264752380. Similar: Cosmic Path: Comet Trail (neon memory, 'retrace' wording) https://apps.apple.com/app/id1355832531 |
| Steam (storesearch API) | WebFetch https://store.steampowered.com/api/storesearch/?term=Retrace&cc=US&l=en, plus curl via Bash | **hayır** | WebFetch: 'getaddrinfo ENOTFOUND store.steampowered.com'. curl: HTTP 000 (proxy CONNECT refused). |
| Steam (web search) | WebSearch: "Retrace" site:store.steampowered.com ; "Retrace" game Spider Lily Studios | evet | Identical game: Retrace (Spider Lily Studios, released 20 Jan 2020, horror puzzle-adventure) https://store.steampowered.com/app/1052640. Console port by Eastasiasoft as 'Retrace: Memories of Death' on Xbox, PlayStation and Switch (2021): https://www.microsoft.com/en-us/store/apps/9n2wnbntz796 , https://store.playstation.com/en-sa/concept/10001842. Similar: Retrace the Light (2.5D action-adventure, |
| Google / Bing general web | WebSearch (US engine; Bing not queried separately): "Retrace" game ; "Retrace" game Android mobile app | evet | Many identical game uses: rawg.io/games/retrace, Discord game listing https://discord.com/game/retrace-1442736822131359774, several itch.io games named Retrace (https://rykogames.itch.io/retrace , https://rayzorite.itch.io/retrace , https://deadpiegamer.itch.io/retrace , https://spiderlilystudios.itch.io/retrace). Non-game software: Stackify/Netreo 'Retrace' APM product https://www.capterra.com/p/ |
| YouTube | WebSearch: "Retrace" game site:youtube.com | evet | No youtube.com results came back directly. The Game Jolt page for Spider Lily's Retrace links a devlog video (youtube.com/watch?v=pQOb7RzQOfI). Several games share the name, so a YouTube search would be crowded. |
| Instagram / TikTok | WebSearch: "Retrace" game site:tiktok.com OR site:instagram.com | evet | The search returned no TikTok or Instagram pages; only the same game listings (backloggd, itch.io, Steam) appeared. Coverage of these platforms is not conclusive. |
| USPTO | Direct tmsearch.uspto.gov not attempted (network allowlist). WebSearch: "Retrace" video game trademark USPTO class 9 OR class 41 ; RETRACE 88515345 Code Walker LLC | **hayır** | Direct database access blocked. Aggregators show: RETRACE, US Reg. 6154624 (Serial 88515345), Code Walker LLC, LIVE, Class 9 downloadable programming software, https://markinton.com/trademark/retrace-88515345 ; RETRACE Reg. 6903541 (Serial 79333352), Retrace Group Ltd, Classes 1-2 (unrelated goods), https://markinton.com/trademark/retrace-79333352 ; a further RETRACE record, https://trademarks.jus |
| UK IPO (supplementary) | WebSearch result: UK Trade Mark Journal 2026/026 | evet | 'Retrace' UK00004388486, Rewind Software Limited, filed 17 May 2026, Classes 9/38/41/42. Covers software for capturing and sharing video game footage and gameplay, and online video game content: https://www.ipo.gov.uk/t-tmj/tm-journals/2026-026/UK00004388486.html. This sits directly in the games space. |
| WIPO Global Brand Database | Direct branddb.wipo.int not attempted (network allowlist, JS app). WebSearch: "RETRACE" trademark EUIPO OR "European Union trade mark" OR WIPO Madrid | **hayır** | Database not reachable. The web search showed the Madrid-origin US record 79333352 (Retrace Group, Classes 1-2) and no game-class international RETRACE record. Not verified in GBD. |
| EUIPO eSearch | Direct euipo.europa.eu not attempted (network allowlist, JS app). WebSearch as above. | **hayır** | Database not reachable. The web search found no EUTM 'RETRACE' record. Unverified. |
| TÜRKPATENT | Direct turkpatent.gov.tr not attempted (network allowlist; its search needs e-Devlet/JS). WebSearch: "Retrace" OR "Outpace" OR "Ghostrail" OR "Echo Escape" marka TÜRKPATENT | **hayır** | Database not reachable. The web search found no Turkish record. Unverified. |

### Outpace — çakışma riski: medium

- Aynı adlı oyun bulundu mu: hayır (erişilen kaynaklarda); benzer oyun: evet
- Marka kaydı: Several US OUTPACE marks in non-game fields: Omega Learning (Class 9/tutoring, live), AndWhat (Class 35, live), Ariat (live, likely footwear), Outpace Systems (software, dead), OUTPACE INC. (35/41/42, abandoned). Also Canada OUTPACE (clinical trials) and Australia OUTPACE (pending). No games-class OUTPACE mark was seen. Identical app names exist: OutPace: Fitness Challenges (Google Play, iOS) and Outpace SEO (iOS).
- Kültürel/dilsel: en: a plain dictionary verb ('to go faster than'). It fits 'outrun your shadow' but is generic, which gives weak distinctiveness and poor search ranking, and it carries fitness/business connotations. tr/de/fr/es/it: no meaning and no negative associations. Non-English speakers may stress or pronounce it unevenly ('out-pa-che' in Italian or Turkish readings).
- Not: No game named 'Outpace' was found in any searched source. However, an identically named live app (OutPace: Fitness Challenges, on Play and iOS) and an iOS developer named 'Outpace' exist, and Outrace/Outracer are close-sounding racing games. Trademark hits sit outside games, but the word is descriptive and widely used. Workable only with a distinctive subtitle (e.g., 'Outpace: Shadow Run'), and ASO will be hard. Official databases were not reachable.

| Kaynak | Yöntem | Erişildi | Bulgular |
|---|---|---|---|
| Google Play (direct fetch) | Not attempted: https://play.google.com/store/search?q=Outpace&c=apps (network allowlist; tool said to stop after two hosts failed). | **hayır** | Access blocked by the session network allowlist. |
| Google Play (web search) | WebSearch: "Outpace" game site:play.google.com ; "Outpace" app Android Google Play ; "Outpace" game play.google.com/store/apps/details (extended) | evet | Identical app name, but not a game: 'OutPace: Fitness Challenges' by OutPace Fit, Inc. (Health & Fitness) https://play.google.com/store/apps/details?id=fit.outpace.app. No Play game titled 'Outpace' found. Similar developer names: Outpox, Outryd, PaceX, PaceUp. |
| Apple App Store (iTunes Search API) | WebFetch https://itunes.apple.com/search?term=Outpace&entity=software&limit=50, plus curl via Bash | **hayır** | WebFetch: 'getaddrinfo ENOTFOUND itunes.apple.com'. curl: CONNECT tunnel failed, response 403. |
| Apple App Store (web search) | WebSearch: "Outpace" site:apps.apple.com | evet | Identical names, non-game: Outpace SEO (developer 'Outpace', Utilities) https://apps.apple.com/app/id1619377782 , developer page https://apps.apple.com/ca/developer/outpace/id1619377784 ; OutPace Fit (fitness challenges) id6759271326. Similar game: Outrace (ArmNomads, racing) https://apps.apple.com/us/app/outrace/id1346574263. The TurboStride endless runner uses 'outpace' only in its promo text. |
| Steam (storesearch API) | curl via Bash https://store.steampowered.com/api/storesearch/?term=Outpace&cc=US&l=en (WebFetch not retried after the ENOTFOUND on the Retrace query) | **hayır** | curl: HTTP 000 (proxy CONNECT refused). WebFetch to store.steampowered.com: ENOTFOUND. |
| Steam (web search) | WebSearch: "Outpace" site:store.steampowered.com | evet | No Steam title 'Outpace'. Similar racing titles: Outrace https://store.steampowered.com/app/1958890 , Outracer https://store.steampowered.com/app/669350 , OutSpaceX https://store.steampowered.com/app/1101650/OutSpaceX |
| Google / Bing general web | WebSearch: "Outpace" game ; "Outpace" mobile game arcade OR runner OR endless | evet | No video game titled 'Outpace' found. Similar: 'Outpaced' (2021 board/dice game) https://www.analog.games/ ; OutScape (itch.io) https://tutek1.itch.io/outscape. Non-game: alternativeto.net/software/outpace/ |
| YouTube | WebSearch: "Outpace" game site:youtube.com | evet | No YouTube results for an 'Outpace' game. |
| Instagram / TikTok | WebSearch: "outpace" site:tiktok.com OR site:instagram.com game | evet | No relevant TikTok or Instagram pages. The results were generic marketing articles. Not conclusive. |
| USPTO | Direct tmsearch.uspto.gov not attempted (network allowlist). WebSearch: "OUTPACE" trademark registration software OR games ; OUTPACE trademark 7241942 ; OUTPACE 5034856 | **hayır** | Direct database access blocked. Aggregators show several OUTPACE marks: Reg. 5034856 (Serial 86786306), Omega Learning Center Franchisor, live, Class 9/tutoring, https://trademarks.justia.com/867/86/outpace-86786306.html ; Reg. 7241942 (Serial 97668459), AndWhat, live, Class 35, https://markinton.com/trademark/outpace-97668459 ; Reg. 7114886, Ariat International, live (likely footwear) ; Reg. 4877 |
| WIPO Global Brand Database | Direct branddb.wipo.int not attempted (network allowlist, JS app). WebSearch: "OUTPACE" trademark EUIPO OR WIPO "class 9" OR "class 28" OR "class 41" | **hayır** | Database not reachable. The web search found no WIPO record for OUTPACE in Classes 9/28/41. Other offices: Canada OUTPACE 2446075 (clinical trial services) https://www.trademarkelite.com/canada/trademark/trademark-detail/2446075/OUTPACE ; Australia OUTPACE 2628942 under examination (goods not seen). |
| EUIPO eSearch | Direct euipo.europa.eu not attempted (network allowlist, JS app). WebSearch as above. | **hayır** | Database not reachable. No EUTM OUTPACE record surfaced in the web search. Unverified. |
| TÜRKPATENT | Direct turkpatent.gov.tr not attempted (network allowlist). WebSearch: combined marka TÜRKPATENT query | **hayır** | Database not reachable. No Turkish record surfaced. Unverified. |

## Son doğrulama turu (çürütme denemesi)

### Doppelglow — sonuç: risky

NOT CLEARED. No source could be checked, so I found no conflicts, but I also confirmed no clearance. This verdict comes from not knowing, not from evidence of a conflict. Nothing below should be read as "Doppelglow is free".

What happened:
1) WebSearch: no searches ran. All 3 attempts ("Doppelglow"; "Doppelglow" game; "Doppel Glow") returned: "Web search was not performed: this turn's web search budget is used up (limit: 200 WebSearch calls per turn, shared by every agent in it)." The tool said not to get around the limit through other search engines, so I didn't.
2) Fetching the stores directly failed with DNS errors. These hosts are not on this cloud session's network allowlist:
- Google Play (https://play.google.com/store/search?q=Doppelglow&c=apps): accessible=false, "getaddrinfo ENOTFOUND play.google.com"
- Apple App Store / iTunes Search API (https://itunes.apple.com/search?term=Doppelglow&entity=software): accessible=false, "getaddrinfo ENOTFOUND itunes.apple.com"
- Steam (https://store.steampowered.com/search/?term=Doppelglow): accessible=false, "getaddrinfo ENOTFOUND store.steampowered.com"
- itch.io (https://itch.io/search?q=Doppelglow): accessible=false, "getaddrinfo ENOTFOUND itch.io"
3) Not tried: Epic Games Store, YouTube, TikTok, company registers and domains, WIPO Global Brand Database, EUIPO eSearch, USPTO, TÜRKPATENT. The tool said that once several hosts fail this way I should stop trying more sites. These count as accessible=false (not attempted because of network restrictions).
4) The local repo /home/user/yorulmaz26 has no earlier research on the name (a case-insensitive grep for "doppel" found nothing).

Unverified background from my training data (cutoff mid-2026), not a search: I don't know of any game, app or trademark called "Doppelglow". Many game names already use "Doppel-" or "Doppelganger", and "glow" is very common in game names. So a near-match (e.g. "Doppel", "DoppelGlow" split into two words, or "Glow" titles in class 9/28/41) is plausible and still needs checking.

To finish: the user can send a follow-up to continue the searches once the budget resets, or raise CLAUDE_CODE_MAX_WEB_SEARCHES_PER_SESSION. They can also add these hosts to the session's network settings: play.google.com, itunes.apple.com, store.steampowered.com, itch.io, store.epicgames.com, www.youtube.com, branddb.wipo.int, euipo.europa.eu, tmsearch.uspto.gov, turkpatent.gov.tr. Then rerun the full check (exact, spaced, plural and "The" forms; Nice classes 9, 28, 41) before committing to the name.

### Ghostrail — sonuç: risky

The name could NOT be cleared: no source was actually checked in this run, so nothing here shows that 'Ghostrail' is free. Exact access log: (1) WebSearch, mode standard, for '"Ghostrail" game', '"Ghost Rail" game' and '"Ghostrail" app'. All three returned 'Web search was not performed: this turn's web search budget is used up (limit: 200 WebSearch calls per turn, shared by every agent in it)'. That is accessible=false, and no 'extended' search was possible either. (2) WebFetch https://store.steampowered.com/search/?term=ghostrail failed with 'getaddrinfo ENOTFOUND store.steampowered.com', so accessible=false. (3) WebFetch https://itch.io/search?q=ghostrail failed with 'getaddrinfo ENOTFOUND itch.io', so accessible=false. (4) WebFetch https://play.google.com/store/search?q=ghostrail&c=apps failed with 'getaddrinfo ENOTFOUND play.google.com', so accessible=false. The harness says this error means the host is outside the session's allowed network domains, and that after repeated failures no more sites should be tried. So these sources were NOT attempted: Apple App Store, Epic Games Store, YouTube, TikTok, company registries, WIPO Global Brand Database, EUIPO eSearch, USPTO, and TÜRKPATENT (Nice classes 9, 28 and 41). The spelling variants 'Ghost Rail', 'Ghost Rails', 'Ghostrails', 'The Ghostrail' and the close phrase 'Ghost Trail' were also not checked. The project repository has no earlier research on the name (a grep for 'ghost ?rail' found nothing). From model knowledge alone, with no verification, I know of no prominent game or brand called exactly 'Ghostrail'. 'Ghost Trail' is a common English phrase that may be used by small apps, and 'Ghostrunner' is a weak near-name. The name is 9 characters and fits the 30-character Google Play title limit, for example 'Ghostrail: Shadow Escape'. It also suits the mechanic (a ghost replaying your trail). The verdict is 'risky' only because clearance is missing, not because a conflict was found. To clear it, rerun with a new WebSearch budget (or CLAUDE_CODE_MAX_WEB_SEARCHES_PER_SESSION raised) and with these hosts allowed in the session's network settings: store.steampowered.com, itch.io, play.google.com, apps.apple.com, branddb.wipo.int, euipo.europa.eu, tmsearch.uspto.gov and turkpatent.gov.tr.

### Umbrix — sonuç: risky

NOT CLEARED: this verdict means the name is unverified, not that a conflict was found. No source could be checked. (1) WebSearch was refused for all three queries ('"Umbrix" game', '"Umbrix" app Google Play', '"Umbrix" trademark'). The error was: 'Web search was not performed: this turn's web search budget is used up (limit: 200 WebSearch calls per turn, shared by every agent in it).' (2) Fetching the stores directly also failed, all with DNS errors because the hosts are outside the session's allowed network domains. Google Play (https://play.google.com/store/search?q=Umbrix&c=apps) returned accessible=false, 'getaddrinfo ENOTFOUND play.google.com'. Steam (https://store.steampowered.com/search/?term=Umbrix) returned accessible=false, 'getaddrinfo ENOTFOUND store.steampowered.com'. itch.io (https://itch.io/search?q=Umbrix) returned accessible=false, 'getaddrinfo ENOTFOUND itch.io'. The Apple App Store via the iTunes Search API (https://itunes.apple.com/search?term=Umbrix&entity=software) returned accessible=false, 'getaddrinfo ENOTFOUND itunes.apple.com'. (3) I did not try Epic, YouTube, TikTok, company registries, WIPO Global Brand Database, EUIPO eSearch, USPTO or TÜRKPATENT. The tool said to stop trying more sites once several hosts had failed the same way, so all of these are accessible=false (not attempted, network-blocked session). (4) The local repo /home/user/yorulmaz26 has no earlier Umbrix research (grep found nothing). As a result, there is no evidence either way for exact or near spellings (Umbrix, Umbrixx, Umbryx, Umbriks, 'The Umbrix', 'Umbrix Games') in stores or trademark registers in classes 9, 28 and 41. The name also sits close to the very common 'Umbra' and 'Umbr-' shadow-themed roots used by many games, which raises similarity risk. To finish the check, the user can send a follow-up to continue searching once the budget resets, or raise CLAUDE_CODE_MAX_WEB_SEARCHES_PER_SESSION. They can also allow these hosts in the session's network settings: play.google.com, itunes.apple.com, store.steampowered.com, itch.io, branddb.wipo.int, euipo.europa.eu, tmsearch.uspto.gov, turkpatent.gov.tr. Do not adopt the name until those searches come back clean.

