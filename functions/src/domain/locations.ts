import locations from "../data/trLocations.json";

/** 81 il / 973 ilçe (app/src/main/assets/tr_locations.json ile aynı dosya). */
const byCity = new Map<string, Set<string>>(
  (locations as { name: string; districts: string[] }[]).map((c) => [c.name, new Set(c.districts)]),
);

export function isValidCityDistrict(city: string, district: string): boolean {
  return byCity.get(city)?.has(district) ?? false;
}

export const CITY_COUNT = byCity.size;
