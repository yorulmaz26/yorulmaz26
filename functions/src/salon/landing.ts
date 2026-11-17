import { onRequest } from "firebase-functions/v2/https";
import { db } from "../lib/admin";
import { isValidSalonCode } from "../domain";
import { SalonType } from "./defaults";

const TYPE_LABELS: Record<SalonType, string> = {
  BEAUTY_SALON: "Güzellik Salonu",
  MEN_HAIRDRESSER: "Erkek Kuaförü",
  WOMEN_HAIRDRESSER: "Kadın Kuaförü",
  UNISEX_HAIRDRESSER: "Erkek & Kadın Kuaförü",
};

/** Play Store sayfası yayınlandığında PLAY_STORE_URL ortam değişkeniyle verilir. */
const PLAY_URL = process.env.PLAY_STORE_URL ?? "https://play.google.com/store/apps/details?id=com.kuaforumcepte";
const ANDROID_PACKAGE = "com.kuaforumcepte";

export function escapeHtml(s: string): string {
  return s.replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]!);
}

export interface LandingSalon {
  code: string;
  name: string;
  type: SalonType;
  city: string;
  district: string;
  logoUrl: string;
  ratingAvg: number | null;
}

/** Uygulama yüklü değilse açılan açıklayıcı sayfa. Yalnızca kamuya açık salon alanlarını içerir. */
export function renderLanding(s: LandingSalon): string {
  const name = escapeHtml(s.name);
  const place = escapeHtml(`${s.district} / ${s.city}`);
  const rating = s.ratingAvg == null ? "Yeni" : `★ ${s.ratingAvg.toFixed(1).replace(".", ",")}`;
  const intent = `intent://salon/${s.code}#Intent;scheme=kuaforumcepte;package=${ANDROID_PACKAGE};S.browser_fallback_url=${encodeURIComponent(PLAY_URL)};end`;
  return `<!doctype html>
<html lang="tr"><head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>${name} — Kuaförüm Cepte</title>
<meta name="description" content="${name}, ${place}. Kuaförüm Cepte ile randevu alın.">
<meta property="og:title" content="${name}"><meta property="og:description" content="${place} · Kuaförüm Cepte ile randevu alın">
<meta property="og:image" content="${escapeHtml(s.logoUrl)}">
<style>
:root{--navy:#14243A;--gold:#C59A64;--bg:#F7F5F1;--ink:#1A1D23;--muted:#5E6672}
@media(prefers-color-scheme:dark){:root{--bg:#0B1626;--ink:#F7F5F1;--muted:#B9C2CF}}
*{box-sizing:border-box}body{margin:0;font-family:system-ui,-apple-system,sans-serif;background:var(--bg);color:var(--ink)}
main{max-width:440px;margin:0 auto;padding:32px 16px;text-align:center}
img{width:112px;height:112px;border-radius:24px;object-fit:cover;background:#ddd}
h1{font-family:Georgia,serif;margin:16px 0 4px}p{color:var(--muted);line-height:1.5;margin:4px 0}
.badge{display:inline-block;margin-top:8px;padding:4px 12px;border-radius:999px;background:var(--gold);color:var(--navy);font-weight:600}
a.btn{display:block;margin-top:16px;padding:16px;border-radius:16px;text-decoration:none;font-weight:600}
.primary{background:var(--navy);color:#fff}.secondary{border:1px solid var(--gold);color:var(--ink)}
@media(prefers-color-scheme:dark){.primary{background:var(--gold);color:var(--navy)}}
footer{margin-top:32px;font-size:13px;color:var(--muted)}
</style></head>
<body><main>
<img src="${escapeHtml(s.logoUrl)}" alt="${name} logosu">
<h1>${name}</h1>
<p>${escapeHtml(TYPE_LABELS[s.type] ?? "")} · ${place}</p>
<span class="badge">${rating}</span>
<p style="margin-top:24px">Bu salondan randevu almak için Kuaförüm Cepte uygulamasını kullanın. Uygulama yüklüyse salon sayfası doğrudan açılır.</p>
<a class="btn primary" href="${escapeHtml(intent)}">Uygulamada Aç</a>
<a class="btn secondary" href="${escapeHtml(PLAY_URL)}">Google Play'den İndir</a>
<p style="margin-top:16px">Salon kodu: <strong>${escapeHtml(s.code)}</strong></p>
<footer>Kuaförüm Cepte · Güzelliğe Bir Dokunuş, Randevuya Tek Tık.</footer>
</main></body></html>`;
}

function notFound(): string {
  return `<!doctype html><html lang="tr"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Salon bulunamadı</title></head>
<body style="font-family:system-ui;text-align:center;padding:48px 16px"><h1>Salon bulunamadı</h1><p>Bağlantı hatalı olabilir veya salon artık aktif değil.</p></body></html>`;
}

/** Firebase Hosting: /s/{kod} → bu fonksiyon (firebase.json rewrites). */
export const salonLanding = onRequest({ memory: "256MiB", maxInstances: 10 }, async (req, res) => {
  const code = (req.path.split("/").filter(Boolean).pop() ?? "").trim();
  res.set("Content-Type", "text/html; charset=utf-8");
  if (!isValidSalonCode(code)) {
    res.status(404).send(notFound());
    return;
  }
  const codeDoc = await db.collection("salonCodes").doc(code).get();
  const salon = codeDoc.exists ? await db.collection("salons").doc(codeDoc.get("salonId")).get() : null;
  if (!salon?.exists || salon.get("status") !== "ACTIVE") {
    res.status(404).send(notFound());
    return;
  }
  res.set("Cache-Control", "public, max-age=300, s-maxage=600");
  res.status(200).send(
    renderLanding({
      code,
      name: salon.get("name"),
      type: salon.get("type"),
      city: salon.get("city"),
      district: salon.get("district"),
      logoUrl: salon.get("logoUrl"),
      ratingAvg: salon.get("ratingAvg") ?? null,
    }),
  );
});
