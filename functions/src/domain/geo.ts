/** Konum yardımcıları: geohash (base32) ve Haversine mesafesi. Harici bağımlılık yoktur. */
const BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz";

export function encodeGeohash(lat: number, lng: number, precision = 9): string {
  let latMin = -90, latMax = 90, lngMin = -180, lngMax = 180;
  let hash = "";
  let bit = 0, ch = 0, even = true;
  while (hash.length < precision) {
    if (even) {
      const mid = (lngMin + lngMax) / 2;
      if (lng >= mid) { ch = (ch << 1) | 1; lngMin = mid; } else { ch <<= 1; lngMax = mid; }
    } else {
      const mid = (latMin + latMax) / 2;
      if (lat >= mid) { ch = (ch << 1) | 1; latMin = mid; } else { ch <<= 1; latMax = mid; }
    }
    even = !even;
    if (++bit === 5) { hash += BASE32[ch]; bit = 0; ch = 0; }
  }
  return hash;
}

export function distanceKm(aLat: number, aLng: number, bLat: number, bLng: number): number {
  const R = 6371;
  const rad = (d: number) => (d * Math.PI) / 180;
  const dLat = rad(bLat - aLat);
  const dLng = rad(bLng - aLng);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(rad(aLat)) * Math.cos(rad(bLat)) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.min(1, Math.sqrt(h)));
}

/** Türkiye ve yakın çevresi için kaba sınır kontrolü (yanlış girilmiş koordinatları eler). */
export function isInServiceArea(lat: number, lng: number): boolean {
  return Number.isFinite(lat) && Number.isFinite(lng) && lat >= 35 && lat <= 43 && lng >= 25 && lng <= 45;
}

/**
 * Yarıçap için geohash hücre önekleri: merkezin ve 8 komşu noktasının (±r) hücreleri.
 * Hücrenin her iki kenarı yarıçaptan büyük seçilir; o durumda (−r..r) kutusunun her boyutunda
 * −r, 0, +r noktaları kutuyla kesişen tüm hücrelere düşer. Mesafe filtresi sonradan uygulanır.
 */
export function geohashPrefixesFor(lat: number, lng: number, radiusKm: number): string[] {
  const cellDeg: Record<number, [number, number]> = { 6: [0.0054932, 0.010986], 5: [0.043945, 0.043945], 4: [0.17578, 0.35156], 3: [1.40625, 1.40625] };
  const cosLat = Math.max(0.2, Math.cos((lat * Math.PI) / 180));
  let precision = 3;
  for (const p of [6, 5, 4]) {
    const [h, w] = cellDeg[p];
    if (Math.min(h * 111, w * 111 * cosLat) >= radiusKm) { precision = p; break; }
  }
  const dLat = radiusKm / 111;
  const dLng = radiusKm / (111 * cosLat);
  const set = new Set<string>();
  for (const a of [-dLat, 0, dLat]) for (const b of [-dLng, 0, dLng]) set.add(encodeGeohash(lat + a, lng + b, precision));
  return [...set];
}
