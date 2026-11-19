// SVG sayfasını Chromium ile şeffaf arka planlı PNG'ye çevirir: node render.js sayfa.html cikti.png genislik yukseklik
const { chromium } = require('playwright');
(async () => {
  const [, , html, out, w, h] = process.argv;
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: +w, height: +h }, deviceScaleFactor: 1 });
  await page.goto('file://' + html);
  await page.waitForTimeout(300);
  await page.screenshot({ path: out, omitBackground: true, clip: { x: 0, y: 0, width: +w, height: +h } });
  await browser.close();
})();
