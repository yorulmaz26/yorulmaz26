/**
 * Kuaförüm Cepte — Cloud Functions giriş noktası.
 * Aşama 1: ortak yapılandırma + domain çekirdeği (src/domain). Callable fonksiyonlar
 * docs/API_CONTRACTS.md sözleşmesine göre sonraki aşamalarda buradan export edilir.
 */
import { setGlobalOptions } from "firebase-functions/v2";

setGlobalOptions({ region: "europe-west1", maxInstances: 20 });

// Domain çekirdeği (src/domain) fonksiyonlar tarafından import edilir; buradan dışa açılmaz.
