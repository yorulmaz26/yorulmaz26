# İlk Derleme Kontrol Listesi (Android Studio)

Bu proje geliştirme ortamında Android SDK/Google Maven erişimi olmadan yazıldı. Saf Kotlin domain modülü,
Cloud Functions ve Firestore kuralları otomatik testlerle doğrulandı; **Android modülü ise GitHub Actions
CI'da (`.github/workflows/ci.yml`) derlenir**. CI kırmızıysa veya Android Studio'da hata görürseniz en olası
nedenler ve çözümleri:

| Belirti | Olası neden | Çözüm |
|---|---|---|
| Gradle Sync: "Could not find androidx.xxx:yyy:Z" | Katalog sürümü mevcut değil | `gradle/libs.versions.toml` içinde ilgili sürümü Android Studio'nun önerdiği kararlı sürüme güncelleyin. |
| `ExposedDropdownMenuAnchorType` bulunamadı | Material3 < 1.4 | Compose BOM'u güncelleyin veya `MenuAnchorType.PrimaryNotEditable` kullanın (`Inputs.kt`). |
| `LocalClipboard` / `ClipEntry` bulunamadı | Compose UI < 1.8 | BOM'u güncelleyin veya `LocalClipboardManager.setText(AnnotatedString(...))` kullanın. |
| `LocalActivity` bulunamadı | activity-compose < 1.10 | Sürümü yükseltin. |
| `LocationManagerCompat.getCurrentLocation` imza hatası | core-ktx sürüm farkı | `androidx.core.os.CancellationSignal` ↔ `android.os.CancellationSignal` arasında değiştirin (`LocationHelper.kt`). |
| KSP/Kotlin uyumsuzluğu | KSP sürümü Kotlin'e uymuyor | `ksp` sürümünü Kotlin sürümüyle uyumlu sürüme getirin. |
| `google-services` eklentisi hata veriyor | `app/google-services.json` geçersiz | Firebase Console'dan iki paket adını (release + `.debug`) içeren dosyayı indirin. |
| Uygulama açılışta "Firebase yapılandırması bulunamadı" | `google-services.json` yok | Beklenen davranış; dosyayı ekleyip yeniden derleyin. |
| Callable çağrılarında `APP_CHECK_FAILED` / UNAUTHENTICATED | App Check debug token'ı eklenmedi | Logcat'teki `DebugAppCheckProvider` token'ını Console → App Check → Debug token'larına ekleyin. |
| SMS gelmiyor | SHA parmak izleri eksik / bölge politikası | Debug SHA-1/256'yı ekleyin; test numaralarını kullanın. |
| "FAILED_PRECONDITION: The query requires an index" | İndeks dağıtılmadı | `firebase deploy --only firestore:indexes`; hata mesajındaki bağlantı da indeksi oluşturur. |
