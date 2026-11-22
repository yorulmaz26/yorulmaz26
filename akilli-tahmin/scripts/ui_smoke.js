#!/usr/bin/env node
/*
 * Akıllı Tahmin — web arayüzü duman testi (Playwright + Chromium).
 *
 * Kullanım:
 *   node scripts/ui_smoke.js [BASE_URL] [--out DIZIN] [--no-write] [--only mobile,desktop,mobile-dark]
 *
 *   BASE_URL     Varsayılan http://127.0.0.1:8765/ (scripts/mock_api.py). Gerçek backend için de çalışır.
 *   --out        Ekran görüntülerinin yazılacağı dizin (varsayılan: <tmp>/akilli-tahmin-ui).
 *   --no-write   Sunucu durumunu değiştiren adımları atla (PUT /settings, POST bildirim izni).
 *
 * Kontroller sözleşme odaklıdır: beklenen kart sayısı, boş durum mesajı, İddaa rozeti vb. aynı
 * uçlardan alınan yanıtlarla karşılaştırılır; böylece betik mock dışında da anlamlı kalır.
 * Her adımda konsol hatası olmadığı ve yatay taşma olmadığı (scrollWidth <= innerWidth) doğrulanır.
 * Çıkış kodu: 0 başarılı, 1 doğrulama hatası, 2 kurulum hatası.
 */
'use strict';

const fs = require('fs');
const os = require('os');
const path = require('path');

let chromium;
try {
  ({ chromium } = require('playwright'));
} catch (e) {
  console.error('playwright modülü bulunamadı (npm i playwright veya NODE_PATH ayarlayın).');
  process.exit(2);
}

// ---------------------------------------------------------------- argümanlar
const args = process.argv.slice(2);
let base = 'http://127.0.0.1:8765/';
let outDir = path.join(os.tmpdir(), 'akilli-tahmin-ui');
let allowWrite = true;
let only = null;
for (let i = 0; i < args.length; i++) {
  const a = args[i];
  if (a === '--out') outDir = args[++i];
  else if (a === '--no-write') allowWrite = false;
  else if (a === '--only') only = new Set(String(args[++i] || '').split(','));
  else if (!a.startsWith('--')) base = a;
}
if (!base.endsWith('/')) base += '/';
fs.mkdirSync(outDir, { recursive: true });

const MODES = ['highest_probability', 'best_value', 'balanced'];
const VIEWPORTS = [
  { name: 'mobile', width: 390, height: 844, colorScheme: 'light', robustness: true },
  { name: 'desktop', width: 1280, height: 900, colorScheme: 'light' },
  { name: 'mobile-dark', width: 360, height: 780, colorScheme: 'dark' },
].filter((v) => !only || only.has(v.name));

const failures = [];
const screenshots = [];
let checks = 0;

function check(cond, msg) {
  checks++;
  if (!cond) {
    failures.push(msg);
    console.log('  ✗ ' + msg);
  }
  return !!cond;
}

