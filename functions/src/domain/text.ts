/** Kotlin SearchKey.of ile aynı: tr küçük harf, ı→i, aksan temizliği, tek boşluk. */
export function searchKey(text: string): string {
  return text
    .trim()
    .toLocaleLowerCase("tr-TR")
    .replace(/ı/g, "i")
    .normalize("NFD")
    .replace(/\p{Mn}+/gu, "")
    .replace(/\s+/g, " ");
}
