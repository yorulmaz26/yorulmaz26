// Prototipten (index.html) karşılaştırma verisi üretir: node Tests/export-fixtures.js
// Playwright gerekir. Çıktı: Tests/Fixtures/levels.json, Tests/Fixtures/sims.json
const { chromium } = require('playwright');
const path = require('path'), fs = require('fs');
(async () => {
  const b = await chromium.launch(); const p = await b.newPage();
  const errs = []; p.on('pageerror', e => errs.push(e.message));
  await p.goto('file://' + path.resolve(__dirname, '../../index.html')); await p.waitForTimeout(300);
  const levels = await p.evaluate(() => {
    const out = [];
    for (let g = 1; g <= TOTAL_LEVELS; g++) {
      const L = makeLevel(g); const blocks = [], layers = [];
      for (let r = 0; r < ROWS; r++) for (let c = 0; c < COLS; c++) { const bl = L.blocks[r][c]; if (bl) blocks.push([r, c, bl.kind, bl.hp]); if (L.layers[r][c]) layers.push([r, c, L.layers[r][c]]); }
      out.push({ g, w: L.w, li: L.li, colors: L.colors, moves: L.moves, primary: L.primary, sig: !!L.sig, hard: !!L.hard, relicTotal: L.relicTotal,
        goals: L.goals.map(x => [x.t, x.c ?? -1, x.n]), blocks, layers, relics: L.relics, locks: L.locks, lotus: L.lotus });
    }
    return out;
  });
  const sims = await p.evaluate(async () => {
    const ost = window.setTimeout; window.setTimeout = (f) => ost(f, 0);
    window.tween = (o, to) => { Object.assign(o, to); return Promise.resolve(); };
    window.winLevel = async () => { G.over = true; }; window.loseLevel = () => {};
    save.sound = false; save.vib = false; closeModal();
    const tick = () => new Promise(r => ost(r, 0));
    const snap = () => { const parts = [];
      for (let r = 0; r < ROWS; r++) for (let c = 0; c < COLS; c++) { const b = G.blocks[r][c], g = G.grid[r][c]; let cell;
        if (b) cell = 'B' + b.kind + b.hp; else if (!g) cell = '.'; else if (g.kind === 'disk') cell = 'D'; else if (g.kind === 'relic') cell = 'R'; else cell = 'g' + g.color + (g.sp || '') + (g.lock ? 'L' : '');
        parts.push(cell + '|' + G.layers[r][c] + (G.lotus[r][c] ? '*' : '')); }
      return parts.join(',') + ` M${G.moves} S${G.score} T${G.turn} G${G.goals.map(gl => gl.t + ':' + (gl.c ?? -1) + ':' + gl.left).join(';')} O${G.over ? 1 : 0}`; };
    const allMoves = () => { const out = [];
      for (let r = 0; r < ROWS; r++) for (let c = 0; c < COLS; c++) for (const [dr, dc] of [[0, 1], [1, 0]]) { const r2 = r + dr, c2 = c + dc; if (!inB(r2, c2)) continue;
        const a = G.grid[r][c], b = G.grid[r2][c2]; if (!a || !b || a.lock || b.lock) continue;
        if ((a.kind === 'disk' && b.kind !== 'relic') || (b.kind === 'disk' && a.kind !== 'relic') || (isSpecial(a) && isSpecial(b))) { out.push([[r, c], [r2, c2]]); continue; }
        G.grid[r][c] = b; G.grid[r2][c2] = a; const ok = matchAt(r, c) || matchAt(r2, c2); G.grid[r][c] = a; G.grid[r2][c2] = b; if (ok) out.push([[r, c], [r2, c2]]); }
      return out; };
    const res = [];
    const sample = []; for (let g = 1; g <= TOTAL_LEVELS; g += 4) sample.push(g);
    for (const g of sample) {
      startLevel(g); for (let i = 0; i < 50 && G.busy; i++) await tick();
      const steps = [snap()], picks = [];
      for (let step = 0; step < 25 && !G.over; step++) {
        const ms = allMoves(); if (!ms.length) break; const k = (step * 7 + g) % ms.length; picks.push(k);
        await trySwap(ms[k][0], ms[k][1]); for (let i = 0; i < 200 && G.busy && !G.over; i++) await tick();
        steps.push(snap());
      }
      res.push({ g, picks, steps });
    }
    return res;
  });
  fs.mkdirSync(path.join(__dirname, 'Fixtures'), { recursive: true });
  fs.writeFileSync(path.join(__dirname, 'Fixtures/levels.json'), JSON.stringify(levels));
  fs.writeFileSync(path.join(__dirname, 'Fixtures/sims.json'), JSON.stringify(sims));
  console.log('levels', levels.length, 'sims', sims.length, 'steps', sims.reduce((a, s) => a + s.steps.length - 1, 0), errs.length ? 'ERR ' + errs[0] : 'ok');
  await b.close();
})();
