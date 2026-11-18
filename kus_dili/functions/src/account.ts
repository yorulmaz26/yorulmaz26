/**
 * Hesap silme planı — saf fonksiyonlar (birim testli).
 * Şifreli mesaj içerikleri sohbetlerde kalır (karşı tarafın kopyası); sunucu
 * zaten okuyamaz. Kullanıcının kimliğine bağlı her şey silinir.
 */

export interface GroupDoc {
  id: string;
  members: string[];
  admins: string[];
}

/** Kullanıcı gruptan çıkarılınca grubun yeni üye ve yönetici listesi. */
export function leaveGroup(g: GroupDoc, uid: string): { members: string[]; admins: string[] } {
  const members = g.members.filter((m) => m !== uid);
  let admins = g.admins.filter((a) => a !== uid);
  if (admins.length === 0 && members.length > 0) admins = [members[0]];
  return { members, admins };
}

/** Silinecek kullanıcıya ait belge yolları (alt koleksiyonlar hariç). */
export function ownedDocPaths(uid: string, username: string | undefined): string[] {
  return [
    `users/${uid}`,
    ...(username ? [`usernames/${username}`] : []),
    `pushTokens/${uid}`,
    `keyBundles/${uid}`,
  ];
}