async function getJson(page, p) {
  const res = await page.request.get(base + p.replace(/^\//, ''));
  if (!res.ok()) throw new Error('GET ' + p + ' → HTTP ' + res.status());
  return res.json();
}

async function waitIdle(page, timeout = 15000) {
  await page.waitForFunction(
    () => document.querySelector('#main') && !document.querySelector('#main [aria-busy="true"]') && !document.querySelector('#main .state-loading'),
    null,
    { timeout }
  );
  await page.waitForTimeout(200); // ResizeObserver + rAF ile çizilen grafikler için
}

async function assertNoOverflow(page, label) {
  const m = await page.evaluate(() => ({ sw: document.documentElement.scrollWidth, iw: window.innerWidth }));
  check(m.sw <= m.iw, `${label}: yatay taşma (scrollWidth ${m.sw} > innerWidth ${m.iw})`);
}

async function shot(page, vp, name, opts = {}) {
  const file = path.join(outDir, `${vp.name}-${name}.png`);
  await page.screenshot({ path: file, fullPage: opts.fullPage !== false, ...opts.extra });
  screenshots.push(file);
  return file;
}

async function runViewport(browser, vp) {
  console.log(`\n== ${vp.name} (${vp.width}x${vp.height}, ${vp.colorScheme})`);
  const context = await browser.newContext({
    viewport: { width: vp.width, height: vp.height },
    deviceScaleFactor: 1,
    locale: 'tr-TR',
    timezoneId: 'Europe/Istanbul',
    colorScheme: vp.colorScheme,
  });
  try {
    await context.grantPermissions(['notifications'], { origin: new URL(base).origin });
  } catch (e) {
    /* bazı sürümlerde desteklenmeyebilir */
  }
  const page = await context.newPage();
  const consoleErrors = [];
  page.on('console', (msg) => {
    if (msg.type() === 'error') consoleErrors.push(msg.text());
  });
  page.on('pageerror', (err) => consoleErrors.push('pageerror: ' + err.message));
  const expectNoConsoleErrors = (label) => {
    check(consoleErrors.length === 0, `${label}: konsol hatası: ${consoleErrors.join(' | ')}`);
    consoleErrors.length = 0;
  };

  // ---- Akıllı Tahminler: varsayılan mod
  const modes = await getJson(page, 'api/v1/recommendations/modes');
  const expectedDefault = MODES.includes(modes.user_default_mode) ? modes.user_default_mode : 'balanced';
  await page.goto(base, { waitUntil: 'domcontentloaded' });
  await waitIdle(page);
  const sel = await page.getAttribute('[role="tablist"].mode-switch [aria-selected="true"]', 'data-mode');
  check(sel === expectedDefault, `ilk açılışta seçili mod ${expectedDefault} olmalı, bulunan ${sel}`);
  check((await page.evaluate(() => location.hash)) === '#tahminler/' + expectedDefault, 'URL hash mod ile senkron olmalı');
  const tabLabels = await page.$$eval('.mode-switch [role="tab"]', (els) => els.map((e) => e.textContent.trim()));
  check(JSON.stringify(tabLabels) === JSON.stringify(['En Güvenilir', 'En Avantajlı', 'Dengeli']), 'mod anahtarı sırası/etiketleri: ' + tabLabels.join(', '));
  const navLabels = await page.$$eval('.nav-tab', (els) => els.map((e) => e.textContent.trim()));
  check(JSON.stringify(navLabels) === JSON.stringify(['Akıllı Tahminler', 'Başarı Merkezi', 'Canlı Gol Radar', 'Ayarlar']), 'ana menü etiketleri: ' + navLabels.join(', '));
  await assertNoOverflow(page, 'tahminler ilk açılış');
  expectNoConsoleErrors('tahminler ilk açılış');

  // ---- üç mod
  let modeWithItems = null;
  for (const mode of MODES) {
    await page.click(`#mode-tab-${mode}`);
    await waitIdle(page);
    const rec = await getJson(page, `api/v1/recommendations?mode=${mode}`);
    const items = Array.isArray(rec.items) ? rec.items : [];
    check((await page.evaluate(() => location.hash)) === '#tahminler/' + mode, `${mode}: hash güncellenmeli`);
    const cards = await page.$$('.rec-card');
    check(cards.length === items.length, `${mode}: kart sayısı ${cards.length} ≠ ${items.length}`);
    const text = await page.innerText('#mode-panel');
    if (rec.disclaimer) check(text.includes(rec.disclaimer), `${mode}: uyarı metni (disclaimer) görünmeli`);
    if (rec.is_synthetic_data) check(text.includes('Sentetik demo verisi'), `${mode}: sentetik veri bandı görünmeli`);
    for (const label of ['Taranan maç', 'Aday', 'Uygun aday', 'Seçilen', 'Seçim yapılmayan maç']) check(text.includes(label), `${mode}: özet alanı "${label}"`);
    if (!items.length) {
      const expectedMsg = rec.message || '';
      const shown = await page.innerText('.empty-state .empty-message').catch(() => '');
      check(shown.trim() === expectedMsg.trim() || (!expectedMsg && shown), `${mode}: boş durum mesajı "${expectedMsg}" gösterilmeli, bulunan "${shown}"`);
    } else {
      if (!modeWithItems) modeWithItems = mode;
      const cardText = await cards[0].innerText();
      for (const label of ['Market', 'Seçenek', 'Gerçek oran', 'MODEL A olasılığı', 'MODEL B olasılığı', 'Beklenen değer', 'Belirsizlik seviyesi',
        'Tahmin dayanıklılığı', 'Veri güncelliği', 'Tahmin geçerliliği', 'Geçmiş başarı', 'Seçim gerekçesi', 'Bağımsız denetim sonucu', 'Başlangıç saati', 'Neden seçildi?']) {
        check(cardText.includes(label), `${mode}: kartta "${label}" alanı olmalı`);
      }
      const iddaa = items.filter((it) => it.odds && it.odds.is_iddaa === true).length;
      check((await page.$$('.rec-card .badge-iddaa')).length === iddaa, `${mode}: İddaa rozeti yalnızca is_iddaa=true olan ${iddaa} kartta olmalı`);
      const nullOdds = items.filter((it) => !it.odds).length;
      if (nullOdds && mode === 'highest_probability') {
        const n = await page.locator('.rec-card', { hasText: 'Oran gerekli değil (En Güvenilir modu)' }).count();
        check(n === nullOdds, `${mode}: oran null kartlarda "Oran gerekli değil" metni (${n}/${nullOdds})`);
      }
      check(/%\d+,\d/.test(cardText), `${mode}: yüzdeler Türkçe biçimde (%56,2) olmalı`);
    }
    const thr = (modes.modes || []).find((m) => m.code === mode);
    if (thr && thr.thresholds && thr.thresholds.status === 'established') check(text.includes('Eşikler belirlendi'), `${mode}: eşik durumu gösterilmeli`);
    await assertNoOverflow(page, `tahminler ${mode}`);
    expectNoConsoleErrors(`tahminler ${mode}`);
    await shot(page, vp, `01-tahminler-${mode}`);
  }

  // ---- Neden seçildi?
  if (modeWithItems) {
    await page.click(`#mode-tab-${modeWithItems}`);
    await waitIdle(page);
    const firstCard = page.locator('.rec-card').first();
    const id = await firstCard.getAttribute('data-id');
    await firstCard.locator('details.explain > summary').click();
    await waitIdle(page);
    const expl = await getJson(page, `api/v1/recommendations/${id}/decision-explanation`);
    const nChecks = (expl.checks || []).length + ((expl.audit && expl.audit.checks) || []).length;
    const shown = await firstCard.locator('details.explain .check').count();
    check(shown === nChecks, `Neden seçildi?: ${nChecks} kontrol bekleniyordu, ${shown} gösterildi`);
    await assertNoOverflow(page, 'neden seçildi');
    expectNoConsoleErrors('neden seçildi');
    await firstCard.screenshot({ path: path.join(outDir, `${vp.name}-02-neden-secildi.png`) });
    screenshots.push(path.join(outDir, `${vp.name}-02-neden-secildi.png`));
  } else {
    check(false, 'hiçbir modda seçim yok; "Neden seçildi?" test edilemedi');
  }

  // ---- Neden elendi?
  const rejMode = modeWithItems || 'balanced';
  await page.click('details.rejections > summary');
  await waitIdle(page);
  const rej = await getJson(page, `api/v1/recommendations/rejections?mode=${rejMode}&limit=50`);
  const rows = page.locator('.reject-item');
  check((await rows.count()) === (rej.items || []).length, `Neden elendi?: satır sayısı ${await rows.count()} ≠ ${(rej.items || []).length}`);
  check((await page.locator('.bar-list .bar-row').count()) === (rej.reason_counts || []).length, 'Neden elendi?: neden sayıları çubuk listesi');
  if ((await rows.count()) > 0) {
    await rows.first().locator('summary').click();
    await waitIdle(page);
    check((await rows.first().locator('.check').count()) > 0, 'Neden elendi?: satır açılınca başarısız koşullar listelenmeli');
  }
  const opts = await page.locator('details.rejections select option').count();
  if (opts > 1) {
    const value = await page.locator('details.rejections select option').nth(1).getAttribute('value');
    const label = (await page.locator('details.rejections select option').nth(1).innerText()).trim();
    await page.selectOption('details.rejections select', value);
    await waitIdle(page);
    const titles = await page.$$eval('.reject-item .reject-title', (els) => els.map((e) => e.textContent.trim()));
    check(titles.length > 0 && titles.every((t) => t === label), `Neden elendi?: maç filtresi (${label}) uygulanmalı`);
    await page.selectOption('details.rejections select', '');
    await waitIdle(page);
  }
  await assertNoOverflow(page, 'neden elendi');
  expectNoConsoleErrors('neden elendi');
  await shot(page, vp, '03-neden-elendi');

  // ---- yeniden yükleme modu korumalı
  await page.click('#mode-tab-highest_probability');
  await waitIdle(page);
  await page.reload({ waitUntil: 'domcontentloaded' });
  await waitIdle(page);
  check((await page.getAttribute('.mode-switch [aria-selected="true"]', 'data-mode')) === 'highest_probability', 'yeniden yüklemede mod (hash) korunmalı');
  expectNoConsoleErrors('yeniden yükleme');

  // ---- Başarı Merkezi › Seçim Modları
  await page.click('.nav-tab[data-page="basari"]');
  await waitIdle(page);
  await page.click('#subtab-secim-modlari');
  await waitIdle(page);
  const cmpRows = await page.locator('#success-panel table').first().locator('tbody tr').count();
  check(cmpRows === 4, `karşılaştırma tablosu 3 mod + "Tüm adaylar" (4 satır) olmalı, bulunan ${cmpRows}`);
  const cmpText = await page.innerText('#success-panel');
  for (const label of ['Uygun aday', 'Önerilen seçim', 'Seçim yapılmayan maç', 'Seçim sıklığı', 'İsabet oranı', 'Brier', 'Log Loss', 'ECE (kalibrasyon)', 'Ort. oran', 'Ort. olasılık', 'Ort. EV', 'Teorik birim ROI', 'Kâr (birim)', 'Tüm adaylar']) {
    check(cmpText.includes(label), `Seçim Modları: "${label}" sütunu/satırı`);
  }
  check(cmpText.includes('Karşılaştırma kapsamı'), 'Seçim Modları: havuz açıklaması ve notlar gösterilmeli');
  const svgCount = await page.locator('#success-panel .chart svg').count();
  check(svgCount >= 5, `Seçim Modları: en az 5 SVG grafik (4 güvenilirlik + 1 kâr) bekleniyordu, bulunan ${svgCount}`);
  check((await page.locator('#success-panel .legend li').count()) === 3, 'kâr grafiği lejantı 3 mod içermeli');
  await page.click('.seg-btn[data-period="30d"]');
  await waitIdle(page);
  check((await page.getAttribute('.seg-btn[data-period="30d"]', 'aria-pressed')) === 'true', 'dönem seçici 30 gün');
  check((await page.innerText('#success-panel')).includes('Son 30 gün'), 'dönem etiketi güncellenmeli');
  await assertNoOverflow(page, 'başarı merkezi');
  expectNoConsoleErrors('başarı merkezi');
  await shot(page, vp, '04-basari-secim-modlari');
  // çapraz çizgi + ipucu
  const overlay = page.locator('#success-panel .chart svg rect.hit').first();
  if (await overlay.count()) {
    await overlay.scrollIntoViewIfNeeded();
    await page.waitForTimeout(250); // kaydırma olayı ipucunu gizler; kaydırma bitsin
    const box = await overlay.boundingBox();
    if (box) {
      await page.mouse.move(box.x + box.width * 0.6, box.y + box.height * 0.5);
      await page.waitForTimeout(150);
      check(await page.isVisible('#tooltip'), 'kâr grafiği üzerinde ipucu görünmeli');
      const panel = page.locator('section[aria-labelledby="profit-title"]');
      await panel.screenshot({ path: path.join(outDir, `${vp.name}-05-kar-grafigi-ipucu.png`) });
      screenshots.push(path.join(outDir, `${vp.name}-05-kar-grafigi-ipucu.png`));
      await page.mouse.move(1, 1);
    }
  }
  await page.click('#subtab-genel');
  await waitIdle(page);
  check((await page.innerText('#success-panel')).includes('Tüm adaylar'), 'Genel sekmesi temel metrikleri göstermeli');
  await assertNoOverflow(page, 'başarı merkezi genel');
  expectNoConsoleErrors('başarı merkezi genel');
  await page.click('#subtab-secim-modlari');
  await waitIdle(page);
  await page.click('.seg-btn[data-period="all"]');
  await waitIdle(page);

  // ---- Canlı Gol Radar
  await page.click('.nav-tab[data-page="radar"]');
  await waitIdle(page);
  const radarText = await page.innerText('#main');
  for (const label of ['MODEL A olasılığı', 'MODEL B olasılığı', '%90', 'Normal sürede kalan süre', 'Güncel canlı veri', 'Gerekli güncel oran', 'nadir olay kalibrasyonu', 'Tahmin Kalite Kapısı', 'Bildirim izni', 'Mükerrer alarm engeli', 'Seçim modları radar eşiklerini değiştiremez']) {
    check(radarText.includes(label), `Radar: "${label}" görünmeli`);
  }
  const live = await getJson(page, 'api/v1/radar/live');
  check((await page.locator('.live-card').count()) === (live.items || []).length, 'Radar: canlı maç kartı sayısı');
  if ((live.items || []).length) {
    const chips = await page.locator('.live-card').first().locator('.chip').count();
    check(chips === (live.items[0].conditions || []).length, 'Radar: koşul çipleri');
  }
  if (allowWrite) {
    const sw = page.locator('button[role="switch"]');
    const before = await sw.getAttribute('aria-checked');
    const [resp] = await Promise.all([
      page.waitForResponse((r) => r.url().includes('/api/v1/radar/notifications/permission') && r.request().method() === 'POST'),
      sw.click(),
    ]);
    check(resp.ok(), 'Radar: bildirim izni POST başarılı olmalı');
    await page.waitForFunction((b) => document.querySelector('button[role="switch"]').getAttribute('aria-checked') !== b, before, { timeout: 5000 }).catch(() => {});
    check((await sw.getAttribute('aria-checked')) !== before, 'Radar: bildirim anahtarı durum değiştirmeli');
    await waitIdle(page);
  }
  await assertNoOverflow(page, 'radar');
  expectNoConsoleErrors('radar');
  await shot(page, vp, '06-radar');
  if (allowWrite && (await page.getAttribute('button[role="switch"]', 'aria-checked')) === 'true') {
    await Promise.all([
      page.waitForResponse((r) => r.url().includes('/api/v1/radar/notifications/permission')),
      page.click('button[role="switch"]'),
    ]);
    await waitIdle(page);
  }

  // ---- Ayarlar
  await page.click('.nav-tab[data-page="ayarlar"]');
  await waitIdle(page);
  const settings = await getJson(page, 'api/v1/settings');
  check((await page.locator('input[name="default_mode"]').count()) === 3, 'Ayarlar: üç mod seçeneği');
  check((await page.getAttribute('input[name="default_mode"]:checked', 'value')) === settings.default_mode, 'Ayarlar: kayıtlı mod işaretli olmalı');
  check((await page.innerText('#main')).includes('Dengeli Seçimler sistemin varsayılan modudur'), 'Ayarlar: Dengeli varsayılan açıklaması');
  await assertNoOverflow(page, 'ayarlar');
  expectNoConsoleErrors('ayarlar');
  await shot(page, vp, '07-ayarlar');
  if (allowWrite) {
    const original = settings.default_mode;
    const target = original === 'best_value' ? 'highest_probability' : 'best_value';
    await page.check(`input[value="${target}"]`);
    const [put] = await Promise.all([
      page.waitForResponse((r) => r.url().includes('/api/v1/settings') && r.request().method() === 'PUT'),
      page.click('form button[type="submit"]'),
    ]);
    check(put.ok(), 'Ayarlar: PUT /settings başarılı olmalı');
    await page.waitForSelector('.form-status.ok');
    await page.click('.nav-tab[data-page="tahminler"]');
    await waitIdle(page);
    check((await page.getAttribute('.mode-switch [aria-selected="true"]', 'data-mode')) === target, 'Ayarlar sonrası Akıllı Tahminler yeni varsayılan modla açılmalı');
    await page.click('.nav-tab[data-page="ayarlar"]');
    await waitIdle(page);
    await page.check(`input[value="${original}"]`);
    await Promise.all([
      page.waitForResponse((r) => r.url().includes('/api/v1/settings') && r.request().method() === 'PUT'),
      page.click('form button[type="submit"]'),
    ]);
    await page.waitForSelector('.form-status.ok');
    expectNoConsoleErrors('ayarlar kaydet');
  }

  if (vp.robustness) await robustness(context, vp);
  await context.close();
}

// ---------------------------------------------------------------- hata/boş/null senaryoları (route ile)
async function robustness(context, vp) {
  console.log('  -- dayanıklılık senaryoları');
  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', (err) => errors.push('pageerror: ' + err.message));
  page.on('console', (msg) => {
    if (msg.type() === 'error' && !/Failed to load resource/.test(msg.text())) errors.push(msg.text());
  });
  const isRecs = (u) => new URL(u).pathname === '/api/v1/recommendations';
  const isModes = (u) => new URL(u).pathname === '/api/v1/recommendations/modes';

  // a) modes çağrısı başarısız → Dengeli seçilmeli, hata + tekrar dene görünmeli
  await page.route(isModes, (route) => route.fulfill({ status: 500, contentType: 'application/json', body: '{"detail":"x"}' }));
  await page.goto(base, { waitUntil: 'domcontentloaded' });
  await waitIdle(page);
  check((await page.getAttribute('.mode-switch [aria-selected="true"]', 'data-mode')) === 'balanced', 'modes hatasında Dengeli seçilmeli');
  check((await page.innerText('#main')).includes('Mod açıklamaları ve eşikleri yüklenemedi'), 'modes hatası kullanıcıya gösterilmeli');
  await page.unroute(isModes);

  // b) öneri çağrısı hata → hata kutusu + "Tekrar dene" → başarı
  await page.route(isRecs, (route) => route.fulfill({ status: 503, contentType: 'application/json', body: '{"detail":"bakım"}' }));
  await page.click('#mode-tab-highest_probability');
  await waitIdle(page);
  check(await page.isVisible('.results .state-error button'), 'öneri hatasında "Tekrar dene" düğmesi görünmeli');
  await shot(page, vp, '08-hata-durumu', { fullPage: false });
  await page.unroute(isRecs);
  await page.click('.results .state-error button');
  await waitIdle(page);
  check((await page.locator('.results .state-error').count()) === 0, '"Tekrar dene" sonrası hata kalkmalı');

  // c) eşikler belirlenmemiş + boş sonuç
  await page.route(isModes, async (route) => {
    const res = await route.fetch();
    const json = await res.json();
    for (const m of json.modes || []) if (m.code === 'balanced') m.thresholds = { status: 'not_established', values: null, source: null };
    await route.fulfill({ response: res, json });
  });
  await page.route(isRecs, async (route) => {
    const res = await route.fetch();
    const json = await res.json();
    json.items = [];
    json.message = 'Güvenilir ve avantajlı seçim bulunamadı.';
    await route.fulfill({ response: res, json });
  });
  await page.goto(base + '#tahminler/balanced', { waitUntil: 'domcontentloaded' });
  await page.reload({ waitUntil: 'domcontentloaded' });
  await waitIdle(page);
  const t = await page.innerText('#main');
  check(t.includes('Eşikler henüz belirlenmedi'), 'not_established eşik açıklaması gösterilmeli');
  check(t.includes('Güvenilir ve avantajlı seçim bulunamadı.'), 'boş durum mesajı gösterilmeli');
  await shot(page, vp, '09-esik-yok-bos', { fullPage: false });
  await page.unroute(isModes);
  await page.unroute(isRecs);

  // d) null alanlar ve kaçış (escape) — hiçbir şey patlamamalı, HTML metin olarak görünmeli
  await page.route(isRecs, async (route) => {
    const res = await route.fetch();
    const json = await res.json();
    const tpl = (json.items && json.items[0]) || { id: 1 };
    const nulled = {};
    for (const k of Object.keys(tpl)) nulled[k] = null;
    nulled.id = 999001;
    nulled.fixture = { id: 1, home: '<img src=x onerror=alert(1)>', away: null, league: null, kickoff_at: 'geçersiz' };
    nulled.reason_summary = '<b>kalın değil</b>';
    json.items = [nulled, { id: 999002 }];
    json.summary = null;
    await route.fulfill({ response: res, json });
  });
  await page.goto(base + '#tahminler/best_value', { waitUntil: 'domcontentloaded' });
  await page.reload({ waitUntil: 'domcontentloaded' });
  await waitIdle(page);
  check((await page.locator('.rec-card').count()) === 2, 'null alanlı kartlar yine de çizilmeli');
  check((await page.locator('.rec-card img').count()) === 0 && (await page.innerText('#main')).includes('<img src=x'), 'API metni kaçışlanmalı (HTML olarak yorumlanmamalı)');
  await assertNoOverflow(page, 'null alanlar');
  await page.unroute(isRecs);

  // e) klavye: mod sekmeleri ok tuşlarıyla gezilebilmeli
  await page.goto(base + '#tahminler/balanced', { waitUntil: 'domcontentloaded' });
  await page.reload({ waitUntil: 'domcontentloaded' });
  await waitIdle(page);
  await page.focus('#mode-tab-balanced');
  await page.keyboard.press('ArrowRight');
  await waitIdle(page);
  check((await page.getAttribute('.mode-switch [aria-selected="true"]', 'data-mode')) === 'highest_probability', 'mod sekmelerinde ok tuşu gezintisi (Dengeli → En Güvenilir)');
  check(errors.length === 0, 'dayanıklılık senaryolarında JS hatası: ' + errors.join(' | '));
  await page.close();

  // f) radar canlı listesi 30 sn'de bir yenilenmeli; sayfadan çıkınca durmalı (sahte saat)
  const p2 = await context.newPage();
  const liveHits = [];
  p2.on('request', (r) => {
    if (new URL(r.url()).pathname === '/api/v1/radar/live') liveHits.push(Date.now());
  });
  await p2.clock.install();
  await p2.goto(base + '#radar', { waitUntil: 'domcontentloaded' });
  await waitIdle(p2);
  const n0 = liveHits.length;
  await p2.clock.fastForward(31000);
  await p2.waitForTimeout(400);
  check(liveHits.length > n0, 'Radar: canlı liste 30 saniyede bir otomatik yenilenmeli');
  await p2.click('.nav-tab[data-page="ayarlar"]');
  await waitIdle(p2);
  const n1 = liveHits.length;
  await p2.clock.fastForward(65000);
  await p2.waitForTimeout(300);
  check(liveHits.length === n1, 'Radar: sayfadan çıkınca otomatik yenileme durmalı');
  await p2.close();
}

(async () => {
  let browser;
  try {
    browser = await chromium.launch();
  } catch (e) {
    console.error('Chromium başlatılamadı: ' + e.message);
    process.exit(2);
  }
  console.log('Hedef: ' + base + '  Ekran görüntüleri: ' + outDir);
  try {
    for (const vp of VIEWPORTS) {
      try {
        await runViewport(browser, vp);
      } catch (e) {
        failures.push(`${vp.name}: beklenmeyen hata: ${e.message}`);
        console.log('  ✗ ' + e.stack);
      }
    }
  } finally {
    await browser.close();
  }
  console.log(`\n${checks} kontrol, ${failures.length} hata.`);
  console.log('Ekran görüntüleri:\n  ' + screenshots.join('\n  '));
  if (failures.length) {
    console.log('\nHATALAR:\n  - ' + failures.join('\n  - '));
    process.exit(1);
  }
  console.log('TAMAM');
})();
