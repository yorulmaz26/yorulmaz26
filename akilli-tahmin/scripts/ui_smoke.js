#!/usr/bin/env node
/*
 * Akıllı Tahmin — web arayüzü duman testi (Playwright + Chromium).
 *
 * Kullanım:
 *   node scripts/ui_smoke.js [BASE_URL] [--out DIZIN] [--no-write] [--only narrow,mobile,desktop,mobile-dark]
 *
 *   BASE_URL     Varsayılan http://127.0.0.1:8765/ (scripts/mock_api.py). Gerçek backend için de çalışır.
 *   --out        Ekran görüntülerinin yazılacağı dizin (varsayılan: <tmp>/akilli-tahmin-ui).
 *   --no-write   Durum değiştiren istekler (PUT /settings, POST bildirim izni) sunucuya gönderilmez;
 *                tarayıcıda yerel olarak yanıtlanır. Arayüz akışı (durum metni, anahtar) yine doğrulanır.
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
  { name: 'narrow', width: 320, height: 700, colorScheme: 'light' },
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

// Başarı Merkezi uçlarının istemci zaman aşımı 45 sn'dir; gerçek veride bekleme bunu kapsamalı.
async function waitIdle(page, timeout = 60000) {
  await page.waitForFunction(
    () => document.querySelector('#main') && !document.querySelector('#main [aria-busy="true"]') && !document.querySelector('#main .state-loading'),
    null,
    { timeout }
  );
  await page.waitForTimeout(200); // ResizeObserver + rAF ile çizilen grafikler için
}

/** Ana menü tıklaması: hash ve aria-current yeni sayfayı gösterene kadar bekle, sonra yüklemelerin bitmesini bekle. */
async function navTo(page, name) {
  await page.click(`.nav-tab[data-page="${name}"]`);
  await page.waitForFunction(
    (n) => location.hash.replace(/^#\/?/, '').split('/')[0] === n
      && (document.querySelector(`.nav-tab[data-page="${n}"]`) || {}).getAttribute?.('aria-current') === 'page',
    name,
    { timeout: 10000 }
  );
  await waitIdle(page);
}

/** Başarı Merkezi alt sekmesi: hash ve aria-selected güncellenene kadar bekle. */
async function subtabTo(page, code) {
  await page.click(`#subtab-${code}`);
  await page.waitForFunction(
    (c) => location.hash === '#basari/' + c && document.querySelector('#subtab-' + c).getAttribute('aria-selected') === 'true',
    code,
    { timeout: 10000 }
  );
  await waitIdle(page);
}

/** Uygulamadaki fmtSigned(v, 1) ile aynı biçim (+1,2 / −0,6 / 0,0). */
function fmtSigned1(v) {
  if (typeof v !== 'number' || !Number.isFinite(v)) return '—';
  const fixed = Math.abs(v).toFixed(1);
  const neg = v < 0 && Number(fixed) !== 0;
  const [i, f] = fixed.split('.');
  const body = i.replace(/\B(?=(\d{3})+(?!\d))/g, '.') + ',' + f;
  return (neg ? '−' : v > 0 && Number(fixed) !== 0 ? '+' : '') + body;
}

/** Sunucuya yazmadan durum değiştiren istekleri tarayıcıda yanıtla (--no-write). İçerik türünü de doğrular. */
async function routeWritesLocally(page) {
  await page.route((u) => ['/api/v1/settings', '/api/v1/radar/notifications/permission'].includes(new URL(u).pathname), async (route) => {
    const req = route.request();
    if (req.method() === 'GET') return route.continue();
    const ctype = (req.headers()['content-type'] || '').split(';')[0].trim();
    if (ctype !== 'application/json') return route.fulfill({ status: 415, contentType: 'application/json', body: '{"detail":"application/json gerekli"}' });
    let body = {};
    try {
      body = JSON.parse(req.postData() || '{}');
    } catch (e) {
      body = {};
    }
    const json = new URL(req.url()).pathname.endsWith('/settings')
      ? { user_id: 'local', default_mode: body.default_mode }
      : { user_id: 'local', granted: body.granted === true };
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(json) });
  });
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
  if (!allowWrite) await routeWritesLocally(page);

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
  const modesWithItems = [];
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
      modesWithItems.push(mode);
      const cardText = await cards[0].innerText();
      for (const label of ['Market', 'Seçenek', 'Gerçek oran', 'MODEL A olasılığı', 'MODEL B olasılığı', 'Beklenen değer', 'Belirsizlik seviyesi',
        'Tahmin dayanıklılığı', 'Veri güncelliği', 'Tahmin geçerliliği', 'Geçmiş başarı', 'Seçim gerekçesi', 'Bağımsız denetim sonucu', 'Başlangıç saati', 'Neden seçildi?']) {
        check(cardText.includes(label), `${mode}: kartta "${label}" alanı olmalı`);
      }
      const iddaa = items.filter((it) => it.odds && it.odds.is_iddaa === true).length;
      check((await page.$$('.rec-card .badge-iddaa')).length === iddaa, `${mode}: İddaa rozeti yalnızca is_iddaa=true olan ${iddaa} kartta olmalı`);
      const nullOdds = items.filter((it) => !it.odds).length;
      if (nullOdds && mode === 'highest_probability') {
        const n = await page.locator('.rec-card', { hasText: 'Geçerli oran yok (bu modda zorunlu değil)' }).count();
        check(n === nullOdds, `${mode}: oran null kartlarda "Geçerli oran yok (bu modda zorunlu değil)" metni (${n}/${nullOdds})`);
      }
      const repeated = items.filter((it) => it.decision && it.decision.first_decision === false).length;
      const notes = await page.locator('.rec-card .decision-note').count();
      check(notes === repeated, `${mode}: ilk karar notu yalnızca first_decision=false olan ${repeated} kartta olmalı (bulunan ${notes})`);
      if (repeated) check(text.includes('Performans ilk karardaki oran ve olasılıkla'), `${mode}: ilk karar notu metni`);
      check(/%\d+,\d/.test(cardText), `${mode}: yüzdeler Türkçe biçimde (%56,2) olmalı`);
    }
    const thr = (modes.modes || []).find((m) => m.code === mode);
    const th = (thr && thr.thresholds) || {};
    if (th.status === 'established') check(text.includes('Eşikler belirlendi'), `${mode}: eşik durumu gösterilmeli`);
    else if (th.validation_window && (th.validation_window.start || th.validation_window.end)) {
      // Doğrulama yapıldı ama eşik belirlenemedi: sunucunun gerçek gerekçesi, pencere ve n görünmeli.
      if (th.reason) check(text.includes(th.reason), `${mode}: not_established gerekçesi gösterilmeli ("${th.reason}")`);
      check(text.includes('Doğrulama penceresi'), `${mode}: not_established doğrulama penceresi gösterilmeli`);
      if (th.validation_metrics && typeof th.validation_metrics.n === 'number') check(text.includes('n = '), `${mode}: doğrulama n gösterilmeli`);
      check(!text.includes('henüz kronolojik doğrulama yapılmadı'), `${mode}: doğrulama yapılmışken "henüz doğrulama yapılmadı" denmemeli`);
    } else check(text.includes('Eşikler henüz belirlenmedi'), `${mode}: doğrulama yokken "Eşikler henüz belirlenmedi" gösterilmeli`);
    await assertNoOverflow(page, `tahminler ${mode}`);
    expectNoConsoleErrors(`tahminler ${mode}`);
    await shot(page, vp, `01-tahminler-${mode}`);
  }

  // ---- Neden seçildi? (seçim olan her modda en fazla 3 kart)
  if (modesWithItems.length) {
    const REF = new Set(['audit', 'thresholds_established']);
    let firstShot = false;
    let nullShot = false;
    let sawConditions = false;
    for (const mode of modesWithItems) {
      await page.click(`#mode-tab-${mode}`);
      await waitIdle(page);
      const n = Math.min(3, await page.locator('.rec-card').count());
      for (let k = 0; k < n; k++) {
        const card = page.locator('.rec-card').nth(k);
        const id = await card.getAttribute('data-id');
        await card.locator('details.explain > summary').click();
        await waitIdle(page);
        const expl = await getJson(page, `api/v1/recommendations/${id}/decision-explanation`);
        const conds = Array.isArray(expl.conditions) ? expl.conditions : [];
        const auditChecks = (expl.audit && expl.audit.checks) || [];
        const nChecks = (expl.checks || []).length + auditChecks.length + conds.length;
        const shown = await card.locator('details.explain .check').count();
        check(shown === nChecks, `Neden seçildi? (${mode} #${id}): ${nChecks} kontrol bekleniyordu, ${shown} gösterildi`);
        // Değeri null olan (veya kayıt kimliği taşıyan) kontrollerde "Değer …" yazılmamalı.
        const withValue = (expl.checks || []).filter((c) => c && c.value != null && !REF.has(c.code)).length;
        const valueTexts = await card.locator('details.explain .check-values', { hasText: 'Değer ' }).count();
        check(valueTexts === withValue, `Neden seçildi? (${mode} #${id}): "Değer" yalnızca değeri olan ${withValue} kontrolde gösterilmeli (bulunan ${valueTexts})`);
        // passed=null → "Değerlendirilmedi" (nötr); başarısız sayılmamalı.
        const failExpected = [...conds, ...(expl.checks || []), ...auditChecks].filter((c) => c && c.passed === false).length;
        const failShown = await card.locator('details.explain .check.fail').count();
        check(failShown === failExpected, `Neden seçildi? (${mode} #${id}): başarısız gösterilen ${failShown} ≠ ${failExpected}`);
        const nullConds = conds.filter((c) => c && c.passed !== true && c.passed !== false).length;
        if (conds.length) {
          sawConditions = true;
          check((await card.locator('details.explain', { hasText: 'Dengeli modun koşulları' }).count()) === 1, `Neden seçildi? (${mode} #${id}): Dengeli koşulları gösterilmeli`);
        }
        if (nullConds) {
          const neutral = await card.locator('details.explain .check.pending', { hasText: 'Değerlendirilmedi' }).count();
          check(neutral >= nullConds, `Neden seçildi? (${mode} #${id}): passed=null ${nullConds} koşul "Değerlendirilmedi" (nötr) gösterilmeli, bulunan ${neutral}`);
        }
        await assertNoOverflow(page, `neden seçildi ${mode}`);
        expectNoConsoleErrors(`neden seçildi ${mode}`);
        for (const [want, suffix] of [[!firstShot, ''], [nullConds > 0 && !nullShot, '-degerlendirilmedi']]) {
          if (!want) continue;
          const file = path.join(outDir, `${vp.name}-02-neden-secildi${suffix}.png`);
          await card.screenshot({ path: file });
          screenshots.push(file);
          if (suffix) nullShot = true;
          else firstShot = true;
        }
      }
    }
    if (modesWithItems.includes('balanced')) check(sawConditions, 'Dengeli kartlarında numaralı koşullar (conditions) gösterilmeli');
  } else {
    check(false, 'hiçbir modda seçim yok; "Neden seçildi?" test edilemedi');
  }

  // ---- Neden elendi? (sayfalama + bütün maçlardan kurulan maç filtresi)
  const rejMode = 'balanced';
  await page.click(`#mode-tab-${rejMode}`);
  await waitIdle(page);
  await page.click('details.rejections > summary');
  await waitIdle(page);
  const rej = await getJson(page, `api/v1/recommendations/rejections?mode=${rejMode}&limit=50&offset=0`);
  const rows = page.locator('details.rejections .reject-item');
  const rejTotal = typeof rej.total === 'number' ? rej.total : (rej.items || []).length;
  check((await rows.count()) === (rej.items || []).length, `Neden elendi?: satır sayısı ${await rows.count()} ≠ ${(rej.items || []).length}`);
  check((await page.locator('.bar-list .bar-row').count()) === (rej.reason_counts || []).length, 'Neden elendi?: neden sayıları çubuk listesi');
  const shownText = async () => (await page.locator('details.rejections .reject-shown').innerText().catch(() => '')).trim();
  const fmtCount = (n) => String(n).replace(/\B(?=(\d{3})+(?!\d))/g, '.');
  if ((rej.items || []).length) check((await shownText()) === `${fmtCount((rej.items || []).length)} / ${fmtCount(rejTotal)} aday gösteriliyor`, `Neden elendi?: "N / toplam" sayacı (${await shownText()})`);
  const moreBtn = page.locator('details.rejections .load-more-btn');
  check((await moreBtn.count()) === ((rej.items || []).length < rejTotal ? 1 : 0), 'Neden elendi?: "Daha fazla göster" yalnızca kalan aday varken görünmeli');
  if ((await rows.count()) > 0) {
    await rows.first().locator('summary').click();
    await waitIdle(page);
    check((await rows.first().locator('.check').count()) > 0, 'Neden elendi?: satır açılınca başarısız koşullar listelenmeli');
    await rows.first().locator('summary').click();
  }
  if (await moreBtn.count()) {
    const before = await rows.count();
    await moreBtn.click();
    const want = Math.min(rejTotal, before + 50);
    await page.waitForFunction((n) => document.querySelectorAll('details.rejections .reject-item').length >= n, want, { timeout: 60000 }).catch(() => {});
    check((await rows.count()) === want, `Neden elendi?: "Daha fazla göster" sonrası ${want} satır bekleniyordu, ${await rows.count()} var`);
    check((await shownText()) === `${fmtCount(want)} / ${fmtCount(rejTotal)} aday gösteriliyor`, `Neden elendi?: sayaç güncellenmeli (${await shownText()})`);
    const ids = await page.$$eval('details.rejections .reject-item', (els) => els.length);
    check(ids === want, 'Neden elendi?: sayfalar tekrar etmemeli');
  }
  const optLoc = page.locator('details.rejections select option');
  const opts = await optLoc.count();
  if (Array.isArray(rej.fixtures)) {
    check(opts === rej.fixtures.length + 1, `Neden elendi?: maç filtresi sunucunun fixtures listesindeki ${rej.fixtures.length} maçın tamamını içermeli (bulunan ${opts - 1})`);
  }
  if (opts > 1) {
    // En çok adayı olan maçı seç (maç içinde sayfalama da denensin).
    let pick = 1;
    if (Array.isArray(rej.fixtures) && rej.fixtures.length) {
      const best = rej.fixtures.reduce((a, b) => ((b.count || 0) > (a.count || 0) ? b : a));
      const values = await optLoc.evaluateAll((els) => els.map((e) => e.value));
      pick = Math.max(1, values.indexOf(String(best.id)));
    }
    const value = await optLoc.nth(pick).getAttribute('value');
    const fx = (rej.fixtures || []).find((f) => String(f.id) === value);
    await page.selectOption('details.rejections select', value);
    await page.waitForFunction(() => !document.querySelector('details.rejections [aria-busy="true"]'), null, { timeout: 60000 });
    await waitIdle(page);
    const titles = await page.$$eval('details.rejections .reject-item .reject-title', (els) => els.map((e) => e.textContent.trim()));
    const label = fx ? `${fx.home} – ${fx.away}` : titles[0];
    check(titles.length > 0 && titles.every((t) => t === label), `Neden elendi?: maç filtresi (${label}) uygulanmalı`);
    const fRes = await getJson(page, `api/v1/recommendations/rejections?mode=${rejMode}&limit=50&offset=0&fixture_id=${value}`);
    check(titles.length === (fRes.items || []).length, `Neden elendi?: filtreli ilk sayfa ${titles.length} ≠ ${(fRes.items || []).length}`);
    if (fx && typeof fx.count === 'number') check(fx.count === fRes.total, `Neden elendi?: fixtures.count (${fx.count}) filtreli total (${fRes.total}) ile aynı olmalı`);
    if ((fRes.items || []).length < fRes.total) {
      await moreBtn.click();
      const want = Math.min(fRes.total, 100);
      await page.waitForFunction((n) => document.querySelectorAll('details.rejections .reject-item').length >= n, want, { timeout: 60000 }).catch(() => {});
      const t2 = await page.$$eval('details.rejections .reject-item .reject-title', (els) => els.map((e) => e.textContent.trim()));
      check(t2.length === want && t2.every((t) => t === label), `Neden elendi?: filtreli "Daha fazla göster" (${t2.length}/${want})`);
    }
    await assertNoOverflow(page, 'neden elendi (filtreli)');
    await shot(page, vp, '03b-neden-elendi-filtre');
    await page.selectOption('details.rejections select', '');
    await waitIdle(page);
    check((await rows.count()) === (rej.items || []).length, 'Neden elendi?: "Tüm maçlar" seçilince liste baştan (offset 0) yüklenmeli');
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
  const heavy = { started: 0, failed: 0, inflight: 0, maxInflight: 0 };
  const isHeavy = (u) => /\/api\/v1\/(recommendations\/modes\/(comparison|performance)|performance\/selection-modes)/.test(u);
  page.on('request', (r) => {
    if (!isHeavy(r.url())) return;
    heavy.started++;
    heavy.inflight++;
    heavy.maxInflight = Math.max(heavy.maxInflight, heavy.inflight);
  });
  page.on('requestfinished', (r) => {
    if (isHeavy(r.url())) heavy.inflight--;
  });
  page.on('requestfailed', (r) => {
    if (!isHeavy(r.url())) return;
    heavy.inflight--;
    heavy.failed++;
  });
  const legendMatchesTable = async (p) => {
    const comp = await getJson(page, `api/v1/recommendations/modes/comparison?period=${p}`);
    const hasTimeline = (comp.modes || []).some((m) => Array.isArray(m.timeline));
    for (const m of comp.modes || []) {
      if (!MODES.includes(m.code)) continue;
      const cell = (await page.locator(`#success-panel tr[data-mode="${m.code}"] td`).last().innerText().catch(() => '')).trim();
      const legend = (await page.locator(`#success-panel .legend li[data-mode="${m.code}"] .legend-value`).innerText().catch(() => '')).trim().replace(/ birim$/, '');
      check(cell === fmtSigned1(m.metrics && m.metrics.profit_units), `Seçim Modları (${p}): ${m.code} tablo kârı ${cell} ≠ API ${fmtSigned1(m.metrics && m.metrics.profit_units)}`);
      if (hasTimeline) check(legend === cell, `Seçim Modları (${p}): ${m.code} kâr grafiği lejantı (${legend}) tablodaki "Kâr (birim)" (${cell}) ile aynı olmalı`);
    }
    if (!hasTimeline) {
      console.log(`  i ${p}: sunucu /modes/comparison yanıtında timeline yok; eski seri yeniden tabanlanıyor (geri dönüş).`);
      if (p !== 'all') check((await page.innerText('section[aria-labelledby="profit-title"]')).includes('yeniden tabanlandı'), `Seçim Modları (${p}): timeline yokken geri dönüş notu gösterilmeli`);
    }
  };
  await navTo(page, 'basari');
  await subtabTo(page, 'secim-modlari');
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
  await legendMatchesTable('all');
  await page.click('.seg-btn[data-period="30d"]');
  await waitIdle(page);
  check((await page.getAttribute('.seg-btn[data-period="30d"]', 'aria-pressed')) === 'true', 'dönem seçici 30 gün');
  check((await page.innerText('#success-panel')).includes('Son 30 gün'), 'dönem etiketi güncellenmeli');
  await legendMatchesTable('30d');
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
  // Hızlı dönem değişimi (ui-03): eski istekler iptal edilmeli, paralel yinelenen yükleme olmamalı,
  // son seçilen dönem hatasız görünmeli. Mock hızlı yanıt verdiği için karşılaştırma yanıtları geciktirilir.
  const isComp = (u) => new URL(u).pathname === '/api/v1/recommendations/modes/comparison';
  await page.route(isComp, async (route) => {
    await new Promise((r) => setTimeout(r, 1200));
    await route.continue().catch(() => {});
  });
  const h0 = { ...heavy };
  heavy.maxInflight = heavy.inflight;
  for (const pd of ['7d', '90d', '30d', '90d']) await page.click(`.seg-btn[data-period="${pd}"]`);
  await waitIdle(page);
  await page.unroute(isComp);
  const errs = await page.locator('#success-panel .state-error').count();
  check(errs === 0, `hızlı dönem değişimi sonrası hata kutusu olmamalı (bulunan ${errs})`);
  check((await page.innerText('section[aria-labelledby="cmp-title"] .panel-title')).includes('Son 90 gün'), 'hızlı dönem değişimi: son seçilen dönem (90 gün) gösterilmeli');
  check(heavy.maxInflight <= 3, `hızlı dönem değişimi: aynı anda en fazla 3 ağır istek olmalı (bulunan ${heavy.maxInflight})`);
  console.log(`  i hızlı dönem değişimi: ${heavy.started - h0.started} ağır istek, ${heavy.failed - h0.failed} iptal, en fazla ${heavy.maxInflight} eşzamanlı`);
  await legendMatchesTable('90d');
  expectNoConsoleErrors('başarı merkezi hızlı dönem');
  await subtabTo(page, 'genel');
  check((await page.innerText('#success-panel')).includes('Tüm adaylar'), 'Genel sekmesi temel metrikleri göstermeli');
  await assertNoOverflow(page, 'başarı merkezi genel');
  expectNoConsoleErrors('başarı merkezi genel');
  await subtabTo(page, 'secim-modlari');
  await page.click('.seg-btn[data-period="all"]');
  await waitIdle(page);

  // ---- Canlı Gol Radar
  await navTo(page, 'radar');
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
  // Bildirim anahtarı (--no-write: POST tarayıcıda yerel olarak yanıtlanır, sunucuya gitmez).
  const sw = page.locator('button[role="switch"]');
  const toggleOnce = async (label) => {
    const before = await sw.getAttribute('aria-checked');
    const [resp] = await Promise.all([
      page.waitForResponse((r) => r.url().includes('/api/v1/radar/notifications/permission') && r.request().method() === 'POST'),
      sw.click(),
    ]);
    check(resp.ok(), `Radar (${label}): bildirim izni POST başarılı olmalı (HTTP ${resp.status()})`);
    const ctype = (resp.request().headers()['content-type'] || '').split(';')[0].trim();
    check(ctype === 'application/json', `Radar (${label}): POST Content-Type application/json olmalı (bulunan "${ctype}")`);
    await page.waitForFunction(() => {
      const st = document.querySelector('#main .form-status');
      return st && st.textContent.trim() && st.textContent.trim() !== 'Kaydediliyor…';
    }, null, { timeout: 10000 }).catch(() => {});
    check((await sw.getAttribute('aria-checked')) !== before, `Radar (${label}): bildirim anahtarı durum değiştirmeli`);
    const stClass = (await page.getAttribute('#main .form-status', 'class')) || '';
    const stText = (await page.innerText('#main .form-status').catch(() => '')).trim();
    check(/\bok\b/.test(stClass), `Radar (${label}): durum metni başarı (.form-status.ok) göstermeli; bulunan "${stClass}" "${stText}"`);
    check(!/kaydedilemedi/i.test(stText), `Radar (${label}): başarılı kayıttan sonra "kaydedilemedi" yazmamalı ("${stText}")`);
    await waitIdle(page);
  };
  await toggleOnce('aç/kapat 1');
  await assertNoOverflow(page, 'radar');
  expectNoConsoleErrors('radar');
  await shot(page, vp, '06-radar');
  await toggleOnce('aç/kapat 2 (geri al)');
  expectNoConsoleErrors('radar geri al');

  // ---- Ayarlar
  await navTo(page, 'ayarlar');
  const settings = await getJson(page, 'api/v1/settings');
  check((await page.locator('input[name="default_mode"]').count()) === 3, 'Ayarlar: üç mod seçeneği');
  check((await page.getAttribute('input[name="default_mode"]:checked', 'value')) === settings.default_mode, 'Ayarlar: kayıtlı mod işaretli olmalı');
  check((await page.innerText('#main')).includes('Dengeli Seçimler sistemin varsayılan modudur'), 'Ayarlar: Dengeli varsayılan açıklaması');
  await assertNoOverflow(page, 'ayarlar');
  expectNoConsoleErrors('ayarlar');
  await shot(page, vp, '07-ayarlar');
  {
    // --no-write: PUT tarayıcıda yerel olarak yanıtlanır.
    const original = settings.default_mode;
    const target = original === 'best_value' ? 'highest_probability' : 'best_value';
    const save = async (mode) => {
      await page.check(`input[value="${mode}"]`);
      const [put] = await Promise.all([
        page.waitForResponse((r) => r.url().includes('/api/v1/settings') && r.request().method() === 'PUT'),
        page.click('form button[type="submit"]'),
      ]);
      check(put.ok(), `Ayarlar: PUT /settings başarılı olmalı (HTTP ${put.status()})`);
      const ctype = (put.request().headers()['content-type'] || '').split(';')[0].trim();
      check(ctype === 'application/json', `Ayarlar: PUT Content-Type application/json olmalı (bulunan "${ctype}")`);
      await page.waitForSelector('.form-status.ok', { timeout: 10000 });
    };
    await save(target);
    await navTo(page, 'tahminler');
    check((await page.getAttribute('.mode-switch [aria-selected="true"]', 'data-mode')) === target, 'Ayarlar sonrası Akıllı Tahminler yeni varsayılan modla açılmalı');
    await navTo(page, 'ayarlar');
    await save(original);
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
  check(t.includes('henüz kronolojik doğrulama yapılmadı'), 'doğrulama penceresi yokken "henüz doğrulama yapılmadı" denmeli');
  await shot(page, vp, '09-esik-yok-bos', { fullPage: false });
  await page.unroute(isModes);

  // c2) doğrulama yapıldı ama eşik belirlenemedi: gerçek gerekçe, pencere ve n gösterilmeli
  const reasonText = 'Bağımsız teyit diliminde teorik getiri pozitif değil; güvenilir avantaj belirlenemedi.';
  await page.route(isModes, async (route) => {
    const res = await route.fetch();
    const json = await res.json();
    for (const m of json.modes || []) {
      if (m.code === 'balanced') {
        m.thresholds = {
          status: 'not_established', values: {}, source: 'chronological_validation', reason: reasonText,
          validation_window: { start: '2025-08-16T13:00:00Z', end: '2026-11-16T12:30:00Z' },
          validation_metrics: { n: 177, confirm_n: 63, confirm_roi: -0.0214 },
        };
      }
    }
    await route.fulfill({ response: res, json });
  });
  await page.reload({ waitUntil: 'domcontentloaded' });
  await waitIdle(page);
  const t2 = await page.innerText('#main');
  check(t2.includes('Eşikler belirlenemedi') && t2.includes(reasonText), 'not_established: sunucunun gerekçesi gösterilmeli');
  check(t2.includes('Doğrulama penceresi: 16 Ağu 2025 – 16 Kas 2026') && t2.includes('n = 177') && t2.includes('bağımsız teyit dilimi n = 63'), 'not_established: doğrulama penceresi ve n gösterilmeli');
  check(!t2.includes('henüz kronolojik doğrulama yapılmadı'), 'doğrulama yapılmışken "henüz doğrulama yapılmadı" denmemeli');
  await page.unroute(isModes);
  await page.unroute(isRecs);

  // c3) eski sunucu: rejections yanıtında fixtures yok → filtre ilk sayfadan kurulur ve bu belirtilir;
  //     comparison yanıtında timeline yok → kâr grafiği eski seriyle çizilir ve not düşülür.
  const isRej = (u) => new URL(u).pathname === '/api/v1/recommendations/rejections';
  await page.route(isRej, async (route) => {
    const res = await route.fetch();
    const json = await res.json();
    delete json.fixtures;
    await route.fulfill({ response: res, json });
  });
  await page.goto(base + '#tahminler/balanced', { waitUntil: 'domcontentloaded' });
  await waitIdle(page);
  await page.click('details.rejections > summary');
  await waitIdle(page);
  check((await page.locator('details.rejections .filter-note').count()) === 1, 'fixtures alanı yokken filtre notu gösterilmeli');
  check((await page.locator('details.rejections select option').count()) > 1, 'fixtures alanı yokken filtre ilk sayfadan kurulmalı');
  await page.unroute(isRej);
  const isCmp = (u) => new URL(u).pathname === '/api/v1/recommendations/modes/comparison';
  await page.route(isCmp, async (route) => {
    const res = await route.fetch();
    const json = await res.json();
    for (const m of json.modes || []) delete m.timeline;
    await route.fulfill({ response: res, json });
  });
  await page.goto(base + '#basari/secim-modlari', { waitUntil: 'domcontentloaded' });
  await waitIdle(page);
  await page.click('.seg-btn[data-period="30d"]');
  await waitIdle(page);
  const pt = await page.innerText('section[aria-labelledby="profit-title"]');
  check((await page.locator('section[aria-labelledby="profit-title"] .legend li').count()) === 3 && pt.includes('yeniden tabanlandı'), 'timeline yokken kâr grafiği eski seriye dönmeli ve not düşmeli');
  check((await page.locator('#success-panel .state-error').count()) === 0, 'timeline yokken hata gösterilmemeli');
  await page.click('.seg-btn[data-period="all"]');
  await waitIdle(page);
  await page.unroute(isCmp);

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
  await navTo(p2, 'ayarlar');
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
