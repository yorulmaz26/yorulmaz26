// HTML şablonlarını tam piksel boyutunda PNG/JPEG'e çevirir.
// Kullanım: node render.js işler.json   (işler: [{html: "dosya.html", out: "x.png", w: 1080, h: 1920, transparent: false}])
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
(async () => {
  const jobs = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
  const browser = await chromium.launch();
  for (const j of jobs) {
    const page = await browser.newPage({ viewport: { width: j.w, height: j.h }, deviceScaleFactor: j.scale || 1 });
    const [f,qs]=j.html.split('?'); await page.goto('file://' + path.resolve(f) + (qs?'?'+qs:''), { waitUntil: 'load' });
    await page.evaluate(() => document.fonts.ready);
    await page.waitForTimeout(j.wait || 150);
    const opts = { path: j.out, omitBackground: !!j.transparent, clip: { x: 0, y: 0, width: j.w, height: j.h } };
    if (j.out.endsWith('.jpg')) { opts.type = 'jpeg'; opts.quality = j.quality || 92; }
    await page.screenshot(opts);
    await page.close();
    process.stdout.write('ok ' + j.out + '\n');
  }
  await browser.close();
})().catch(e => { console.error(e); process.exit(1); });
