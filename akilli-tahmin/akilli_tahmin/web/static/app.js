/* Akıllı Tahmin — web arayüzü (vanilla JS, ES2020, derleme adımı yok, harici bağımlılık yok).
 *
 * Güvenlik: API'den gelen hiçbir metin innerHTML ile eklenmez; tüm DOM düğümleri
 * createElement/textContent ile kurulur (bkz. h() ve svgEl()).
 */
(function () {
  'use strict';

  // ------------------------------------------------------------------ sabitler
  const API_BASE = '/api/v1';
  const MODE_ORDER = ['highest_probability', 'best_value', 'balanced'];
  const DEFAULT_MODE = 'balanced';
  const MODE_FALLBACK = {
    highest_probability: { name: 'En Güvenilir Tahminler', short: 'En Güvenilir' },
    best_value: { name: 'En Avantajlı Oranlar', short: 'En Avantajlı' },
    balanced: { name: 'Dengeli Seçimler', short: 'Dengeli' },
  };
  const EMPTY_MESSAGES = {
    balanced: 'Güvenilir ve avantajlı seçim bulunamadı.',
    best_value: 'Güvenilir bir avantaj belirlenemedi; seçim üretilmedi.',
    highest_probability: 'Kalibrasyon ve belirsizlik kontrollerinden geçen güvenilir seçim bulunamadı.',
  };
  const PERIODS = [['7d', 'Son 7 gün'], ['30d', 'Son 30 gün'], ['90d', 'Son 90 gün'], ['all', 'Tümü']];
  const PERIOD_LABEL = Object.fromEntries(PERIODS);
  const RADAR_REFRESH_MS = 30000;
  const API_TIMEOUT_MS = 20000;
  // Başarı Merkezi uçları bütün kayıt havuzunu tarar; gerçek veride 20 sn'yi aşabilir.
  const PERF_TIMEOUT_MS = 45000;
  const REJECTION_PAGE_SIZE = 50;
  const SMALL_SAMPLE = 30;
  // Bu kontrollerin `value` alanı ölçüm değil kayıt kimliğidir (eski sunucu sürümleri); "Değer" olarak gösterilmez.
  const REF_VALUE_CODES = new Set(['audit', 'thresholds_established']);
  const TR_MONTHS = ['Oca', 'Şub', 'Mar', 'Nis', 'May', 'Haz', 'Tem', 'Ağu', 'Eyl', 'Eki', 'Kas', 'Ara'];
  const SVG_NS = 'http://www.w3.org/2000/svg';
  const PAGE_TITLES = {
    tahminler: 'Akıllı Tahminler',
    basari: 'Başarı Merkezi',
    radar: 'Canlı Gol Radar',
    ayarlar: 'Ayarlar',
  };

  const THRESHOLD_LABELS = {
    min_probability_lower: ['Asgari olasılık alt sınırı', 'pct'],
    min_probability: ['Asgari olasılık', 'pct'],
    min_ev_lower: ['Asgari EV alt sınırı', 'pct'],
    min_ev: ['Asgari beklenen değer', 'pct'],
    level: ['Tek yönlü güven düzeyi', 'pct'],
    max_relative_width: ['Azami göreli belirsizlik (%80 aralık / p)', 'dec'],
    max_market_deviation: ['Azami piyasa referansı sapması (logit)', 'dec'],
    max_interval_width: ['Azami belirsizlik aralığı', 'dec'],
    max_disagreement: ['Azami model uyuşmazlığı', 'dec'],
    max_ece: ['Azami kalibrasyon hatası (ECE)', 'dec'],
    min_sample_size: ['Asgari kalibrasyon örneklemi', 'int'],
    max_odds_age_seconds: ['Azami oran yaşı', 'sec'],
    min_robustness_score: ['Asgari dayanıklılık skoru', 'dec'],
    max_shift: ['Azami senaryo sapması', 'dec'],
  };

  const STATUS_TONES = {
    uncertainty: { low: 'good', medium: 'warn', high: 'bad' },
    robustness: { robust: 'good', fragile: 'bad' },
    freshness: { fresh: 'good', stale: 'warn' },
    validity: { valid: 'good', expired: 'bad' },
    audit: { passed: 'good', failed: 'bad', pending: 'warn' },
  };
  const STATUS_LABELS = {
    uncertainty: { low: 'Düşük', medium: 'Orta', high: 'Yüksek' },
    robustness: { robust: 'Dayanıklı', fragile: 'Kırılgan' },
    freshness: { fresh: 'Güncel', stale: 'Eski' },
    validity: { valid: 'Geçerli', expired: 'Süresi doldu' },
    audit: { passed: 'Denetimden geçti', failed: 'Denetimden kaldı', pending: 'Bekliyor' },
  };

  // ------------------------------------------------------------------ durum
  const state = {
    modesData: null,
    modesPromise: null,
    lastMode: null,
    current: null,
    observers: [],
  };

  // ------------------------------------------------------------------ yerel depolama (her zaman try/catch)
  const store = {
    get(key, fallback) {
      try {
        const raw = window.localStorage.getItem('akilli-tahmin:' + key);
        return raw == null ? fallback : JSON.parse(raw);
      } catch (e) {
        return fallback;
      }
    },
    set(key, value) {
      try {
        window.localStorage.setItem('akilli-tahmin:' + key, JSON.stringify(value));
      } catch (e) {
        /* depolama kullanılamıyor (gizli pencere vb.) — sessizce yok say */
      }
    },
  };

  // ------------------------------------------------------------------ DOM yardımcıları
  function appendKids(node, kids) {
    for (const kid of kids) {
      if (kid == null || kid === false || kid === true) continue;
      if (Array.isArray(kid)) appendKids(node, kid);
      else if (kid instanceof Node) node.appendChild(kid);
      else node.appendChild(document.createTextNode(String(kid)));
    }
  }

  function setAttrs(node, props) {
    if (!props) return;
    for (const [key, value] of Object.entries(props)) {
      if (value == null || value === false) continue;
      if (key === 'class') node.setAttribute('class', value);
      else if (key === 'dataset') Object.assign(node.dataset, value);
      else if (key === 'text') node.textContent = String(value);
      else if (key.startsWith('on') && typeof value === 'function') node.addEventListener(key.slice(2), value);
      else if (value === true) node.setAttribute(key, '');
      else node.setAttribute(key, String(value));
    }
  }

  /** Güvenli eleman oluşturucu: metin çocukları her zaman metin düğümü olur. */
  function h(tag, props, ...kids) {
    const node = document.createElement(tag);
    setAttrs(node, props);
    appendKids(node, kids);
    return node;
  }

  function svgEl(tag, props, ...kids) {
    const node = document.createElementNS(SVG_NS, tag);
    setAttrs(node, props);
    appendKids(node, kids);
    return node;
  }

  function safeId(value) {
    return String(value == null ? 'x' : value).replace(/[^A-Za-z0-9_-]/g, '_');
  }

  function modeVar(code) {
    return MODE_ORDER.includes(code) ? 'var(--mode-' + code + ')' : 'var(--mode-baseline)';
  }

  function swatch(code, extraClass) {
    return h('span', { class: 'swatch' + (extraClass ? ' ' + extraClass : ''), style: 'background:' + modeVar(code), 'aria-hidden': 'true' });
  }

  // ------------------------------------------------------------------ biçimlendirme (Türkçe)
  function isNum(v) {
    return typeof v === 'number' && Number.isFinite(v);
  }

  function fmtNum(v, digits) {
    if (!isNum(v)) return '—';
    const d = digits == null ? 2 : digits;
    const fixed = Math.abs(v).toFixed(d);
    const neg = v < 0 && Number(fixed) !== 0;
    const parts = fixed.split('.');
    const intPart = parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, '.');
    return (neg ? '−' : '') + intPart + (parts[1] ? ',' + parts[1] : '');
  }

  function fmtInt(v) {
    return isNum(v) ? fmtNum(Math.round(v), 0) : '—';
  }

  function fmtTrim(v, maxDigits) {
    if (!isNum(v)) return '—';
    if (Number.isInteger(v)) return fmtInt(v);
    let s = fmtNum(v, maxDigits == null ? 3 : maxDigits);
    if (s.includes(',')) s = s.replace(/0+$/, '').replace(/,$/, '');
    return s;
  }

  function fmtPct(v, digits) {
    if (!isNum(v)) return '—';
    const d = digits == null ? 1 : digits;
    const body = fmtNum(Math.abs(v) * 100, d);
    const neg = v < 0 && body !== fmtNum(0, d);
    return (neg ? '−\u2060' : '') + '%' + body;
  }

  function fmtSignedPct(v, digits) {
    if (!isNum(v)) return '—';
    const s = fmtPct(v, digits);
    return v > 0 && s !== fmtPct(0, digits) ? '+\u2060' + s : s;
  }

  function fmtSigned(v, digits) {
    if (!isNum(v)) return '—';
    const s = fmtNum(v, digits);
    return v > 0 && s !== fmtNum(0, digits) ? '+' + s : s;
  }

  function fmtPctRange(lo, hi) {
    if (!isNum(lo) || !isNum(hi)) return '—';
    return fmtPct(lo) + '\u2060–\u2060' + fmtPct(hi);
  }

  function fmtOdds(v) {
    return fmtNum(v, 2);
  }

  function fmtAge(seconds) {
    if (!isNum(seconds)) return '—';
    const s = Math.max(0, seconds);
    if (s < 60) return Math.round(s) + ' sn önce';
    if (s < 3600) return Math.round(s / 60) + ' dk önce';
    if (s < 86400) return Math.round(s / 3600) + ' sa önce';
    return Math.round(s / 86400) + ' gün önce';
  }

  let istanbulFormatter = null;
  function istanbulParts(iso) {
    if (!iso || typeof iso !== 'string') return null;
    const date = new Date(iso);
    if (Number.isNaN(date.getTime())) return null;
    try {
      if (!istanbulFormatter) {
        istanbulFormatter = new Intl.DateTimeFormat('en-GB', {
          timeZone: 'Europe/Istanbul', year: 'numeric', month: 'numeric', day: 'numeric',
          hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23',
        });
      }
      const p = {};
      for (const part of istanbulFormatter.formatToParts(date)) p[part.type] = part.value;
      return { y: Number(p.year), m: Number(p.month), d: Number(p.day), H: pad2(p.hour), M: pad2(p.minute), S: pad2(p.second) };
    } catch (e) {
      // Saat dilimi verisi yoksa: Türkiye 2016'dan beri sabit UTC+3.
      const t = new Date(date.getTime() + 3 * 3600 * 1000);
      return { y: t.getUTCFullYear(), m: t.getUTCMonth() + 1, d: t.getUTCDate(), H: pad2(t.getUTCHours()), M: pad2(t.getUTCMinutes()), S: pad2(t.getUTCSeconds()) };
    }
  }

  function pad2(v) {
    return String(v).padStart(2, '0').slice(-2);
  }

  function currentYear() {
    const p = istanbulParts(new Date().toISOString());
    return p ? p.y : new Date().getFullYear();
  }

  /** "21 Kas 21:00" (İstanbul saati). Farklı yıl ise yıl eklenir. */
  function fmtDateTime(iso) {
    const p = istanbulParts(iso);
    if (!p) return '—';
    const year = p.y !== currentYear() ? ' ' + p.y : '';
    return p.d + ' ' + TR_MONTHS[p.m - 1] + year + ' ' + p.H + ':' + p.M;
  }

  function fmtDate(iso) {
    const p = istanbulParts(iso);
    if (!p) return '—';
    return p.d + ' ' + TR_MONTHS[p.m - 1] + ' ' + p.y;
  }

  function fmtTime(iso) {
    const p = istanbulParts(iso);
    return p ? p.H + ':' + p.M + ':' + p.S : '—';
  }

  function parseDay(str) {
    const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(String(str || ''));
    if (!m) return null;
    return { y: Number(m[1]), m: Number(m[2]), d: Number(m[3]), ms: Date.UTC(Number(m[1]), Number(m[2]) - 1, Number(m[3])) };
  }

  function fmtDay(str, withYear) {
    const p = parseDay(str);
    if (!p) return '—';
    return p.d + ' ' + TR_MONTHS[p.m - 1] + (withYear ? ' ' + p.y : '');
  }

  function teamsLabel(fx) {
    const home = (fx && fx.home) || 'Ev sahibi ?';
    const away = (fx && fx.away) || 'Deplasman ?';
    return home + ' – ' + away;
  }

  function modeName(code) {
    const m = findMode(code);
    return (m && m.name) || (MODE_FALLBACK[code] ? MODE_FALLBACK[code].name : String(code || '—'));
  }

  function modeShort(code) {
    return MODE_FALLBACK[code] ? MODE_FALLBACK[code].short : modeName(code);
  }

  function findMode(code) {
    const list = state.modesData && Array.isArray(state.modesData.modes) ? state.modesData.modes : [];
    return list.find((m) => m && m.code === code) || null;
  }

  function isValidMode(code) {
    return MODE_ORDER.includes(code);
  }

  // ------------------------------------------------------------------ API
  class ApiError extends Error {
    constructor(message, status) {
      super(message);
      this.status = status || 0;
    }
  }

  function httpMessage(status, data) {
    const detail = data && typeof data.detail === 'string' ? data.detail : null;
    if (status === 404) return 'İstenen kayıt bulunamadı' + (detail && detail !== 'Not Found' ? ' (' + detail + ')' : '') + '.';
    if (status === 400 || status === 422) return 'Geçersiz istek' + (detail ? ': ' + detail : '.');
    if (status === 401 || status === 403) return 'Bu işlem için yetkiniz yok (HTTP ' + status + ').';
    if (status === 415) return 'Sunucu istek biçimini kabul etmedi (HTTP 415).';
    if (status >= 500) return 'Sunucu hatası oluştu (HTTP ' + status + '). Lütfen biraz sonra tekrar deneyin.';
    return 'Beklenmeyen sunucu yanıtı (HTTP ' + status + ').';
  }

  /** Çağıranın iptal ettiği istek (dönem değişti, sayfadan çıkıldı vb.); kullanıcıya hata olarak gösterilmez. */
  function isAbortError(err) {
    return !!(err && err.aborted === true);
  }

  /**
   * JSON API çağrısı. options: method, body (JSON'a çevrilir; Content-Type: application/json gönderilir —
   * sunucu durum değiştiren isteklerde başka içerik türünü 415 ile reddeder), timeout (ms),
   * signal (dış AbortSignal: iptal edilirse `aborted: true` işaretli ApiError fırlatılır).
   * Zaman aşımı yanıt gövdesinin okunmasını da kapsar.
   */
  async function api(path, options) {
    const opts = options || {};
    const ctrl = new AbortController();
    const outer = opts.signal || null;
    let timedOut = false;
    const timer = setTimeout(() => {
      timedOut = true;
      ctrl.abort();
    }, opts.timeout || API_TIMEOUT_MS);
    const onOuterAbort = () => ctrl.abort();
    if (outer) {
      if (outer.aborted) ctrl.abort();
      else outer.addEventListener('abort', onOuterAbort);
    }
    const failure = (message, status) => {
      if (timedOut) return new ApiError('Sunucu zamanında yanıt vermedi.', 0);
      if (outer && outer.aborted) {
        const err = new ApiError('İstek iptal edildi.', 0);
        err.aborted = true;
        return err;
      }
      return new ApiError(message, status || 0);
    };
    const headers = { Accept: 'application/json' };
    if (opts.body !== undefined) headers['Content-Type'] = 'application/json';
    try {
      let res;
      try {
        res = await fetch(API_BASE + path, {
          method: opts.method || 'GET',
          headers,
          body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
          signal: ctrl.signal,
          credentials: 'same-origin',
          cache: 'no-store',
        });
      } catch (e) {
        throw failure('Sunucuya ulaşılamadı. Bağlantınızı kontrol edin.', 0);
      }
      let text = '';
      try {
        text = await res.text();
      } catch (e) {
        throw failure('Sunucu yanıtı okunamadı.', res.status);
      }
      let data = null;
      if (text) {
        try {
          data = JSON.parse(text);
        } catch (e) {
          if (res.ok) throw new ApiError('Sunucudan geçersiz yanıt alındı.', res.status);
        }
      }
      if (!res.ok) throw new ApiError(httpMessage(res.status, data), res.status);
      return data;
    } finally {
      clearTimeout(timer);
      if (outer) outer.removeEventListener('abort', onOuterAbort);
    }
  }

  function getModes(force) {
    if (force || !state.modesPromise) {
      state.modesPromise = api('/recommendations/modes').then(
        (data) => {
          state.modesData = data && typeof data === 'object' ? data : null;
          return state.modesData;
        },
        (err) => {
          state.modesPromise = null;
          throw err;
        }
      );
    }
    return state.modesPromise;
  }

  function cachedFetch(cache, key, path) {
    if (!cache.has(key)) {
      const p = api(path).catch((err) => {
        cache.delete(key);
        throw err;
      });
      cache.set(key, p);
    }
    return cache.get(key);
  }

  // ------------------------------------------------------------------ yükleme / hata / boş durumları
  function loadingView(text) {
    return h('div', { class: 'state state-loading', role: 'status' }, h('span', { class: 'spinner', 'aria-hidden': 'true' }), h('span', null, text || 'Yükleniyor…'));
  }

  function errorView(err, retry, title) {
    return h('div', { class: 'state state-error', role: 'alert' },
      h('p', { class: 'state-title' }, title || 'Veriler yüklenemedi.'),
      h('p', { class: 'state-detail' }, (err && err.message) || 'Beklenmeyen bir hata oluştu.'),
      retry ? h('button', { type: 'button', class: 'btn btn-small', onclick: retry }, 'Tekrar dene') : null);
  }

  /**
   * Bir kapsayıcıya veri yükler: yükleniyor → içerik | hata + "Tekrar dene".
   * load(signal) her çalıştırmada yeni bir AbortSignal alır; kapsayıcı yeniden yüklenince önceki
   * çalıştırmanın isteği iptal edilir ve eski yanıtlar (yarış durumu) yok sayılır.
   * keepFrame: yenilemede eski içeriği soluk tut. Dönen run() yeniden yükler, run.abort() durdurur.
   */
  function loadInto(container, load, render, options) {
    const opts = options || {};
    const run = () => {
      if (container._loadCtrl) container._loadCtrl.abort();
      const ctrl = new AbortController();
      const t = {};
      container._loadToken = t;
      container._loadCtrl = ctrl;
      const hadContent = opts.keepFrame && container._hasContent;
      if (hadContent) container.classList.add('is-refreshing');
      else container.replaceChildren(loadingView(opts.loadingText));
      container.setAttribute('aria-busy', 'true');
      Promise.resolve()
        .then(() => load(ctrl.signal))
        .then(
          (data) => {
            if (container._loadToken !== t) return;
            container._loadCtrl = null;
            container.removeAttribute('aria-busy');
            container.classList.remove('is-refreshing');
            container.replaceChildren();
            try {
              render(data, container);
              container._hasContent = true;
            } catch (e) {
              console.error('Akıllı Tahmin: görüntüleme hatası', e);
              container._hasContent = false;
              container.replaceChildren(errorView({ message: 'Veriler görüntülenemedi. Yanıt beklenen biçimde değil.' }, run));
            }
          },
          (err) => {
            if (container._loadToken !== t) return;
            container._loadCtrl = null;
            container.removeAttribute('aria-busy');
            container.classList.remove('is-refreshing');
            if (hadContent) {
              const old = container.querySelector(':scope > .state-error');
              if (old) old.remove();
              container.prepend(errorView(err, run, opts.refreshErrorTitle || 'Yenileme başarısız; son alınan veriler gösteriliyor.'));
            } else {
              container.replaceChildren(errorView(err, run, opts.errorTitle));
            }
          }
        );
    };
    run.abort = () => {
      // Yalnızca kapsayıcı kaldırılırken (sekme/sayfa değişimi) kullanılır.
      container._loadToken = null;
      if (container._loadCtrl) container._loadCtrl.abort();
      container._loadCtrl = null;
      container.removeAttribute('aria-busy');
    };
    run();
    return run;
  }

  function badge(tone, label, extraClass) {
    return h('span', { class: 'badge tone-' + (tone || 'neutral') + (extraClass ? ' ' + extraClass : '') }, label);
  }

  function statusBadge(kind, obj) {
    const status = obj && obj.status != null ? obj.status : obj && obj.level;
    const tone = (STATUS_TONES[kind] && STATUS_TONES[kind][status]) || 'neutral';
    const label = (obj && obj.label) || (STATUS_LABELS[kind] && STATUS_LABELS[kind][status]) || 'Bilinmiyor';
    return badge(tone, label);
  }

  function notice(tone, icon, title, ...body) {
    return h('div', { class: 'notice notice-' + tone },
      h('span', { class: 'notice-icon', 'aria-hidden': 'true' }, icon),
      h('div', { class: 'notice-body' }, title ? h('p', { class: 'notice-title' }, title) : null, body));
  }

  function syntheticBanner() {
    return h('div', { class: 'notice notice-warn', role: 'note' },
      h('span', { class: 'notice-icon', 'aria-hidden': 'true' }, '!'),
      h('div', { class: 'notice-body' },
        h('p', { class: 'notice-title' }, 'Sentetik demo verisi'),
        h('p', null, 'Bu sayfadaki maçlar, oranlar ve sonuçlar sentetik demo verisidir; gerçek maç, oran veya sonuç içermez ve gerçek bahis kararlarında kullanılmamalıdır.')));
  }

  function setSyntheticChip(on) {
    const chip = document.getElementById('synthetic-chip');
    if (chip && on) chip.hidden = false;
  }

  function setDisclaimer(text) {
    const el = document.getElementById('footer-disclaimer');
    if (el && typeof text === 'string' && text.trim()) el.textContent = text;
  }

  // ------------------------------------------------------------------ ipucu (tooltip)
  const tip = {
    el: null,
    init() {
      this.el = document.getElementById('tooltip');
    },
    show(nodes, x, y) {
      if (!this.el) return;
      this.el.replaceChildren();
      appendKids(this.el, [nodes]);
      this.el.hidden = false;
      this.move(x, y);
    },
    move(x, y) {
      if (!this.el || this.el.hidden) return;
      const r = this.el.getBoundingClientRect();
      let left = x + 14;
      let top = y + 14;
      if (left + r.width > window.innerWidth - 8) left = x - r.width - 14;
      if (left < 8) left = 8;
      if (top + r.height > window.innerHeight - 8) top = y - r.height - 14;
      if (top < 8) top = 8;
      this.el.style.left = Math.round(left) + 'px';
      this.el.style.top = Math.round(top) + 'px';
    },
    hide() {
      if (this.el) this.el.hidden = true;
    },
  };

  function attachTip(target, content) {
    target.addEventListener('pointerenter', (e) => tip.show(content(), e.clientX, e.clientY));
    target.addEventListener('pointermove', (e) => tip.move(e.clientX, e.clientY));
    target.addEventListener('pointerleave', () => tip.hide());
    target.addEventListener('focus', () => {
      const r = target.getBoundingClientRect();
      tip.show(content(), r.left + r.width / 2, r.top + r.height / 2);
    });
    target.addEventListener('blur', () => tip.hide());
  }

  function tipRow(color, value, name) {
    return h('div', { class: 'tt-row' },
      color ? h('span', { class: 'line-key', style: 'border-color:' + color, 'aria-hidden': 'true' }) : null,
      h('span', { class: 'tt-value' }, value),
      name ? h('span', { class: 'tt-name' }, name) : null);
  }

  // ------------------------------------------------------------------ duyarlı grafik kabı
  function responsive(container, draw) {
    // Yalnızca bir kez DOM'a bağlanmış ve sonra kaldırılmış kapların gözlemcilerini temizle.
    state.observers = state.observers.filter((o) => {
      if (!o.seen || o.el.isConnected) return true;
      o.disconnect();
      return false;
    });
    let lastWidth = -1;
    const entry = { el: container, seen: false, disconnect: () => {} };
    state.observers.push(entry);
    const render = () => {
      if (container.isConnected) entry.seen = true;
      const w = Math.floor(container.clientWidth);
      if (w <= 0 || w === lastWidth) return;
      lastWidth = w;
      container.replaceChildren();
      try {
        draw(w);
      } catch (e) {
        console.error('Akıllı Tahmin: grafik hatası', e);
        container.replaceChildren(h('div', { class: 'chart-empty' }, 'Grafik çizilemedi.'));
      }
    };
    if (typeof window.ResizeObserver === 'function') {
      const ro = new ResizeObserver(() => window.requestAnimationFrame(render));
      ro.observe(container);
      entry.disconnect = () => ro.disconnect();
    } else {
      const onResize = () => render();
      window.addEventListener('resize', onResize);
      entry.disconnect = () => window.removeEventListener('resize', onResize);
      window.requestAnimationFrame(render);
    }
  }

  function niceTicks(min, max, count) {
    let lo = min;
    let hi = max;
    if (lo === hi) {
      lo -= 1;
      hi += 1;
    }
    const raw = (hi - lo) / (count || 5);
    const mag = Math.pow(10, Math.floor(Math.log10(raw)));
    const norm = raw / mag;
    const step = (norm < 1.5 ? 1 : norm < 3 ? 2 : norm < 7 ? 5 : 10) * mag;
    const start = Math.floor(lo / step) * step;
    const end = Math.ceil(hi / step) * step;
    const ticks = [];
    for (let v = start; v <= end + step / 2; v += step) ticks.push(Number(v.toFixed(10)));
    return { lo: start, hi: end, ticks, step };
  }

  // ------------------------------------------------------------------ grafik: güvenilirlik diyagramı
  function reliabilityChart(container, opts) {
    const bins = (Array.isArray(opts.bins) ? opts.bins : []).filter((b) => b && isNum(b.p_mean) && isNum(b.observed)).sort((a, b) => a.p_mean - b.p_mean);
    if (!bins.length) {
      container.append(h('div', { class: 'chart-empty' }, 'Bu dönem için kalibrasyon verisi yok.'));
      return;
    }
    const color = modeVar(opts.code);
    responsive(container, (width) => {
      const size = Math.max(200, Math.min(width, 340));
      const m = { l: 50, r: 10, t: 8, b: 38 };
      const pw = size - m.l - m.r;
      const ph = size - m.t - m.b;
      const lo = opts.domain[0];
      const hi = opts.domain[1];
      const sx = (v) => m.l + ((Math.min(hi, Math.max(lo, v)) - lo) / (hi - lo)) * pw;
      const sy = (v) => m.t + ph - ((Math.min(hi, Math.max(lo, v)) - lo) / (hi - lo)) * ph;
      const step = hi - lo > 0.5 ? 0.2 : 0.1;
      const svg = svgEl('svg', { width: size, height: size, viewBox: '0 0 ' + size + ' ' + size, role: 'group', 'aria-label': opts.label });
      for (let t = Math.ceil(lo / step - 1e-9) * step; t <= hi + 1e-9; t += step) {
        const v = Number(t.toFixed(4));
        svg.append(
          svgEl('line', { class: 'grid', x1: sx(v), x2: sx(v), y1: m.t, y2: m.t + ph }),
          svgEl('line', { class: 'grid', x1: m.l, x2: m.l + pw, y1: sy(v), y2: sy(v) }),
          svgEl('text', { x: sx(v), y: m.t + ph + 14, 'text-anchor': 'middle' }, fmtPct(v, 0)),
          svgEl('text', { x: m.l - 6, y: sy(v) + 4, 'text-anchor': 'end' }, fmtPct(v, 0))
        );
      }
      svg.append(
        svgEl('line', { class: 'axis', x1: m.l, x2: m.l + pw, y1: m.t + ph, y2: m.t + ph }),
        svgEl('line', { class: 'axis', x1: m.l, x2: m.l, y1: m.t, y2: m.t + ph }),
        svgEl('line', { class: 'diag', x1: sx(lo), y1: sy(lo), x2: sx(hi), y2: sy(hi) }),
        svgEl('text', { class: 'axis-title', x: m.l + pw / 2, y: size - 4, 'text-anchor': 'middle' }, 'Tahmin edilen olasılık'),
        svgEl('text', { class: 'axis-title', x: 12, y: m.t + ph / 2, 'text-anchor': 'middle', transform: 'rotate(-90 12 ' + (m.t + ph / 2) + ')' }, 'Gerçekleşen oran')
      );
      const maxN = Math.max(1, ...bins.map((b) => (isNum(b.n) ? b.n : 1)));
      for (const b of bins) {
        if (isNum(b.ci_low) && isNum(b.ci_high)) {
          svg.append(svgEl('line', { class: 'whisker', x1: sx(b.p_mean), x2: sx(b.p_mean), y1: sy(b.ci_low), y2: sy(b.ci_high), style: 'stroke:' + color }));
        }
      }
      const pts = bins.map((b) => sx(b.p_mean).toFixed(1) + ',' + sy(b.observed).toFixed(1)).join(' ');
      svg.append(svgEl('polyline', { class: 'series-line', points: pts, style: 'stroke:' + color }));
      for (const b of bins) {
        const r = 4 + 3 * Math.sqrt((isNum(b.n) ? b.n : 1) / maxN);
        const dot = svgEl('circle', { class: 'dot', cx: sx(b.p_mean), cy: sy(b.observed), r: r.toFixed(1), style: 'fill:' + color });
        const hit = svgEl('circle', {
          class: 'hit', cx: sx(b.p_mean), cy: sy(b.observed), r: 13, tabindex: '0',
          'aria-label': 'Tahmin ' + fmtPct(b.p_mean) + ', gerçekleşen ' + fmtPct(b.observed) + ', n = ' + fmtInt(b.n),
        });
        attachTip(hit, () => [
          h('div', { class: 'tt-head' }, opts.title || ''),
          tipRow(null, fmtPct(b.observed), 'gerçekleşen'),
          tipRow(null, fmtPct(b.p_mean), 'tahmin (ortalama)'),
          tipRow(null, fmtPctRange(b.ci_low, b.ci_high), '%95 güven aralığı'),
          tipRow(null, fmtInt(b.n), 'seçim'),
        ]);
        svg.append(dot, hit);
      }
      container.append(svg);
    });
  }

  // ------------------------------------------------------------------ grafik: kümülatif kâr çizgisi
  function lineChart(container, opts) {
    const series = (opts.series || []).filter((s) => s.points.length);
    if (!series.length) {
      container.append(h('div', { class: 'chart-empty' }, 'Bu dönem için kâr zaman serisi yok.'));
      return;
    }
    const xsSet = new Set();
    for (const s of series) for (const p of s.points) xsSet.add(p.ms);
    const xs = Array.from(xsSet).sort((a, b) => a - b);
    let minX = xs[0];
    let maxX = xs[xs.length - 1];
    if (minX === maxX) {
      minX -= 86400000;
      maxX += 86400000;
    }
    let minY = 0;
    let maxY = 0;
    for (const s of series) for (const p of s.points) {
      minY = Math.min(minY, p.value);
      maxY = Math.max(maxY, p.value);
    }
    const yt = niceTicks(minY, maxY, 5);
    const spanDays = (maxX - minX) / 86400000;

    const valueAt = (s, ms) => {
      let lo = 0;
      let hi = s.points.length - 1;
      let found = -1;
      while (lo <= hi) {
        const mid = (lo + hi) >> 1;
        if (s.points[mid].ms <= ms) {
          found = mid;
          lo = mid + 1;
        } else hi = mid - 1;
      }
      return found < 0 ? null : s.points[found];
    };

    responsive(container, (width) => {
      const W = width;
      const H = width < 520 ? 230 : 290;
      const m = { l: 50, r: 14, t: 12, b: 30 };
      const pw = W - m.l - m.r;
      const ph = H - m.t - m.b;
      const sx = (ms) => m.l + ((ms - minX) / (maxX - minX)) * pw;
      const sy = (v) => m.t + ph - ((v - yt.lo) / (yt.hi - yt.lo)) * ph;
      const svg = svgEl('svg', { width: W, height: H, viewBox: '0 0 ' + W + ' ' + H, role: 'group', 'aria-label': opts.label });
      for (const t of yt.ticks) {
        svg.append(
          svgEl('line', { class: t === 0 ? 'axis' : 'grid', x1: m.l, x2: m.l + pw, y1: sy(t), y2: sy(t) }),
          svgEl('text', { x: m.l - 6, y: sy(t) + 4, 'text-anchor': 'end' }, fmtSigned(t, yt.step < 1 ? 1 : 0))
        );
      }
      const tickCount = W < 520 ? 3 : 6;
      const used = new Set();
      for (let i = 0; i < tickCount; i++) {
        const ms = minX + ((maxX - minX) * i) / (tickCount - 1);
        const d = new Date(ms);
        const label = spanDays > 200
          ? TR_MONTHS[d.getUTCMonth()] + ' ' + String(d.getUTCFullYear()).slice(2)
          : d.getUTCDate() + ' ' + TR_MONTHS[d.getUTCMonth()];
        if (used.has(label)) continue;
        used.add(label);
        const anchor = i === 0 ? 'start' : i === tickCount - 1 ? 'end' : 'middle';
        svg.append(svgEl('text', { x: sx(ms), y: m.t + ph + 18, 'text-anchor': anchor }, label));
      }
      svg.append(svgEl('line', { class: 'axis', x1: m.l, x2: m.l, y1: m.t, y2: m.t + ph }));
      svg.append(svgEl('text', { class: 'axis-title', x: m.l + 4, y: m.t + 10 }, opts.yLabel || ''));
      for (const s of series) {
        // Kümülatif kâr yalnızca maç günlerinde değişir: basamaklı çizgi (önce yatay, sonra dikey).
        const d = s.points.map((p, i) => (i ? 'H' + sx(p.ms).toFixed(1) + ' V' + sy(p.value).toFixed(1) : 'M' + sx(p.ms).toFixed(1) + ' ' + sy(p.value).toFixed(1))).join(' ');
        svg.append(svgEl('path', { class: 'series-line', d, style: 'stroke:' + modeVar(s.code) }));
      }
      for (const s of series) {
        const last = s.points[s.points.length - 1];
        svg.append(svgEl('circle', { class: 'dot', cx: sx(last.ms), cy: sy(last.value), r: 4.5, style: 'fill:' + modeVar(s.code) }));
      }
      const cross = svgEl('line', { class: 'crosshair', x1: 0, x2: 0, y1: m.t, y2: m.t + ph, visibility: 'hidden' });
      const markers = series.map((s) => svgEl('circle', { class: 'dot', r: 4.5, visibility: 'hidden', style: 'fill:' + modeVar(s.code) }));
      svg.append(cross, ...markers);
      const overlay = svgEl('rect', { class: 'hit', x: m.l, y: m.t, width: pw, height: ph, tabindex: '0', 'aria-label': (opts.label || '') + '. Değerleri görmek için sol ve sağ ok tuşlarını kullanın.' });
      svg.append(overlay);
      let idx = xs.length - 1;
      const showAt = (i, clientX, clientY) => {
        idx = Math.max(0, Math.min(xs.length - 1, i));
        const ms = xs[idx];
        const x = sx(ms);
        cross.setAttribute('x1', x);
        cross.setAttribute('x2', x);
        cross.setAttribute('visibility', 'visible');
        const rows = [h('div', { class: 'tt-head' }, fmtDay(new Date(ms).toISOString(), true))];
        series.forEach((s, k) => {
          const p = valueAt(s, ms);
          const v = p ? p.value : 0;
          markers[k].setAttribute('cx', x);
          markers[k].setAttribute('cy', sy(v));
          markers[k].setAttribute('visibility', 'visible');
          rows.push(tipRow(modeVar(s.code), fmtSigned(v, 1) + ' birim', s.name + (p && isNum(p.n) ? ' · n = ' + fmtInt(p.n) : '')));
        });
        if (clientX == null) {
          const r = overlay.getBoundingClientRect();
          clientX = r.left + (x - m.l);
          clientY = r.top + 20;
        }
        tip.show(rows, clientX, clientY);
      };
      const hideCross = () => {
        cross.setAttribute('visibility', 'hidden');
        markers.forEach((mk) => mk.setAttribute('visibility', 'hidden'));
        tip.hide();
      };
      const nearest = (clientX) => {
        const r = overlay.getBoundingClientRect();
        const ms = minX + ((clientX - r.left) / Math.max(1, r.width)) * (maxX - minX);
        let best = 0;
        let bestD = Infinity;
        for (let i = 0; i < xs.length; i++) {
          const dd = Math.abs(xs[i] - ms);
          if (dd < bestD) {
            bestD = dd;
            best = i;
          }
        }
        return best;
      };
      overlay.addEventListener('pointermove', (e) => showAt(nearest(e.clientX), e.clientX, e.clientY));
      overlay.addEventListener('pointerdown', (e) => showAt(nearest(e.clientX), e.clientX, e.clientY));
      overlay.addEventListener('pointerleave', hideCross);
      overlay.addEventListener('focus', () => showAt(idx));
      overlay.addEventListener('blur', hideCross);
      overlay.addEventListener('keydown', (e) => {
        if (e.key === 'ArrowLeft' || e.key === 'ArrowRight' || e.key === 'Home' || e.key === 'End') {
          e.preventDefault();
          const next = e.key === 'Home' ? 0 : e.key === 'End' ? xs.length - 1 : idx + (e.key === 'ArrowLeft' ? -1 : 1);
          showAt(next);
        } else if (e.key === 'Escape') hideCross();
      });
      container.append(svg);
    });
  }

  // ------------------------------------------------------------------ ortak parçalar
  function checkStatus(c) {
    if (c && c.passed === true) return 'pass';
    if (c && c.passed === false) return 'fail';
    return 'pending';
  }

  function fmtValue(v) {
    if (v == null) return '—';
    if (typeof v === 'boolean') return v ? 'Evet' : 'Hayır';
    if (isNum(v)) return fmtTrim(v, 3);
    return String(v);
  }

  /** passed: true → Geçti, false → Geçmedi, null/eksik → nötr (varsayılan "Değerlendirilmedi"). */
  function checkStatusText(st, nullText) {
    return st === 'pass' ? 'Geçti' : st === 'fail' ? 'Geçmedi' : nullText || 'Değerlendirilmedi';
  }

  function checkIcon(st) {
    return st === 'pass' ? '✓' : st === 'fail' ? '✗' : '–';
  }

  /** "Değer … · Eşik …": yalnızca null olmayan parçalar; kayıt kimliği taşıyan değerler gösterilmez. */
  function checkValuesText(c) {
    if (!c) return null;
    const parts = [];
    if (c.value != null && !REF_VALUE_CODES.has(c.code)) parts.push('Değer ' + fmtValue(c.value));
    if (c.threshold != null) parts.push('Eşik ' + fmtValue(c.threshold));
    return parts.length ? parts.join(' · ') : null;
  }

  function checkList(checks, options) {
    const opts = options || {};
    const list = Array.isArray(checks) ? checks : [];
    if (!list.length) return h('p', { class: 'muted small' }, opts.emptyText || 'Kontrol kaydı yok.');
    return h(opts.ordered ? 'ol' : 'ul', { class: 'check-list' }, list.map((c) => {
      const st = checkStatus(c);
      const stText = checkStatusText(st, opts.nullText);
      const values = checkValuesText(c);
      return h('li', { class: 'check ' + st },
        h('span', { class: 'check-icon', 'aria-hidden': 'true', title: stText }, checkIcon(st)),
        h('span', { class: 'check-label' }, h('span', { class: 'visually-hidden' }, stText + ': '), (c && (c.label || c.code)) || 'Kontrol'),
        st === 'pending' ? h('span', { class: 'check-detail' }, stText + '.') : null,
        c && c.detail ? h('span', { class: 'check-detail' }, c.detail) : null,
        values ? h('span', { class: 'check-values' }, values) : null);
    }));
  }

  /** "8 / 10 koşul sağlandı · 2 değerlendirilmedi" */
  function checkCountText(list, noun) {
    const items = Array.isArray(list) ? list : [];
    const passed = items.filter((c) => c && c.passed === true).length;
    const skipped = items.filter((c) => !c || (c.passed !== true && c.passed !== false)).length;
    return fmtInt(passed) + ' / ' + fmtInt(items.length) + ' ' + (noun || 'koşul') + ' sağlandı' + (skipped ? ' · ' + fmtInt(skipped) + ' değerlendirilmedi' : '');
  }

  function formatThreshold(key, value) {
    const def = THRESHOLD_LABELS[key];
    const kind = def ? def[1] : Number.isInteger(value) ? 'int' : 'dec';
    if (!isNum(value)) return fmtValue(value);
    if (kind === 'pct') return fmtPct(value);
    if (kind === 'int') return fmtInt(value);
    if (kind === 'sec') return fmtInt(value) + ' sn';
    return fmtTrim(value, 3);
  }

  function thresholdLabel(key) {
    const def = THRESHOLD_LABELS[key];
    if (def) return def[0];
    const text = String(key).replace(/_/g, ' ');
    return text.charAt(0).toUpperCase() + text.slice(1);
  }

  function thresholdGrid(values) {
    const entries = values && typeof values === 'object' ? Object.entries(values) : [];
    if (!entries.length) return null;
    return h('div', { class: 'threshold-grid' }, entries.map(([k, v]) => h('div', { class: 'threshold' },
      h('span', { class: 'threshold-label' }, thresholdLabel(k)),
      h('span', { class: 'threshold-value' }, formatThreshold(k, v)))));
  }

  function ciText(obj, fmt) {
    if (!obj || !isNum(obj.ci_low) || !isNum(obj.ci_high)) return null;
    return fmt(obj.ci_low) + ' – ' + fmt(obj.ci_high);
  }

  function withCI(obj, fmt) {
    const main = obj && isNum(obj.value) ? fmt(obj.value) : '—';
    const ci = obj && isNum(obj.ci_low) && isNum(obj.ci_high) ? fmt(obj.ci_low) + '\u2060…\u2060' + fmt(obj.ci_high) : null;
    return [main, ci ? h('span', { class: 'ci', title: '%95 güven aralığı' }, ci) : null];
  }

  function periodSeg(current, onChange) {
    const seg = h('div', { class: 'seg seg-fill', role: 'group', 'aria-label': 'Dönem' });
    for (const [code, label] of PERIODS) {
      seg.append(h('button', {
        type: 'button', class: 'seg-btn', 'aria-pressed': String(code === current), dataset: { period: code },
        onclick: () => {
          for (const b of seg.querySelectorAll('button')) b.setAttribute('aria-pressed', String(b.dataset.period === code));
          onChange(code);
        },
      }, label));
    }
    return seg;
  }

  function tabKeyNav(listEl, selector) {
    listEl.addEventListener('keydown', (e) => {
      const tabs = Array.from(listEl.querySelectorAll(selector));
      const i = tabs.indexOf(document.activeElement);
      if (i < 0) return;
      let next = null;
      if (e.key === 'ArrowRight' || e.key === 'ArrowDown') next = tabs[(i + 1) % tabs.length];
      else if (e.key === 'ArrowLeft' || e.key === 'ArrowUp') next = tabs[(i - 1 + tabs.length) % tabs.length];
      else if (e.key === 'Home') next = tabs[0];
      else if (e.key === 'End') next = tabs[tabs.length - 1];
      if (next) {
        e.preventDefault();
        next.focus();
        next.click();
      }
    });
  }

  // ================================================================== SAYFA: Akıllı Tahminler
  function PredictionsPage(root, sub) {
    let mode = null;
    const tabs = {};
    const switchEl = h('div', { class: 'mode-switch', role: 'tablist', 'aria-label': 'Seçim modu' });
    for (const code of MODE_ORDER) {
      const btn = h('button', {
        type: 'button', role: 'tab', id: 'mode-tab-' + code, class: 'mode-tab', 'aria-selected': 'false',
        'aria-controls': 'mode-panel', tabindex: '-1', style: '--mode-color:' + modeVar(code), dataset: { mode: code },
        onclick: () => selectMode(code, true),
      }, swatch(code), h('span', null, modeShort(code)));
      tabs[code] = btn;
      switchEl.append(btn);
    }
    tabKeyNav(switchEl, '[role="tab"]');

    const modeInfoEl = h('div', { class: 'mode-info-wrap' });
    const resultsEl = h('div', { class: 'page results', 'aria-live': 'polite' });
    const rejectionsEl = h('div');
    const panel = h('div', { id: 'mode-panel', role: 'tabpanel', class: 'page', 'aria-labelledby': 'mode-tab-' + DEFAULT_MODE }, modeInfoEl, resultsEl, rejectionsEl);
    root.append(h('section', { class: 'page', 'aria-label': 'Akıllı Tahminler' },
      h('h1', { class: 'visually-hidden' }, 'Akıllı Tahminler'),
      switchEl,
      h('p', { class: 'mode-hint' }, 'Seçim modları üçüncü bir tahmin modeli değildir: MODEL A ve MODEL B olasılıkları üç modda da aynıdır; yalnızca uygunluk, sıralama ve gerekçe değişir.'),
      panel));

    function selectMode(code, userAction) {
      if (!isValidMode(code)) code = DEFAULT_MODE;
      const changed = code !== mode;
      mode = code;
      if (userAction) state.lastMode = code;
      for (const c of MODE_ORDER) {
        tabs[c].setAttribute('aria-selected', String(c === code));
        tabs[c].setAttribute('tabindex', c === code ? '0' : '-1');
      }
      panel.setAttribute('aria-labelledby', 'mode-tab-' + code);
      const wanted = '#tahminler/' + code;
      if (location.hash !== wanted) {
        try {
          history.replaceState(null, '', wanted);
        } catch (e) {
          location.hash = wanted;
        }
      }
      if (!changed) return;
      renderModeInfo();
      loadInto(resultsEl, () => api('/recommendations?mode=' + encodeURIComponent(code)), (data) => renderResults(data, code), { loadingText: 'Seçimler yükleniyor…' });
      rejectionsEl.replaceChildren(RejectionsSection(code));
    }

    function renderModeInfo() {
      modeInfoEl.replaceChildren();
      if (!state.modesData) {
        if (modeInfoEl._modesError) {
          modeInfoEl.append(h('div', { class: 'state state-error state-inline', role: 'alert' },
            h('p', { class: 'state-title' }, 'Mod açıklamaları ve eşikleri yüklenemedi. Kayıtlı varsayılan mod alınamadığında Dengeli Seçimler kullanılır.'),
            h('p', { class: 'state-detail' }, modeInfoEl._modesError.message || ''),
            h('button', { type: 'button', class: 'btn btn-small', onclick: retryModes }, 'Tekrar dene')));
        } else {
          modeInfoEl.append(loadingView('Mod bilgileri yükleniyor…'));
        }
        return;
      }
      const m = findMode(mode);
      if (!m) {
        modeInfoEl.append(notice('neutral', 'i', modeName(mode), h('p', null, 'Bu mod için açıklama bulunamadı.')));
        return;
      }
      const userDefault = state.modesData.user_default_mode;
      modeInfoEl.append(h('section', { class: 'panel mode-info', 'aria-label': 'Mod açıklaması' },
        h('div', { class: 'mode-info-head' }, swatch(mode), h('h2', null, m.name || modeName(mode)),
          m.is_default ? badge('neutral', 'Sistem varsayılanı', 'no-dot') : null,
          userDefault === mode ? badge('info', 'Varsayılan modunuz', 'no-dot') : null),
        m.description ? h('p', null, m.description) : null,
        h('div', { class: 'mode-meta' },
          m.ranking ? h('span', null, 'Sıralama ölçütü: ', h('strong', null, m.ranking)) : null,
          h('span', null, m.requires_odds ? 'Gerçek ve güncel oran zorunlu' : 'Oran zorunlu değil')),
        thresholdsView(m.thresholds)));
    }

    function thresholdsView(thr) {
      const t = thr && typeof thr === 'object' ? thr : {};
      const win = t.validation_window && typeof t.validation_window === 'object' ? t.validation_window : {};
      const vm = t.validation_metrics && typeof t.validation_metrics === 'object' ? t.validation_metrics : {};
      const hasWindow = !!(win.start || win.end);
      const reason = typeof t.reason === 'string' && t.reason.trim() ? t.reason.trim() : null;
      if (t.status !== 'established') {
        const noSelection = h('p', { class: 'small' }, 'Rastgele sabit eşik dayatılmadığı için bu mod şu anda seçim üretmez.');
        if (!hasWindow) {
          // Doğrulama hiç yapılamadı (ör. yeterli kronolojik veri yok).
          return notice('warn', '!', 'Eşikler henüz belirlenmedi',
            h('p', null, 'Bu modun eşikleri için henüz kronolojik doğrulama yapılmadı.' + (reason ? ' ' + reason : '')),
            noSelection);
        }
        // Doğrulama yapıldı ama güvenilir eşik/avantaj bulunamadı: sunucunun gerçek gerekçesini göster.
        const facts = ['Doğrulama penceresi: ' + fmtDate(win.start) + ' – ' + fmtDate(win.end)];
        if (isNum(vm.n)) facts.push('n = ' + fmtInt(vm.n));
        if (isNum(vm.confirm_n)) {
          facts.push('bağımsız teyit dilimi n = ' + fmtInt(vm.confirm_n) + (isNum(vm.confirm_roi) ? ', teorik ROI ' + fmtSignedPct(vm.confirm_roi) : ''));
        }
        return notice('warn', '!', 'Eşikler belirlenemedi',
          h('p', { class: 'threshold-reason' }, reason || 'Kronolojik doğrulama yapıldı, ancak güvenilir eşikler belirlenemedi.'),
          h('p', { class: 'small' }, facts.join(' · ')),
          noSelection);
      }
      const parts = [];
      if (hasWindow) parts.push('Doğrulama penceresi: ' + fmtDate(win.start) + ' – ' + fmtDate(win.end));
      if (isNum(vm.n)) parts.push('n = ' + fmtInt(vm.n));
      if (isNum(vm.hit_rate_lower)) parts.push('isabet alt sınırı ' + fmtPct(vm.hit_rate_lower));
      if (isNum(vm.roi_lower)) parts.push('ROI alt sınırı ' + fmtSignedPct(vm.roi_lower));
      return h('div', { class: 'mode-thresholds' },
        h('p', { class: 'sub-heading' }, badge('good', 'Eşikler belirlendi'), h('span', { class: 'muted small' }, t.source === 'chronological_validation' || !t.source ? 'Kronolojik doğrulama ile' : 'Kaynak: ' + t.source)),
        thresholdGrid(t.values),
        parts.length ? h('p', { class: 'small muted' }, parts.join(' · ')) : null);
    }

    function retryModes() {
      modeInfoEl._modesError = null;
      renderModeInfo();
      getModes(true).then(() => renderModeInfo(), (err) => {
        modeInfoEl._modesError = err;
        renderModeInfo();
      });
    }

    function renderResults(data, code) {
      const d = data && typeof data === 'object' ? data : {};
      const items = Array.isArray(d.items) ? d.items : [];
      setDisclaimer(d.disclaimer);
      if (d.is_synthetic_data) setSyntheticChip(true);
      const out = [];
      if (d.is_synthetic_data) out.push(syntheticBanner());
      out.push(summaryStrip(d.summary));
      const meta = [];
      if (d.run_id != null) meta.push('Çalıştırma #' + d.run_id);
      if (d.generated_at) meta.push('Üretildi: ' + fmtDateTime(d.generated_at));
      if (d.data_cutoff_at) meta.push('Veri kesimi: ' + fmtDateTime(d.data_cutoff_at));
      if (meta.length) out.push(h('p', { class: 'run-meta' }, meta.join(' · ')));
      const disclaimer = h('div', { class: 'notice notice-neutral disclaimer', role: 'note' },
        h('span', { class: 'notice-icon', 'aria-hidden': 'true' }, 'i'),
        h('div', { class: 'notice-body' }, h('p', null, d.disclaimer || 'Tahminler olasılık tahminidir; kazanç garantisi değildir. 18 yaş altı kullanamaz.')));
      if (!items.length) {
        out.push(h('div', { class: 'empty-state', role: 'status' },
          h('p', { class: 'empty-message' }, d.message || EMPTY_MESSAGES[code] || 'Uygun seçim bulunamadı.'),
          h('p', { class: 'muted' }, 'Güvenilir seçim bulunmayan maçlarda zorla tahmin üretilmez. Elenen adayların gerekçelerini aşağıdaki “Neden elendi?” bölümünde inceleyebilirsiniz.')));
        out.push(disclaimer);
      } else {
        out.push(disclaimer);
        if (d.message) out.push(notice('info', 'i', null, h('p', null, d.message)));
        out.push(h('div', { class: 'cards' }, items.map((it) => recCard(it, code))));
      }
      resultsEl.append(...out);
    }

    function summaryStrip(s) {
      const sum = s && typeof s === 'object' ? s : {};
      const cells = [
        ['fixtures_scanned', 'Taranan maç'],
        ['candidates', 'Aday'],
        ['eligible', 'Uygun aday'],
        ['selected', 'Seçilen'],
        ['fixtures_without_selection', 'Seçim yapılmayan maç'],
      ];
      return h('div', { class: 'summary-strip', role: 'list', 'aria-label': 'Özet' }, cells.map(([k, label]) =>
        h('div', { class: 'summary-item', role: 'listitem' }, h('span', { class: 'summary-value num' }, fmtInt(sum[k])), h('span', { class: 'summary-label' }, label))));
    }

    function update(newSub) {
      if (isValidMode(newSub) && newSub !== mode) selectMode(newSub, true);
    }

    // başlangıç: hash'teki mod > kullanıcının varsayılanı > Dengeli
    const hashMode = isValidMode(sub) ? sub : null;
    if (hashMode) selectMode(hashMode, false);
    else if (state.lastMode) selectMode(state.lastMode, false);
    else renderModeInfo();
    if (!hashMode && !state.lastMode) {
      resultsEl.replaceChildren(loadingView('Varsayılan mod belirleniyor…'));
    }
    getModes().then(
      (data) => {
        if (!mode) selectMode(isValidMode(data && data.user_default_mode) ? data.user_default_mode : DEFAULT_MODE, false);
        else renderModeInfo();
      },
      (err) => {
        modeInfoEl._modesError = err;
        if (!mode) selectMode(DEFAULT_MODE, false);
        else renderModeInfo();
      }
    );

    return { update };
  }

  // ---------- seçim kartı
  function oddsBox(odds, code) {
    if (!odds || typeof odds !== 'object') {
      return h('div', { class: 'odds-box' }, h('span', { class: 'field-label' }, 'Gerçek oran'),
        h('span', { class: 'odds-none' }, code === 'highest_probability' ? 'Geçerli oran yok (bu modda zorunlu değil)' : 'Oran bilgisi yok'));
    }
    const extras = [];
    if (isNum(odds.margin)) extras.push('marj ' + fmtPct(odds.margin));
    if (isNum(odds.fair_probability)) extras.push('marjsız olasılık ' + fmtPct(odds.fair_probability));
    return h('div', { class: 'odds-box' },
      h('span', { class: 'field-label' }, 'Gerçek oran'),
      h('span', { class: 'odds-value' }, fmtOdds(odds.decimal)),
      odds.is_iddaa === true ? h('span', { class: 'badge badge-iddaa' }, 'İddaa') : null,
      h('span', { class: 'odds-meta' }, odds.source_label || odds.source || 'Kaynak belirtilmedi'),
      h('span', { class: 'odds-meta' }, isNum(odds.age_seconds) ? fmtAge(odds.age_seconds) + ' alındı' : (odds.captured_at ? fmtDateTime(odds.captured_at) : 'Zaman bilgisi yok')),
      extras.map((x) => h('span', { class: 'odds-meta' }, x)));
  }

  function modelBox(name, m) {
    const mm = m && typeof m === 'object' ? m : {};
    return h('div', { class: 'model' },
      h('span', { class: 'model-name' }, name + ' olasılığı'),
      h('span', { class: 'model-prob num' }, fmtPct(mm.calibrated)),
      h('span', { class: 'model-sub' }, 'Aralık ' + fmtPctRange(mm.lower, mm.upper)),
      h('span', { class: 'model-raw' }, 'Ham (kalibrasyon öncesi): ' + fmtPct(mm.raw)));
  }

  function methodLabel(method) {
    const map = {
      conservative_min: 'ihtiyatlı yöntem: iki modelden düşük olan',
      model_a: 'MODEL A',
      model_b: 'MODEL B',
      validated_blend: 'kronolojik doğrulanmış birleşim',
    };
    return map[method] || (method ? String(method) : 'yöntem belirtilmedi');
  }

  /**
   * Aynı seçim daha önce kaydedilmişse (first_decision === false) performans ilk karardaki
   * oran ve olasılıkla sayılır; kartta gösterilen güncel değerlerle karıştırılmaması için not düşülür.
   */
  function decisionNote(dec) {
    if (!dec || typeof dec !== 'object' || dec.first_decision !== false) return null;
    const when = dec.selection_timestamp ? fmtDateTime(dec.selection_timestamp) : null;
    const values = 'kayıtlı oran ' + (isNum(dec.recorded_odds) ? fmtOdds(dec.recorded_odds) : 'yok') + ', kayıtlı olasılık ' + fmtPct(dec.recorded_probability);
    return h('p', { class: 'decision-note', role: 'note' },
      'Performans ilk karardaki oran ve olasılıkla' + (when && when !== '—' ? ' (' + when + ' tarihinde)' : '') + ' sayılır: ' + values + '.');
  }

  function fact(label, wide, ...content) {
    return h('div', { class: 'fact' + (wide ? ' fact-wide' : '') }, h('dt', null, label), h('dd', null, content));
  }

  function recCard(it, code) {
    const item = it && typeof it === 'object' ? it : {};
    const fx = item.fixture || {};
    const league = fx.league || {};
    const titleId = 'rec-' + safeId(item.id) + '-' + safeId(code);
    const market = item.market || {};
    const outcome = item.outcome || {};
    const ev = item.expected_value;
    const unc = item.uncertainty || {};
    const rob = item.robustness || {};
    const fresh = item.data_freshness || {};
    const val = item.validity || {};
    const hist = item.history || {};
    const audit = item.audit || {};
    const related = Array.isArray(item.related_selections) ? item.related_selections : [];
    const warnings = Array.isArray(item.warnings) ? item.warnings.filter((w) => w) : [];

    const leagueText = (league.name || 'Lig belirtilmedi') + (league.country ? ' · ' + league.country : '');
    const head = h('header', { class: 'rec-head' },
      h('div', { class: 'rec-head-top' },
        isNum(item.rank) ? h('span', { class: 'rank', 'aria-label': 'Sıra ' + item.rank }, '#' + item.rank) : null,
        h('span', null, h('span', { class: 'visually-hidden' }, 'Lig: '), leagueText),
        h('span', { class: 'kickoff' }, 'Başlangıç saati ', h('time', { datetime: fx.kickoff_at || null }, h('strong', null, fmtDateTime(fx.kickoff_at))))),
      h('h3', { class: 'rec-match', id: titleId }, h('span', { class: 'visually-hidden' }, 'Maç: '), teamsLabel(fx)));

    const pick = h('section', { class: 'rec-pick', 'aria-label': 'Market, seçenek ve oran' },
      h('div', null,
        h('span', { class: 'field-label' }, 'Market'),
        h('div', { class: 'pick-market' }, market.name || market.id || '—'),
        h('span', { class: 'field-label' }, 'Seçenek'),
        h('div', { class: 'pick-outcome' }, outcome.name || outcome.id || '—')),
      oddsBox(item.odds, code));

    const models = h('section', { class: 'models', 'aria-label': 'Model olasılıkları' },
      modelBox('MODEL A', item.model_a),
      modelBox('MODEL B', item.model_b),
      h('p', { class: 'models-note' }, 'Seçimde kullanılan olasılık: ', h('strong', null, fmtPct(item.estimated_probability)), ' (' + methodLabel(item.probability_method) + '). Modeller otomatik olarak ortalanmaz.'));

    let evContent;
    if (!ev || typeof ev !== 'object') {
      evContent = [h('span', { class: 'fact-main' }, '—'), h('span', { class: 'fact-sub' }, item.odds ? 'Beklenen değer hesaplanmadı.' : 'Geçerli oran olmadan beklenen değer hesaplanmaz.')];
    } else {
      evContent = [
        h('span', { class: 'fact-line' }, h('span', { class: 'fact-main' }, fmtSignedPct(ev.point)), h('span', { class: 'fact-sub' }, 'nokta tahmini')),
        h('span', { class: 'fact-sub' }, 'Belirsizlik altında alt sınır: ', h('strong', null, fmtSignedPct(ev.lower))),
        h('span', { class: 'fact-sub' }, 'MODEL A EV ' + fmtSignedPct(ev.model_a) + ' · MODEL B EV ' + fmtSignedPct(ev.model_b)),
      ];
    }

    const histContent = [];
    if (hist.scope) histContent.push(h('span', { class: 'fact-main' }, hist.scope));
    if (!isNum(hist.n) || hist.n === 0) {
      histContent.push(h('span', { class: 'fact-sub' }, 'Bu kapsam için geçmiş kayıt yok.'));
    } else {
      histContent.push(h('span', { class: 'fact-sub' }, 'n = ' + fmtInt(hist.n) + ' · isabet ' + fmtPct(hist.hit_rate) + ' · ROI ' + fmtSignedPct(hist.roi)));
      if (hist.n < SMALL_SAMPLE) histContent.push(h('span', { class: 'small-n' }, 'Küçük örneklem (n = ' + fmtInt(hist.n) + '): geçmiş başarı güvenilir bir gösterge değildir.'));
    }

    const validityReasons = Array.isArray(val.reasons) ? val.reasons.filter((r) => r) : [];
    const facts = h('dl', { class: 'facts' },
      fact('Beklenen değer', false, evContent),
      fact('Belirsizlik seviyesi', false,
        h('span', { class: 'fact-line' }, statusBadge('uncertainty', unc)),
        h('span', { class: 'fact-sub' }, 'Aralık genişliği ' + fmtTrim(unc.interval_width, 3)
          + (isNum(unc.relative_width) ? ' (göreli ' + fmtTrim(unc.relative_width, 3) + ')' : '')
          + ' · model uyuşmazlığı ' + fmtTrim(unc.disagreement, 3)
          + (isNum(unc.market_deviation) ? ' · piyasa sapması ' + fmtTrim(unc.market_deviation, 3) : ''))),
      fact('Tahmin dayanıklılığı', false,
        h('span', { class: 'fact-line' }, statusBadge('robustness', rob)),
        h('span', { class: 'fact-sub' }, 'Skor ' + fmtNum(rob.score, 2) + ' · en büyük olasılık sapması ' + (isNum(rob.max_shift) ? fmtNum(rob.max_shift * 100, 1) + ' puan' : '—'))),
      fact('Veri güncelliği', false,
        h('span', { class: 'fact-line' }, statusBadge('freshness', fresh)),
        h('span', { class: 'fact-sub' }, 'Son sonuç ' + fmtDateTime(fresh.last_result_at) + (isNum(fresh.odds_age_seconds) ? ' · oran ' + fmtAge(fresh.odds_age_seconds) : ''))),
      fact('Tahmin geçerliliği', false,
        h('span', { class: 'fact-line' }, statusBadge('validity', val)),
        h('span', { class: 'fact-sub' }, 'Geçerlilik sonu ' + fmtDateTime(val.valid_until)),
        validityReasons.length ? h('span', { class: 'fact-sub' }, validityReasons.join(' · ')) : null),
      fact('Geçmiş başarı', false, histContent),
      fact('Bağımsız denetim sonucu', false,
        h('span', { class: 'fact-line' }, statusBadge('audit', audit)),
        h('span', { class: 'fact-sub' }, audit.audited_at ? 'Denetim zamanı ' + fmtDateTime(audit.audited_at) : (audit.status === 'pending' ? 'Denetim sürüyor; sonuç kesinleşmedi.' : 'Denetim zamanı bilinmiyor'))),
      fact('Seçim gerekçesi', true, h('p', { class: 'reason' }, item.reason_summary || 'Gerekçe belirtilmedi.')),
      related.length ? fact('Aynı maçta ilişkili seçimler', true,
        h('ul', { class: 'related-list' }, related.map((r) => h('li', null,
          h('span', { class: 'fact-main' }, (r && (r.label || [r.market, r.outcome].filter(Boolean).join(' · '))) || 'İlişkili seçim'),
          h('span', { class: 'fact-sub' }, 'korelasyon ' + fmtNum(r && r.correlation, 2))))),
        h('span', { class: 'fact-sub' }, 'İlişkili seçimler birlikte değerlendirildiğinde riskler birbirinden bağımsız değildir.')) : null,
      warnings.length ? fact('Uyarılar', true, h('ul', { class: 'warning-list' }, warnings.map((w) => h('li', null, String(w))))) : null);

    const explain = h('details', { class: 'explain' }, h('summary', null, 'Neden seçildi?'));
    const explainBody = h('div', { class: 'details-body' });
    explain.append(explainBody);
    let explainStarted = false;
    explain.addEventListener('toggle', () => {
      if (!explain.open || explainStarted) return;
      explainStarted = true;
      loadInto(explainBody, () => api('/recommendations/' + encodeURIComponent(item.id) + '/decision-explanation'), (data) => renderExplanation(explainBody, data, item), { loadingText: 'Karar açıklaması yükleniyor…' });
    });

    return h('article', { class: 'rec-card', 'aria-labelledby': titleId, style: '--mode-color:' + modeVar(code), dataset: { id: String(item.id) } },
      head, pick, decisionNote(item.decision), models, h('section', { class: 'rec-details', 'aria-label': 'Ayrıntılar' }, facts), explain);
  }

  function renderExplanation(body, data, item) {
    const d = data && typeof data === 'object' ? data : {};
    const checks = Array.isArray(d.checks) ? d.checks : [];
    if (d.summary) body.append(h('p', null, d.summary));
    if (d.thresholds && typeof d.thresholds === 'object' && Object.keys(d.thresholds).length) {
      body.append(h('div', null,
        h('p', { class: 'sub-heading' }, 'Uygulanan eşikler', d.config_id != null ? h('span', { class: 'muted small' }, 'yapılandırma #' + d.config_id) : null),
        thresholdGrid(d.thresholds)));
    }
    // Dengeli: numaralı koşullar. passed === null → "Değerlendirilmedi" (nötr; başarısız sayılmaz).
    const conditions = (Array.isArray(d.conditions) ? d.conditions : []).filter((c) => c && typeof c === 'object');
    if (conditions.length) {
      const labelOf = new Map(checks.filter((c) => c && c.code).map((c) => [c.code, c.label || c.code]));
      const rows = conditions.map((c) => ({
        code: c.code,
        label: (isNum(c.no) ? c.no + '. ' : '') + (c.label || 'Koşul'),
        passed: c.passed === true ? true : c.passed === false ? false : null,
        detail: Array.isArray(c.codes) && c.codes.length ? 'Kontroller: ' + c.codes.map((x) => labelOf.get(x) || x).join(', ') : c.detail || null,
      }));
      body.append(h('div', null,
        h('p', { class: 'sub-heading' }, 'Dengeli modun koşulları', h('span', { class: 'muted small' }, checkCountText(rows))),
        checkList(rows, { ordered: true })));
    }
    body.append(h('div', null,
      h('p', { class: 'sub-heading' }, conditions.length ? 'Ayrıntılı kontroller' : 'Seçim koşulları', h('span', { class: 'muted small' }, checkCountText(checks, conditions.length ? 'kontrol' : 'koşul'))),
      checkList(checks)));
    const scenarios = item && item.robustness && Array.isArray(item.robustness.scenarios) ? item.robustness.scenarios : [];
    if (scenarios.length) {
      body.append(h('div', null,
        h('p', { class: 'sub-heading' }, 'Dayanıklılık senaryoları'),
        h('ul', { class: 'related-list' }, scenarios.map((s) => h('li', null, h('span', null, (s && s.name) || 'Senaryo'), h('span', { class: 'fact-sub' }, fmtPct(s && s.probability)))))));
    }
    const audit = d.audit && typeof d.audit === 'object' ? d.audit : {};
    body.append(h('div', null,
      h('p', { class: 'sub-heading' }, 'Bağımsız denetim', statusBadge('audit', audit)),
      checkList(audit.checks, { nullText: audit.status === 'pending' ? 'Bekliyor' : null })));
  }

  // ---------- "Neden elendi?"
  function RejectionsSection(code) {
    const details = h('details', { class: 'rejections' }, h('summary', null, h('span', { class: 'summary-text' }, 'Neden elendi?', h('span', { class: 'summary-hint' }, 'Reddedilen adaylar ve gerekçeleri'))));
    const body = h('div', { class: 'details-body' });
    const filterRow = h('div', { class: 'filter-row' });
    const listWrap = h('div', { class: 'details-body' });
    body.append(h('p', { class: 'small muted' }, 'Bir aday, seçim koşullarından en az biri sağlanmadığında elenir. Gerekçeler her mod için ayrı kaydedilir.'), filterRow, listWrap);
    details.append(body);
    let fixtureId = '';
    let fixtures = null;
    let started = false;

    const select = h('select', { id: 'reject-fixture-' + code, class: 'select' });
    select.addEventListener('change', () => {
      fixtureId = select.value;
      load();
    });

    function load() {
      const q = '/recommendations/rejections?mode=' + encodeURIComponent(code) + '&limit=50' + (fixtureId ? '&fixture_id=' + encodeURIComponent(fixtureId) : '');
      return loadInto(listWrap, () => api(q), render, { loadingText: 'Elenen adaylar yükleniyor…', keepFrame: true });
    }

    function buildFilter(items) {
      if (fixtures) return;
      fixtures = new Map();
      for (const it of items) {
        const fx = it && it.fixture;
        if (fx && fx.id != null && !fixtures.has(String(fx.id))) fixtures.set(String(fx.id), teamsLabel(fx));
      }
      if (!fixtures.size) return;
      select.append(h('option', { value: '' }, 'Tüm maçlar'));
      for (const [id, label] of fixtures) select.append(h('option', { value: id }, label));
      filterRow.append(h('label', { for: select.id }, 'Maç filtresi'), select);
    }

    function render(data) {
      const d = data && typeof data === 'object' ? data : {};
      const items = Array.isArray(d.items) ? d.items : [];
      if (!fixtureId) buildFilter(items);
      const counts = (Array.isArray(d.reason_counts) ? d.reason_counts : []).filter((r) => r && isNum(r.count)).sort((a, b) => b.count - a.count);
      const total = isNum(d.total) ? d.total : items.length;
      const maxCount = Math.max(1, ...counts.map((r) => r.count));
      listWrap.append(h('p', null, h('strong', null, fmtInt(total) + ' aday elendi'),
        items.length < total ? h('span', { class: 'muted small' }, ' · ilk ' + fmtInt(items.length) + ' gösteriliyor') : null));
      if (counts.length) {
        listWrap.append(h('div', null,
          h('p', { class: 'sub-heading' }, 'Eleme nedenleri'),
          h('ul', { class: 'bar-list' }, counts.map((r) => h('li', { class: 'bar-row' },
            h('span', null, r.label || r.code),
            h('span', { class: 'bar-count' }, fmtInt(r.count) + (total ? ' · ' + fmtPct(r.count / total) : '')),
            h('span', { class: 'bar-track', 'aria-hidden': 'true' }, h('span', { class: 'bar-fill', style: 'display:block;width:' + Math.max(2, (r.count / maxCount) * 100).toFixed(1) + '%' }))))),
          h('p', { class: 'small muted' }, 'Bir aday birden fazla koşulda başarısız olabileceği için sayılar toplamı aşabilir.')));
      }
      if (!items.length) {
        listWrap.append(h('p', { class: 'muted' }, 'Gösterilecek elenen aday yok.'));
        return;
      }
      listWrap.append(h('p', { class: 'sub-heading' }, 'Elenen adaylar'), h('ul', { class: 'reject-list' }, items.map((it) => h('li', null, rejectRow(it)))));
    }

    function rejectRow(it) {
      const item = it && typeof it === 'object' ? it : {};
      const fx = item.fixture || {};
      const oddsVal = isNum(item.odds) ? item.odds : item.odds && isNum(item.odds.decimal) ? item.odds.decimal : null;
      const metaParts = ['Olasılık ' + fmtPct(item.estimated_probability), 'Oran ' + (oddsVal == null ? 'yok' : fmtOdds(oddsVal)), 'EV alt sınırı ' + fmtSignedPct(item.ev_lower)];
      const row = h('details', { class: 'reject-item' },
        h('summary', null, h('span', { class: 'reject-sum' },
          h('span', { class: 'reject-title' }, teamsLabel(fx)),
          h('span', { class: 'reject-meta' }, [(item.market && item.market.name) || '—', (item.outcome && item.outcome.name) || '—'].join(' · ') + (fx.kickoff_at ? ' · ' + fmtDateTime(fx.kickoff_at) : '')),
          h('span', { class: 'reject-meta' }, metaParts.join(' · ')),
          item.reason_text ? h('span', { class: 'reject-reason' }, item.reason_text) : null)));
      const rb = h('div', { class: 'details-body' });
      row.append(rb);
      let done = false;
      row.addEventListener('toggle', () => {
        if (!row.open || done) return;
        done = true;
        loadInto(rb, () => api('/recommendations/' + encodeURIComponent(item.id) + '/rejection-reasons'), (data) => {
          const dd = data && typeof data === 'object' ? data : {};
          rb.append(h('p', { class: 'sub-heading' }, 'Başarısız koşullar'), checkList(dd.failed_checks));
          if (dd.reason_text) rb.append(h('p', { class: 'small' }, dd.reason_text));
        }, { loadingText: 'Eleme gerekçeleri yükleniyor…' });
      });
      return row;
    }

    details.addEventListener('toggle', () => {
      if (details.open && !started) {
        started = true;
        load();
      }
    });
    return details;
  }

  // ================================================================== SAYFA: Başarı Merkezi
  function SuccessPage(root, sub) {
    const SUBS = [['secim-modlari', 'Seçim Modları'], ['genel', 'Genel']];
    let current = SUBS.some((s) => s[0] === sub) ? sub : 'secim-modlari';
    let period = store.get('period', 'all');
    if (!PERIOD_LABEL[period]) period = 'all';
    let detailMode = isValidMode(state.lastMode) ? state.lastMode : DEFAULT_MODE;
    const compCache = new Map();
    let perfPromise = null;
    const getPerf = () => {
      if (!perfPromise) perfPromise = api('/performance/selection-modes').catch((e) => {
        perfPromise = null;
        throw e;
      });
      return perfPromise;
    };
    const getComp = (p) => cachedFetch(compCache, p, '/recommendations/modes/comparison?period=' + encodeURIComponent(p));

    const tabList = h('div', { class: 'subtabs', role: 'tablist', 'aria-label': 'Başarı Merkezi bölümleri' });
    const tabBtns = {};
    for (const [code, label] of SUBS) {
      const b = h('button', { type: 'button', role: 'tab', class: 'subtab', id: 'subtab-' + code, 'aria-controls': 'success-panel', onclick: () => go(code) }, label);
      tabBtns[code] = b;
      tabList.append(b);
    }
    tabKeyNav(tabList, '[role="tab"]');
    const content = h('div', { id: 'success-panel', role: 'tabpanel', class: 'page' });
    root.append(h('section', { class: 'page' },
      h('div', { class: 'page-head' }, h('h1', null, 'Başarı Merkezi'),
        h('p', null, 'Tahmin Başarı Merkezi: seçim modlarının geçmiş başarısı aynı zaman aralığı ve aynı maç havuzu üzerinde, her mod için ayrı ve değiştirilemez kayıtlarla karşılaştırılır.')),
      tabList, content));

    function go(code) {
      current = code;
      const wanted = '#basari/' + code;
      if (location.hash !== wanted) {
        try {
          history.replaceState(null, '', wanted);
        } catch (e) {
          /* yok say */
        }
      }
      for (const [c] of SUBS) {
        tabBtns[c].setAttribute('aria-selected', String(c === code));
        tabBtns[c].setAttribute('tabindex', c === code ? '0' : '-1');
      }
      content.setAttribute('aria-labelledby', 'subtab-' + code);
      content.replaceChildren();
      if (code === 'genel') renderGeneral();
      else renderModes();
    }

    // ---- Seçim Modları
    function renderModes() {
      const toolbar = h('div', { class: 'toolbar' }, h('span', { class: 'toolbar-label', id: 'period-label' }, 'Dönem'),
        periodSeg(period, (p) => {
          period = p;
          store.set('period', p);
          reloadComp();
          reloadProfit();
          reloadDetail();
        }));
      const bannerWrap = h('div');
      const compWrap = h('div', { class: 'page' });
      const profitWrap = h('div');
      const seasonWrap = h('div');
      const detailBody = h('div');
      const detailSeg = h('div', { class: 'seg seg-fill', role: 'group', 'aria-label': 'Ayrıntısı gösterilecek mod' });
      for (const code of MODE_ORDER) {
        detailSeg.append(h('button', {
          type: 'button', class: 'seg-btn', 'aria-pressed': String(code === detailMode), dataset: { mode: code },
          onclick: () => {
            detailMode = code;
            for (const b of detailSeg.querySelectorAll('button')) b.setAttribute('aria-pressed', String(b.dataset.mode === code));
            reloadDetail();
          },
        }, swatch(code), modeShort(code)));
      }

      content.append(toolbar, bannerWrap, compWrap,
        h('section', { class: 'panel', 'aria-labelledby': 'profit-title' },
          h('div', { class: 'panel-title' }, h('h2', { class: 'section-title', id: 'profit-title' }, 'Kümülatif teorik kâr'), h('span', { class: 'muted small' }, 'Her seçime 1 birim; dönem başında sıfırdan')),
          profitWrap),
        h('section', { class: 'panel', 'aria-labelledby': 'season-title' },
          h('div', { class: 'panel-title' }, h('h2', { class: 'section-title', id: 'season-title' }, 'Sezonlar arası dayanıklılık'), h('span', { class: 'muted small' }, 'Tüm dönem')),
          seasonWrap),
        h('section', { class: 'panel', 'aria-labelledby': 'detail-title' },
          h('div', { class: 'panel-title' }, h('h2', { class: 'section-title', id: 'detail-title' }, 'Mod ayrıntısı: lig ve market bazlı başarı')),
          detailSeg, detailBody));

      const reloadComp = () => loadInto(compWrap, () => getComp(period), (comp) => renderComparison(compWrap, comp), { keepFrame: true, loadingText: 'Karşılaştırma yükleniyor…' });
      const reloadProfit = () => loadInto(profitWrap, () => Promise.all([getPerf(), getComp(period)]), ([perf, comp]) => renderProfit(profitWrap, perf, comp), { keepFrame: true });
      const reloadDetail = () => loadInto(detailBody, () => api('/recommendations/modes/performance?mode=' + encodeURIComponent(detailMode) + '&period=' + encodeURIComponent(period)), (d) => renderDetail(detailBody, d), { keepFrame: true, loadingText: 'Mod ayrıntısı yükleniyor…' });
      reloadComp();
      reloadProfit();
      reloadDetail();
      loadInto(seasonWrap, getPerf, (perf) => {
        renderSeasons(seasonWrap, perf);
        bannerWrap.replaceChildren();
        if (perf && perf.is_synthetic_data) {
          setSyntheticChip(true);
          bannerWrap.append(syntheticBanner());
        }
        const notes = perf && Array.isArray(perf.notes) ? perf.notes.filter(Boolean) : [];
        if (notes.length) bannerWrap.append(h('div', { class: 'small muted', style: 'margin-top:8px' }, notes.join(' ')));
      });
    }

    function rowsFor(comp) {
      const modes = Array.isArray(comp && comp.modes) ? comp.modes : [];
      const rows = [];
      for (const code of MODE_ORDER) {
        const m = modes.find((x) => x && x.code === code);
        if (m) rows.push({ code, name: modeShort(code), full: m.name || modeName(code), metrics: m.metrics || {} });
      }
      for (const m of modes) {
        if (m && !MODE_ORDER.includes(m.code)) rows.push({ code: m.code, name: m.name || String(m.code), full: m.name, metrics: m.metrics || {} });
      }
      return rows;
    }

    function renderComparison(wrap, comp) {
      const c = comp && typeof comp === 'object' ? comp : {};
      const pool = c.pool || {};
      const notes = Array.isArray(c.notes) ? c.notes.filter(Boolean) : [];
      const win = c.window || {};
      wrap.append(h('div', { class: 'notice notice-info', role: 'note' },
        h('span', { class: 'notice-icon', 'aria-hidden': 'true' }, 'i'),
        h('div', { class: 'notice-body' },
          h('p', { class: 'notice-title' }, 'Karşılaştırma kapsamı — ' + (PERIOD_LABEL[c.period] || PERIOD_LABEL[period])),
          h('p', null, pool.description || 'Üç mod aynı zaman aralığı ve aynı maç havuzu üzerinde karşılaştırılır.'),
          (win.start || win.end) ? h('p', { class: 'small' }, 'Zaman aralığı: ' + fmtDate(win.start) + ' – ' + fmtDate(win.end) + (isNum(pool.fixtures) ? ' · ' + fmtInt(pool.fixtures) + ' maç' : '') + (isNum(pool.candidates) ? ' · ' + fmtInt(pool.candidates) + ' aday' : '')) : null,
          notes.length ? h('ul', null, notes.map((n) => h('li', null, n))) : null,
          h('p', { class: 'small' }, 'Yalnızca seçilen tahminlere bakmak başarıyı olduğundan yüksek gösterebilir; seçim sayısı ve “Tüm adaylar” satırıyla birlikte değerlendirin.'))));

      const rows = rowsFor(c);
      const baseline = c.baseline && typeof c.baseline === 'object' ? c.baseline : null;
      const cols = [
        ['Uygun aday', (m) => fmtInt(m.n_eligible)],
        ['Önerilen seçim', (m) => fmtInt(m.n_selections)],
        ['Seçim yapılmayan maç', (m) => fmtInt(m.n_fixtures_without_selection)],
        ['Seçim sıklığı', (m) => fmtPct(m.selection_rate)],
        ['İsabet oranı', (m) => withCI(m.hit_rate, fmtPct)],
        ['Brier', (m) => fmtNum(m.brier, 3)],
        ['Log Loss', (m) => fmtNum(m.log_loss, 3)],
        ['ECE (kalibrasyon)', (m) => fmtNum(m.ece, 3)],
        ['Ort. oran', (m) => fmtOdds(m.avg_odds)],
        ['Ort. olasılık', (m) => fmtPct(m.avg_probability)],
        ['Ort. EV', (m) => fmtSignedPct(m.avg_ev)],
        ['Teorik birim ROI', (m) => withCI(m.roi, fmtSignedPct)],
        ['Kâr (birim)', (m) => fmtSigned(m.profit_units, 1)],
      ];
      const thead = h('thead', null, h('tr', null, h('th', { scope: 'col', class: 'sticky-col' }, 'Mod'), cols.map(([label]) => h('th', { scope: 'col' }, label))));
      const tbody = h('tbody', null, rows.map((r) => h('tr', null,
        h('th', { scope: 'row', class: 'sticky-col' }, h('span', { class: 'mode-cell' }, swatch(r.code), r.name)),
        cols.map(([, fn]) => h('td', null, fn(r.metrics || {}))))));
      if (baseline) {
        tbody.append(h('tr', { class: 'row-baseline' },
          h('th', { scope: 'row', class: 'sticky-col' }, h('span', { class: 'mode-cell' }, swatch('baseline'), 'Tüm adaylar'), h('span', { class: 'ci' }, baselineNote(baseline.label))),
          cols.map(([, fn]) => h('td', null, fn(baseline.metrics || {})))));
      }
      wrap.append(h('section', { class: 'panel', 'aria-labelledby': 'cmp-title' },
        h('div', { class: 'panel-title' }, h('h2', { class: 'section-title', id: 'cmp-title' }, 'Üç modun karşılaştırması'), h('span', { class: 'muted small' }, PERIOD_LABEL[c.period] || PERIOD_LABEL[period])),
        h('div', { class: 'table-wrap', tabindex: '0', role: 'region', 'aria-label': 'Mod karşılaştırma tablosu (yatay kaydırılabilir)' }, h('table', { class: 'table-compact' }, thead, tbody)),
        h('p', { class: 'table-caption' }, 'Hücrelerdeki alt satır %95 güven aralığıdır (alt…üst). ROI, her seçime 1 birim yatırıldığı varsayımıyla hesaplanan teorik getiridir; kazanç garantisi değildir. Brier, Log Loss ve ECE için düşük değer daha iyidir.')));

      // güvenilirlik diyagramları (küçük çoklu, ortak eksen)
      const charts = rows.map((r) => ({ code: r.code, title: r.full || r.name, short: r.name, metrics: r.metrics || {} }));
      if (baseline) charts.push({ code: 'baseline', title: 'Tüm adaylar', short: 'Tüm adaylar', metrics: baseline.metrics || {} });
      let lo = 1;
      let hi = 0;
      for (const ch of charts) for (const b of (Array.isArray(ch.metrics.calibration_bins) ? ch.metrics.calibration_bins : [])) {
        if (b && isNum(b.p_mean) && isNum(b.observed)) {
          lo = Math.min(lo, b.p_mean, b.observed);
          hi = Math.max(hi, b.p_mean, b.observed);
        }
      }
      if (lo > hi) {
        lo = 0;
        hi = 1;
      }
      const domain = [Math.max(0, Math.floor(lo * 10 - 1e-9) / 10), Math.min(1, Math.ceil(hi * 10 + 1e-9) / 10)];
      if (domain[1] - domain[0] < 0.2) domain[1] = Math.min(1, domain[0] + 0.2);
      const multiples = h('div', { class: 'multiples' });
      for (const ch of charts) {
        const box = h('div', { class: 'chart' });
        multiples.append(h('figure', { class: 'multiple', style: 'margin:0' },
          h('figcaption', { class: 'multiple-title' }, swatch(ch.code), ch.short,
            h('span', { class: 'muted' }, 'ECE ' + fmtNum(ch.metrics.ece, 3) + ' · n = ' + fmtInt(ch.metrics.n_settled))),
          box));
        reliabilityChart(box, { bins: ch.metrics.calibration_bins, code: ch.code, domain, title: ch.title, label: ch.title + ' için güvenilirlik diyagramı' });
      }
      wrap.append(h('section', { class: 'panel', 'aria-labelledby': 'rel-title' },
        h('div', { class: 'panel-title' }, h('h2', { class: 'section-title', id: 'rel-title' }, 'Kalibrasyon: güvenilirlik diyagramı')),
        h('p', { class: 'small muted' }, 'Noktalar tahmin edilen olasılık ile gerçekleşen oranı karşılaştırır; köşegen mükemmel kalibrasyondur. Dikey çizgiler %95 güven aralığı, nokta boyutu seçim sayısıdır.'),
        multiples));
    }

    function renderProfit(wrap, perf, comp) {
      const modes = Array.isArray(perf && perf.modes) ? perf.modes : [];
      const win = (comp && comp.window) || {};
      const startDay = period !== 'all' && win.start ? String(win.start).slice(0, 10) : null;
      const series = [];
      for (const code of MODE_ORDER) {
        const m = modes.find((x) => x && x.code === code);
        if (!m) continue;
        const tl = (Array.isArray(m.timeline) ? m.timeline : []).filter((p) => p && parseDay(p.date) && isNum(p.cumulative_profit));
        let baseValue = 0;
        let baseN = 0;
        let pts = tl;
        if (startDay) {
          const before = tl.filter((p) => p.date <= startDay);
          if (before.length) {
            baseValue = before[before.length - 1].cumulative_profit;
            baseN = isNum(before[before.length - 1].n) ? before[before.length - 1].n : 0;
          }
          pts = tl.filter((p) => p.date > startDay);
        }
        const points = pts.map((p) => ({ ms: parseDay(p.date).ms, value: p.cumulative_profit - baseValue, n: isNum(p.n) ? p.n - baseN : null }));
        const originDay = parseDay(startDay || (win.start ? String(win.start).slice(0, 10) : (tl[0] && tl[0].date)));
        if (points.length && originDay && originDay.ms < points[0].ms) points.unshift({ ms: originDay.ms, value: 0, n: 0 });
        series.push({ code, name: modeShort(code), points });
      }
      const legend = h('ul', { class: 'legend' }, series.map((s) => {
        const last = s.points[s.points.length - 1];
        return h('li', null, h('span', { class: 'line-key', style: 'border-color:' + modeVar(s.code), 'aria-hidden': 'true' }), h('span', null, s.name),
          h('span', { class: 'legend-value' }, last ? fmtSigned(last.value, 1) + ' birim' : 'veri yok'));
      }));
      const chartBox = h('div', { class: 'chart' });
      wrap.append(legend, chartBox);
      const label = 'Kümülatif teorik kâr: ' + series.map((s) => s.name + ' ' + (s.points.length ? fmtSigned(s.points[s.points.length - 1].value, 1) : '—') + ' birim').join(', ');
      lineChart(chartBox, { series, yLabel: 'birim', label });
      wrap.append(h('p', { class: 'small muted', style: 'margin-top:8px' }, 'Geçmiş kâr geleceği garanti etmez. Çizgiler yalnızca o modun kendi kayıtlı seçimlerinden hesaplanır.'));
    }

    function renderSeasons(wrap, perf) {
      const modes = Array.isArray(perf && perf.modes) ? perf.modes : [];
      const ordered = MODE_ORDER.map((c) => modes.find((m) => m && m.code === c)).filter(Boolean);
      if (!ordered.length) {
        wrap.append(h('p', { class: 'muted' }, 'Sezon verisi yok.'));
        return;
      }
      const tbody = h('tbody');
      for (const m of ordered) {
        const st = m.season_stability || {};
        const consistent = st.consistent === true ? badge('good', 'Tutarlı') : st.consistent === false ? badge('warn', 'Tutarsız') : badge('neutral', 'Belirsiz');
        tbody.append(h('tr', { class: 'group-row' }, h('td', { colspan: '6' },
          h('span', { class: 'mode-cell' }, swatch(m.code), modeShort(m.code)), ' ', consistent,
          h('span', { class: 'group-meta' }, fmtInt(st.seasons) + ' sezon · ROI std ' + fmtNum(st.roi_std, 3) + ' · isabet std ' + fmtNum(st.hit_rate_std, 3)))));
        for (const s of (Array.isArray(m.by_season) ? m.by_season : [])) {
          const mt = (s && s.metrics) || {};
          tbody.append(h('tr', null,
            h('td', null, s && s.season ? s.season + ' sezonu' : '—'),
            h('td', null, fmtInt(mt.n_selections)),
            h('td', null, withCI(mt.hit_rate, fmtPct)),
            h('td', null, withCI(mt.roi, fmtSignedPct)),
            h('td', null, fmtSigned(mt.profit_units, 1)),
            h('td', null, fmtNum(mt.ece, 3))));
        }
      }
      wrap.append(h('div', { class: 'table-wrap', tabindex: '0', role: 'region', 'aria-label': 'Sezon tablosu' }, h('table', null,
        h('thead', null, h('tr', null, ['Sezon', 'Seçim', 'İsabet oranı', 'Teorik ROI', 'Kâr (birim)', 'ECE'].map((t) => h('th', { scope: 'col' }, t)))),
        tbody)),
      h('p', { class: 'table-caption', style: 'margin-top:8px' }, 'Alt satırlar %95 güven aralığıdır. Tutarlı: en az iki sezonda sonuçlanmış seçim var ve her sezonun isabet oranı %95 güven aralığı genel isabet oranını içeriyor. Modların başarısı birbirinden bağımsız kaydedilir.'));
    }

    function breakdownTable(title, rows) {
      if (!rows.length) return h('p', { class: 'muted small' }, title + ': veri yok.');
      return h('div', null,
        h('h3', { style: 'margin-bottom:8px' }, title),
        h('div', { class: 'table-wrap', tabindex: '0', role: 'region', 'aria-label': title }, h('table', null,
          h('thead', null, h('tr', null, ['', 'Seçim', 'İsabet oranı', 'Teorik ROI', 'Kâr (birim)', 'Ort. oran'].map((t, i) => h('th', { scope: 'col' }, i === 0 ? title.split(' ')[0] : t)))),
          h('tbody', null, rows.map(([label, mt]) => h('tr', null,
            h('th', { scope: 'row' }, label, isNum(mt.n_settled) && mt.n_settled < SMALL_SAMPLE ? h('span', { class: 'ci' }, 'küçük örneklem') : null),
            h('td', null, fmtInt(mt.n_selections)),
            h('td', null, withCI(mt.hit_rate, fmtPct)),
            h('td', null, withCI(mt.roi, fmtSignedPct)),
            h('td', null, fmtSigned(mt.profit_units, 1)),
            h('td', null, fmtOdds(mt.avg_odds)))))))) ;
    }

    function renderDetail(wrap, data) {
      const d = data && typeof data === 'object' ? data : {};
      const o = d.overall || {};
      wrap.append(h('p', { class: 'sub-heading' }, swatch(d.mode || detailMode), modeName(d.mode || detailMode), h('span', { class: 'muted small' }, PERIOD_LABEL[d.period] || PERIOD_LABEL[period])));
      wrap.append(h('div', { class: 'tiles', style: 'margin-top:8px' },
        tile('Önerilen seçim', fmtInt(o.n_selections), 'sonuçlanan ' + fmtInt(o.n_settled)),
        tile('İsabet oranı', fmtPct(o.hit_rate && o.hit_rate.value), ciText(o.hit_rate, fmtPct) ? 'GA ' + ciText(o.hit_rate, fmtPct) : ''),
        tile('Teorik ROI', fmtSignedPct(o.roi && o.roi.value), ciText(o.roi, fmtSignedPct) ? 'GA ' + ciText(o.roi, fmtSignedPct) : ''),
        tile('Kâr (birim)', fmtSigned(o.profit_units, 1), ''),
        tile('ECE', fmtNum(o.ece, 3), 'Brier ' + fmtNum(o.brier, 3)),
        tile('Ort. oran', fmtOdds(o.avg_odds), 'ort. EV ' + fmtSignedPct(o.avg_ev))));
      const windows = d.windows && typeof d.windows === 'object' ? d.windows : {};
      const winRows = ['7d', '30d', '90d'].filter((k) => windows[k]).map((k) => [PERIOD_LABEL[k], windows[k]]);
      const grid = h('div', { class: 'page', style: 'margin-top:16px' });
      if (winRows.length) grid.append(breakdownTable('Dönem bazlı başarı', winRows));
      grid.append(breakdownTable('Lig bazlı başarı', (Array.isArray(d.by_league) ? d.by_league : []).map((r) => [(r && r.league && r.league.name) || '—', (r && r.metrics) || {}])));
      grid.append(breakdownTable('Market bazlı başarı', (Array.isArray(d.by_market) ? d.by_market : []).map((r) => [(r && (r.name || r.family)) || '—', (r && r.metrics) || {}])));
      wrap.append(grid);
    }

    // ---- Genel
    function renderGeneral() {
      const wrap = h('div', { class: 'page' });
      content.append(wrap);
      loadInto(wrap, getPerf, (perf) => {
        const p = perf && typeof perf === 'object' ? perf : {};
        if (p.is_synthetic_data) {
          setSyntheticChip(true);
          wrap.append(syntheticBanner());
        }
        const base = p.baseline || {};
        const m = base.metrics || {};
        const box = h('div', { class: 'chart' });
        wrap.append(h('section', { class: 'panel', 'aria-labelledby': 'base-title' },
          h('div', { class: 'panel-title' }, h('h2', { class: 'section-title', id: 'base-title' }, 'Genel başarı: tüm adaylar'), h('span', { class: 'muted small' }, base.label || 'Seçim yapılmadan')),
          h('p', { class: 'small muted' }, 'Seçim modlarından bağımsız olarak, taranan tüm adayların MODEL B olasılıklarıyla ölçülen temel başarı. Seçim modlarının sonuçları bu temel çizgiyle birlikte okunmalıdır.'),
          h('div', { class: 'tiles' },
            tile('Aday', fmtInt(m.n_candidates), fmtInt(m.n_fixtures) + ' maç'),
            tile('İsabet oranı', fmtPct(m.hit_rate && m.hit_rate.value), ciText(m.hit_rate, fmtPct) ? 'GA ' + ciText(m.hit_rate, fmtPct) : ''),
            tile('Brier', fmtNum(m.brier, 3), 'Log Loss ' + fmtNum(m.log_loss, 3)),
            tile('ECE', fmtNum(m.ece, 3), 'kalibrasyon hatası'),
            tile('Ort. oran', fmtOdds(m.avg_odds), 'ort. olasılık ' + fmtPct(m.avg_probability)),
            tile('Teorik ROI', fmtSignedPct(m.roi && m.roi.value), ciText(m.roi, fmtSignedPct) ? 'GA ' + ciText(m.roi, fmtSignedPct) : '')),
          h('div', { style: 'max-width:360px' }, box)));
        reliabilityChart(box, { bins: m.calibration_bins, code: 'baseline', domain: [0, 1], title: 'Tüm adaylar', label: 'Tüm adaylar için güvenilirlik diyagramı' });
        const notes = Array.isArray(p.notes) ? p.notes.filter(Boolean) : [];
        if (notes.length) wrap.append(notice('info', 'i', 'Notlar', h('ul', null, notes.map((n) => h('li', null, n)))));
        wrap.append(h('p', { class: 'muted small' }, 'Lig, market ve dönem bazlı ayrıntılar için “Seçim Modları” sekmesine bakın.'));
      });
    }

    go(current);
    return {
      update(newSub) {
        if (SUBS.some((s) => s[0] === newSub) && newSub !== current) go(newSub);
      },
    };
  }

  /** "Tüm adaylar (seçim yapılmadan, MODEL B)" → "seçim yapılmadan, MODEL B" (başlık zaten "Tüm adaylar"). */
  function baselineNote(label) {
    const text = typeof label === 'string' && label.trim() ? label.trim() : 'seçim yapılmadan';
    const rest = text.replace(/^Tüm adaylar\s*/i, '').replace(/^\((.*)\)$/, '$1').trim();
    return rest || text;
  }

  function tile(label, value, subText) {
    return h('div', { class: 'tile' }, h('span', { class: 'tile-label' }, label), h('span', { class: 'tile-value' }, value), subText ? h('span', { class: 'tile-sub' }, subText) : null);
  }

  // ================================================================== SAYFA: Canlı Gol Radar
  function RadarPage(root) {
    const configWrap = h('div');
    const notifyWrap = h('div');
    const liveWrap = h('div');
    const alertsWrap = h('div');
    let liveNames = new Map();
    let livePromise = null;
    let lastRefresh = 0;
    let granted = store.get('radarNotifications', false) === true;

    root.append(h('section', { class: 'page' },
      h('div', { class: 'page-head' }, h('h1', null, 'Canlı Gol Radar'),
        h('p', null, 'Canlı maçlarda kalan normal sürede gol olasılığını izler. Radar, seçim modlarından tamamen bağımsızdır.')),
      h('div', { class: 'radar-grid' },
        h('section', { class: 'panel', 'aria-labelledby': 'radar-cond-title' }, h('h2', { class: 'section-title', id: 'radar-cond-title' }, 'Zorunlu çalışma koşulları'), configWrap),
        h('section', { class: 'panel', 'aria-labelledby': 'radar-notify-title' }, h('h2', { class: 'section-title', id: 'radar-notify-title' }, 'Bildirimler'), notifyWrap)),
      h('section', { class: 'panel', 'aria-labelledby': 'radar-live-title' },
        h('div', { class: 'panel-title' }, h('h2', { class: 'section-title', id: 'radar-live-title' }, 'Canlı maçlar'),
          h('button', { type: 'button', class: 'btn btn-small', onclick: () => refresh() }, 'Şimdi yenile')),
        liveWrap),
      h('section', { class: 'panel', 'aria-labelledby': 'radar-alerts-title' }, h('h2', { class: 'section-title', id: 'radar-alerts-title' }, 'Alarm geçmişi'), alertsWrap)));

    loadInto(configWrap, () => api('/radar/config'), (cfg) => renderConfig(cfg), { loadingText: 'Radar koşulları yükleniyor…' });
    renderNotify();
    const loadLive = () => {
      livePromise = api('/radar/live');
      return livePromise;
    };
    // Alarm listesi yalnızca fixture_id içerir; maç adları canlı listeden eşlenir.
    const loadAlerts = () => Promise.all([api('/radar/alerts?limit=50'), (livePromise || Promise.resolve(null)).catch(() => null)]).then(([alerts, live]) => {
      if (live) setLiveNames(live);
      return alerts;
    });
    const reloadLive = loadInto(liveWrap, loadLive, renderLive, { keepFrame: true, loadingText: 'Canlı maçlar değerlendiriliyor…' });
    const reloadAlerts = loadInto(alertsWrap, loadAlerts, renderAlerts, { keepFrame: true, loadingText: 'Alarmlar yükleniyor…' });
    lastRefresh = Date.now();

    function refresh() {
      lastRefresh = Date.now();
      reloadLive();
      reloadAlerts();
    }
    const timer = window.setInterval(() => {
      if (document.visibilityState === 'visible') refresh();
    }, RADAR_REFRESH_MS);
    const onVisible = () => {
      if (document.visibilityState === 'visible' && Date.now() - lastRefresh >= RADAR_REFRESH_MS) refresh();
    };
    document.addEventListener('visibilitychange', onVisible);

    function renderConfig(cfg) {
      const c = cfg && typeof cfg === 'object' ? cfg : {};
      const req = (flag) => (flag === false ? 'Gerekli değil' : 'Zorunlu');
      const rows = [
        ['MODEL A olasılığı', isNum(c.model_a_min_probability) ? '> ' + fmtPct(c.model_a_min_probability, 0) : '—'],
        ['MODEL B olasılığı', isNum(c.model_b_min_probability) ? '> ' + fmtPct(c.model_b_min_probability, 0) : '—'],
        ['Normal sürede kalan süre', isNum(c.min_remaining_minutes) ? '≥ ' + fmtInt(c.min_remaining_minutes) + ' dk' : '—'],
        ['Güncel canlı veri', isNum(c.live_data_max_age_seconds) ? '≤ ' + fmtInt(c.live_data_max_age_seconds) + ' sn' : 'Zorunlu'],
        ['Gerekli güncel oran', isNum(c.live_odds_max_age_seconds) ? '≤ ' + fmtInt(c.live_odds_max_age_seconds) + ' sn' : 'Zorunlu'],
        ['Yeterli nadir olay kalibrasyonu', req(c.requires_rare_event_calibration)],
        ['Tahmin Kalite Kapısı', req(c.requires_quality_gate)],
        ['Bildirim izni', req(c.requires_notification_permission)],
        ['Mükerrer alarm engeli', c.duplicate_alarm_block === false ? 'Kapalı' : 'Açık'],
      ];
      configWrap.append(
        h('p', null, 'Hedef: ', h('strong', null, c.target || 'Kalan normal sürede en az bir gol')),
        h('p', { class: 'small muted' }, 'Alarm yalnızca aşağıdaki koşulların tamamı aynı anda sağlandığında üretilir.'),
        h('ul', { class: 'cond-list' }, rows.map(([label, value]) => h('li', null, h('span', null, label), h('span', { class: 'cond-value' }, value)))),
        notice('info', 'i', 'Seçim modları radar eşiklerini değiştiremez',
          h('p', null, 'Dengeli, En Güvenilir veya En Avantajlı modu seçmek Canlı Gol Radar eşiklerini gevşetmez veya değiştirmez.'),
          c.note ? h('p', { class: 'small' }, c.note) : null));
    }

    function renderNotify() {
      const supported = 'Notification' in window;
      const status = h('p', { class: 'form-status', role: 'status', 'aria-live': 'polite' });
      const btn = h('button', { type: 'button', class: 'switch', role: 'switch', 'aria-checked': String(granted), 'aria-describedby': 'notify-desc' },
        h('span', { class: 'switch-track', 'aria-hidden': 'true' }), h('span', { class: 'switch-label' }, granted ? 'Radar bildirimleri açık' : 'Radar bildirimleri kapalı'));
      btn.addEventListener('click', () => toggle(btn, status));
      const browserNote = h('p', { class: 'small browser-note' });
      updateBrowserNote(browserNote);
      notifyWrap.replaceChildren();
      appendKids(notifyWrap, [
        h('div', { class: 'switch-row' }, btn),
        h('p', { id: 'notify-desc', class: 'small muted' }, 'Bildirim izni radar alarmının zorunlu koşullarından biridir. Açtığınızda tarayıcı da bildirim izni isteyebilir.'),
        supported ? null : h('p', { class: 'small muted' }, 'Bu tarayıcı masaüstü bildirimlerini desteklemiyor; alarmlar yalnızca bu sayfada görünür.'),
        browserNote,
        status,
      ]);
    }

    function updateBrowserNote(el) {
      let text = '';
      try {
        if ('Notification' in window && window.Notification.permission === 'denied') {
          text = 'Tarayıcı bildirimleri bu site için engellenmiş; tarayıcı ayarlarından izin verebilirsiniz.';
        }
      } catch (e) {
        text = '';
      }
      el.textContent = text;
      el.hidden = !text;
    }

    function requestBrowserPermission() {
      return new Promise((resolve) => {
        try {
          const maybe = window.Notification.requestPermission((r) => resolve(r));
          if (maybe && typeof maybe.then === 'function') maybe.then(resolve, () => resolve('denied'));
        } catch (e) {
          resolve('error');
        }
      });
    }

    async function toggle(btn, status) {
      const want = !granted;
      btn.disabled = true;
      status.className = 'form-status';
      status.textContent = 'Kaydediliyor…';
      let value = want;
      let note = '';
      if (want) {
        if ('Notification' in window && typeof window.Notification.requestPermission === 'function') {
          const res = await requestBrowserPermission();
          if (res !== 'granted') {
            value = false;
            note = res === 'error' ? ' Tarayıcı bildirim izni istenemedi.' : ' Tarayıcı bildirim iznini vermedi; radar alarmları bildirim olarak gönderilemez.';
          }
        } else {
          note = ' Bu tarayıcı bildirimleri desteklemiyor; alarmlar yalnızca bu sayfada görünür.';
        }
      }
      try {
        const res = await api('/radar/notifications/permission', { method: 'POST', body: { granted: value } });
        granted = !!(res && res.granted === true);
        store.set('radarNotifications', granted);
        btn.setAttribute('aria-checked', String(granted));
        const lbl = btn.querySelector('.switch-label');
        if (lbl) lbl.textContent = granted ? 'Radar bildirimleri açık' : 'Radar bildirimleri kapalı';
        status.className = 'form-status ' + (granted || !want ? 'ok' : 'err');
        status.textContent = (granted ? 'Bildirim izni kaydedildi.' : 'Bildirimler kapalı olarak kaydedildi.') + note;
        refresh();
        const note = notifyWrap.querySelector('.browser-note');
        if (note) updateBrowserNote(note);
      } catch (e) {
        status.className = 'form-status err';
        status.textContent = 'İzin kaydedilemedi: ' + ((e && e.message) || 'bilinmeyen hata');
      } finally {
        btn.disabled = false;
      }
    }

    function setLiveNames(live) {
      const items = live && Array.isArray(live.items) ? live.items : [];
      for (const it of items) if (it && it.fixture && it.fixture.id != null) liveNames.set(String(it.fixture.id), teamsLabel(it.fixture));
    }

    function renderLive(data) {
      const d = data && typeof data === 'object' ? data : {};
      const items = Array.isArray(d.items) ? d.items : [];
      setLiveNames(d);
      liveWrap.append(h('p', { class: 'refresh-meta' }, 'Son değerlendirme ' + fmtTime(d.evaluated_at) + ' · Sayfa açıkken 30 saniyede bir otomatik yenilenir.'));
      if (!items.length) {
        liveWrap.append(h('p', { class: 'muted', style: 'margin-top:8px' }, 'Şu anda değerlendirilen canlı maç yok.'));
        return;
      }
      liveWrap.append(h('ul', { class: 'live-list', style: 'margin-top:10px' }, items.map((it) => h('li', null, liveCard(it)))));
    }

    function liveCard(it) {
      const item = it && typeof it === 'object' ? it : {};
      const fx = item.fixture || {};
      const conds = Array.isArray(item.conditions) ? item.conditions : [];
      const passed = conds.filter((c) => c && c.passed === true).length;
      const odds = item.odds && typeof item.odds === 'object' ? item.odds : null;
      const alarm = item.alarm === true;
      const condDetails = h('details', null, h('summary', { class: 'small' }, 'Koşul ayrıntıları'), h('div', { class: 'details-body' }, checkList(conds)));
      return h('article', { class: 'live-card' + (alarm ? ' alarm-on' : ''), 'aria-label': teamsLabel(fx) },
        h('div', { class: 'live-head' },
          h('div', null, h('h3', null, teamsLabel(fx)), h('p', { class: 'small muted' }, (fx.league && fx.league.name) || '')),
          h('div', null,
            h('div', { class: 'live-score' }, item.score || '—'),
            h('div', { class: 'live-minute' }, (isNum(item.minute) ? item.minute + '. dk' : '—') + (isNum(item.remaining_minutes) ? ' · ' + item.remaining_minutes + ' dk kaldı' : '')))),
        h('div', { class: 'live-probs' },
          h('span', null, 'MODEL A ', h('strong', null, fmtPct(item.model_a_probability))),
          h('span', null, 'MODEL B ', h('strong', null, fmtPct(item.model_b_probability))),
          h('span', null, 'Oran ', h('strong', null, odds ? fmtOdds(odds.decimal) : 'yok'),
            odds ? h('span', { class: 'muted small' }, ' · ' + (odds.source_label || 'kaynak?') + ' · ' + fmtAge(odds.age_seconds)) : null)),
        h('div', { class: 'fact-line' },
          alarm ? badge('info', 'Alarm verildi' + (item.alert_id != null ? ' · #' + item.alert_id : ''), 'alarm-badge') : badge('neutral', 'Alarm yok', 'alarm-badge'),
          h('span', { class: 'small muted' }, fmtInt(passed) + ' / ' + fmtInt(conds.length) + ' koşul sağlandı')),
        item.blocked_reason ? h('p', { class: 'small', style: 'color:var(--warn-ink)' }, item.blocked_reason) : null,
        h('ul', { class: 'chip-list', 'aria-label': 'Radar koşulları' }, conds.map((c) => {
          const st = checkStatus(c);
          return h('li', { class: 'chip ' + st, title: (c && c.detail) || '' },
            h('span', { class: 'chip-icon', 'aria-hidden': 'true' }, st === 'pass' ? '✓' : st === 'fail' ? '✗' : '…'),
            h('span', { class: 'visually-hidden' }, st === 'pass' ? 'Geçti: ' : st === 'fail' ? 'Geçmedi: ' : 'Bekliyor: '),
            (c && (c.label || c.code)) || 'Koşul');
        })),
        condDetails);
    }

    function renderAlerts(data) {
      const items = Array.isArray(data && data.items) ? data.items : [];
      if (!items.length) {
        alertsWrap.append(h('p', { class: 'muted' }, 'Henüz alarm yok.'));
        return;
      }
      alertsWrap.append(h('div', { class: 'table-wrap', tabindex: '0', role: 'region', 'aria-label': 'Alarm geçmişi tablosu' }, h('table', null,
        h('thead', null, h('tr', null, ['Zaman', 'Maç', 'Dakika', 'MODEL A', 'MODEL B', 'Bildirim'].map((t) => h('th', { scope: 'col', class: t === 'Maç' ? 'text-left' : null }, t)))),
        h('tbody', null, items.map((a) => {
          const al = a && typeof a === 'object' ? a : {};
          return h('tr', null,
            h('td', null, fmtDateTime(al.created_at)),
            h('td', { class: 'text-left' }, liveNames.get(String(al.fixture_id)) || (al.fixture_id != null ? 'Maç #' + al.fixture_id : '—')),
            h('td', null, isNum(al.minute) ? al.minute + '. dk' : '—'),
            h('td', null, fmtPct(al.model_a_probability)),
            h('td', null, fmtPct(al.model_b_probability)),
            h('td', null, al.notified === true ? badge('good', 'Gönderildi') : badge('neutral', 'Gönderilmedi')));
        })))));
    }

    return {
      destroy() {
        window.clearInterval(timer);
        document.removeEventListener('visibilitychange', onVisible);
      },
    };
  }

  // ================================================================== SAYFA: Ayarlar
  function SettingsPage(root) {
    const formWrap = h('div');
    root.append(h('section', { class: 'page' },
      h('div', { class: 'page-head' }, h('h1', null, 'Ayarlar')),
      h('section', { class: 'panel', 'aria-labelledby': 'settings-mode-title' },
        h('h2', { class: 'section-title', id: 'settings-mode-title' }, 'Varsayılan seçim modu'),
        notice('neutral', 'i', 'Dengeli Seçimler sistemin varsayılan modudur',
          h('p', null, 'Bir değişiklik yapmazsanız Akıllı Tahminler sayfası her açılışta Dengeli Seçimler ile başlar. Burada başka bir mod seçerseniz sayfa o modla açılır; modlar arasında istediğiniz zaman geçiş yapabilirsiniz.'),
          h('p', { class: 'small' }, 'Mod seçimi MODEL A ve MODEL B olasılıklarını, geçmiş seçim kayıtlarını ve Canlı Gol Radar eşiklerini değiştirmez.')),
        formWrap)));

    loadInto(formWrap, () => Promise.all([api('/settings'), getModes().catch(() => null)]), ([settings]) => renderForm(settings), { loadingText: 'Ayarlar yükleniyor…' });

    function renderForm(settings) {
      const s = settings && typeof settings === 'object' ? settings : {};
      const currentMode = isValidMode(s.default_mode) ? s.default_mode : DEFAULT_MODE;
      const status = h('p', { class: 'form-status', role: 'status', 'aria-live': 'polite' });
      const fieldset = h('fieldset', { class: 'radio-list' }, h('legend', { class: 'visually-hidden' }, 'Varsayılan seçim modu'));
      for (const code of MODE_ORDER) {
        const m = findMode(code) || {};
        const id = 'default-mode-' + code;
        fieldset.append(h('label', { class: 'radio-card', for: id },
          h('input', { type: 'radio', name: 'default_mode', id, value: code, checked: code === currentMode }),
          h('span', { class: 'radio-title' }, swatch(code), m.name || modeName(code), code === DEFAULT_MODE ? badge('neutral', 'Sistem varsayılanı', 'no-dot') : null,
            code === currentMode ? badge('info', 'Kayıtlı tercih', 'no-dot') : null),
          h('span', { class: 'radio-desc' }, m.description || '')));
      }
      const saveBtn = h('button', { type: 'submit', class: 'btn btn-primary' }, 'Kaydet');
      const resetBtn = h('button', { type: 'button', class: 'btn' }, 'Dengeli’ye dön');
      const form = h('form', { class: 'page', novalidate: true }, fieldset, h('div', { class: 'form-actions' }, saveBtn, resetBtn, status));
      if (s.user_id) form.append(h('p', { class: 'small muted' }, 'Kullanıcı: ' + s.user_id));
      resetBtn.addEventListener('click', () => {
        const r = form.querySelector('input[value="' + DEFAULT_MODE + '"]');
        if (r) r.checked = true;
        save(DEFAULT_MODE);
      });
      form.addEventListener('submit', (e) => {
        e.preventDefault();
        const chosen = form.querySelector('input[name="default_mode"]:checked');
        save(chosen ? chosen.value : DEFAULT_MODE);
      });

      async function save(code) {
        saveBtn.disabled = true;
        resetBtn.disabled = true;
        status.className = 'form-status';
        status.textContent = 'Kaydediliyor…';
        try {
          const res = await api('/settings', { method: 'PUT', body: { default_mode: code } });
          const saved = res && isValidMode(res.default_mode) ? res.default_mode : code;
          if (state.modesData) state.modesData.user_default_mode = saved;
          state.lastMode = null;
          status.className = 'form-status ok';
          status.textContent = 'Varsayılan mod kaydedildi: ' + modeName(saved) + '.';
          for (const lbl of fieldset.querySelectorAll('.radio-card')) {
            const input = lbl.querySelector('input');
            const tag = lbl.querySelector('.tone-info');
            if (tag) tag.remove();
            if (input && input.value === saved) lbl.querySelector('.radio-title').append(badge('info', 'Kayıtlı tercih', 'no-dot'));
          }
        } catch (e) {
          status.className = 'form-status err';
          status.textContent = 'Ayar kaydedilemedi: ' + ((e && e.message) || 'bilinmeyen hata');
        } finally {
          saveBtn.disabled = false;
          resetBtn.disabled = false;
        }
      }
      formWrap.append(form);
    }
    return {};
  }

  // ================================================================== yönlendirme
  const PAGES = { tahminler: PredictionsPage, basari: SuccessPage, radar: RadarPage, ayarlar: SettingsPage };

  function parseHash() {
    let raw = '';
    try {
      raw = decodeURIComponent(location.hash.replace(/^#\/?/, ''));
    } catch (e) {
      raw = '';
    }
    const [page, sub] = raw.split('/');
    return { page: PAGES[page] ? page : null, sub: sub || null };
  }

  function updateNav(name) {
    for (const btn of document.querySelectorAll('.nav-tab')) {
      if (btn.dataset.page === name) btn.setAttribute('aria-current', 'page');
      else btn.removeAttribute('aria-current');
    }
    document.title = (PAGE_TITLES[name] || 'Akıllı Tahmin') + ' · Akıllı Tahmin';
  }

  function route(focusMain) {
    const parsed = parseHash();
    const name = parsed.page || 'tahminler';
    if (state.current && state.current.name === name) {
      if (state.current.ctl && typeof state.current.ctl.update === 'function') state.current.ctl.update(parsed.sub);
      updateNav(name);
      return;
    }
    if (state.current && state.current.ctl && typeof state.current.ctl.destroy === 'function') state.current.ctl.destroy();
    for (const o of state.observers) o.disconnect();
    state.observers = [];
    tip.hide();
    const main = document.getElementById('main');
    main.replaceChildren();
    let ctl = {};
    try {
      ctl = PAGES[name](main, parsed.sub) || {};
    } catch (e) {
      console.error('Akıllı Tahmin: sayfa açılamadı', e);
      main.replaceChildren(errorView({ message: 'Sayfa açılırken beklenmeyen bir hata oluştu.' }, () => location.reload(), 'Sayfa yüklenemedi.'));
    }
    state.current = { name, ctl };
    updateNav(name);
    if (focusMain) {
      try {
        main.focus({ preventScroll: true });
      } catch (e) {
        main.focus();
      }
      window.scrollTo(0, 0);
    }
  }

  function init() {
    tip.init();
    let navClicked = false;
    for (const btn of document.querySelectorAll('.nav-tab')) {
      btn.addEventListener('click', () => {
        const page = btn.dataset.page;
        const target = page === 'tahminler' && state.lastMode ? '#tahminler/' + state.lastMode : '#' + page;
        if (state.current && state.current.name === page) return;
        navClicked = true;
        location.hash = target;
      });
    }
    window.addEventListener('hashchange', () => {
      route(navClicked);
      navClicked = false;
    });
    window.addEventListener('scroll', () => tip.hide(), { passive: true });
    route(false);
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
  else init();
})();
