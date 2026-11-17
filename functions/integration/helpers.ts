import functionsTest from "firebase-functions-test";
import { HttpsError } from "firebase-functions/v2/https";

process.env.GCLOUD_PROJECT = "demo-kuaforum";
process.env.FIREBASE_CONFIG = JSON.stringify({ projectId: "demo-kuaforum", storageBucket: "demo-kuaforum.appspot.com" });

export const fft = functionsTest({ projectId: "demo-kuaforum", storageBucket: "demo-kuaforum.appspot.com" });

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function caller(fn: any) {
  const wrapped = fft.wrap(fn);
  return (data: unknown, uid: string, phone?: string, claims: Record<string, unknown> = {}) =>
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    (wrapped as any)({ data, auth: { uid, token: { ...(phone ? { phone_number: phone } : {}), ...claims } }, rawRequest: {} });
}

export async function expectReason(p: Promise<unknown>, reason: string) {
  try {
    await p;
  } catch (e) {
    if (e instanceof HttpsError) {
      expect((e.details as { reason?: string } | undefined)?.reason).toBe(reason);
      return;
    }
    throw e;
  }
  throw new Error(`Beklenen hata: ${reason}, ama işlem başarılı oldu`);
}

export async function clearFirestore() {
  await fetch(`http://${process.env.FIRESTORE_EMULATOR_HOST}/emulator/v1/projects/demo-kuaforum/databases/(default)/documents`, {
    method: "DELETE",
  });
}

export const uuid = () => crypto.randomUUID();
