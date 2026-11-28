# Third-Party Licenses / Üçüncü Taraf Lisansları

**App:** Gölgen Seni Yakalamasın / Doppelglow (provisional) — `com.golgen.oyun` — v1.1.0
**Developer:** Magic Lumos Software

## 1. Components shipped in the app

| Component | Usage | License / Terms |
|---|---|---|
| Google Mobile Ads SDK (`com.google.android.gms:play-services-ads`) | Banner, interstitial, rewarded ads | Proprietary — [Google APIs Terms of Service](https://developers.google.com/terms), [AdMob & AdSense Terms](https://admob.google.com/home/), [AdMob program policies](https://support.google.com/admob/answer/6128543) |
| Google User Messaging Platform (`com.google.android.ump:user-messaging-platform`) | EEA/UK consent form | Proprietary — Google terms (as above) |
| AndroidX libraries, incl. WorkManager (transitive, pulled in by the ads SDK) | Background tasks of the ads SDK (source of `WAKE_LOCK` / `FOREGROUND_SERVICE` permissions) | Apache License 2.0 — <https://www.apache.org/licenses/LICENSE-2.0> |
| Other transitive Google Play services / AndroidX dependencies of the ads SDK | Internal SDK use | Google terms / Apache License 2.0 (verify with the dependency tree: `./gradlew :app:dependencies`) |

> Apache 2.0 requires attribution notices to be retained. Consider adding an "Open source licenses" screen (e.g. via the `play-services-oss-licenses` plugin) or linking this list from the store listing/website.

## 2. Development / test only (NOT shipped)

| Component | Usage | License |
|---|---|---|
| JUnit 4 | Unit tests only; not included in the APK/AAB | Eclipse Public License 1.0 — <https://www.eclipse.org/legal/epl-v10.html> |

## 3. Fonts (marketing materials only)

| Asset | Usage | License |
|---|---|---|
| Roboto | **Only in marketing images and logos** (store graphics, logos). Not bundled in the game. | Google Fonts — historically Apache License 2.0; newer releases are under SIL Open Font License 1.1. **Verify the current license at <https://fonts.google.com/specimen/Roboto>** before final use. Both licenses allow commercial use in images. |

## 4. Original content

All in-game graphics, sound effects and the trailer music are **original works created by Magic Lumos Software and generated in code** (procedural shapes, synthesized audio). No third-party art, sound or music assets are used.

## 5. Trademarks

"Google", "AdMob", "Google Play" and "Android" are trademarks of Google LLC. The English brand name "Doppelglow" is **provisional** and subject to pending trademark clearance; do not register or market it as final until clearance is completed.

---
*Legal review of this package (privacy policy, licenses and trademark) before release is recommended.*
