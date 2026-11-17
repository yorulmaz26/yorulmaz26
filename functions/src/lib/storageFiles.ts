import { randomUUID } from "crypto";
import { bucket } from "./admin";
import { fail } from "./errors";

export const MAX_IMAGE_BYTES = 5 * 1024 * 1024;
const ALLOWED_TYPES = new Set(["image/jpeg", "image/png", "image/webp"]);

export interface StoredImage {
  path: string;
  url: string;
}

/** Firebase Storage indirme URL'si (emülatörde emülatör adresi). */
export function downloadUrl(bucketName: string, path: string, token: string): string {
  const enc = encodeURIComponent(path);
  const emu = process.env.FIREBASE_STORAGE_EMULATOR_HOST;
  const base = emu ? `http://${emu}` : "https://firebasestorage.googleapis.com";
  return `${base}/v0/b/${bucketName}/o/${enc}?alt=media&token=${token}`;
}

/** Kullanıcının geçici yükleme alanındaki görseli doğrular. */
export async function assertPendingImage(uid: string, path: unknown, field: string): Promise<string> {
  if (typeof path !== "string" || !path.startsWith(`uploads/${uid}/pending/`) || path.includes("..")) {
    fail("invalid-argument", "INVALID_IMAGE", { field });
  }
  const file = bucket().file(path);
  const [exists] = await file.exists();
  if (!exists) fail("failed-precondition", "IMAGE_NOT_UPLOADED", { field });
  const [meta] = await file.getMetadata();
  const size = Number(meta.size ?? 0);
  if (!ALLOWED_TYPES.has(String(meta.contentType)) || size <= 0 || size > MAX_IMAGE_BYTES) {
    fail("invalid-argument", "INVALID_IMAGE", { field });
  }
  return path;
}

/** Geçici görseli kalıcı (kamuya açık) salon yoluna kopyalar ve indirme token'ı atar. */
export async function promoteImage(fromPath: string, toPath: string): Promise<StoredImage> {
  const b = bucket();
  const token = randomUUID();
  const dest = b.file(toPath);
  await b.file(fromPath).copy(dest);
  await dest.setMetadata({ cacheControl: "public, max-age=86400", metadata: { firebaseStorageDownloadTokens: token } });
  return { path: toPath, url: downloadUrl(b.name, toPath, token) };
}

export async function deleteQuietly(paths: string[]): Promise<void> {
  await Promise.all(paths.map((p) => bucket().file(p).delete().catch(() => undefined)));
}
