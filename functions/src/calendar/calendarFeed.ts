import { randomBytes } from "crypto";
import { onRequest } from "firebase-functions/v2/https";
import { logger } from "firebase-functions/v2";
import { authedCallable } from "../lib/callable";
import { db, FieldValue, Timestamp } from "../lib/admin";
import { fail } from "../lib/errors";
import { buildIcs, IcsEvent } from "../domain";

const PAST_DAYS = 30;
const MAX_EVENTS = 500;

/**
 * Takvim aboneliği: kullanıcıya özel, tahmin edilemez bir adres (https://<alan>/cal/<token>.ics).
 * Google Takvim / Outlook / Apple Takvim bu adrese abone olur ve randevular otomatik güncellenir.
 * Adres kişisel bilgi (müşteri adları) içerebileceği için gizli tutulmalı; "yenile" eski adresi geçersiz kılar.
 */
export const getCalendarFeed = authedCallable<{ rotate?: unknown }, { token: string; path: string }>("getCalendarFeed", async (data, caller) => {
  const userRef = db.collection("users").doc(caller.uid);
  return db.runTransaction(async (tx) => {
    const user = await tx.get(userRef);
    if (!user.exists) fail("failed-precondition", "PROFILE_REQUIRED");
    let token = user.get("calendarFeedToken") as string | undefined;
    if (token && data.rotate === true) {
      tx.delete(db.collection("calendarTokens").doc(token));
      token = undefined;
    }
    if (!token) {
      token = randomBytes(24).toString("base64url");
      tx.create(db.collection("calendarTokens").doc(token), { uid: caller.uid, createdAt: FieldValue.serverTimestamp() });
      tx.update(userRef, { calendarFeedToken: token });
    }
    return { token, path: `/cal/${token}.ics` };
  });
});

type Perspective = "CUSTOMER" | "STAFF" | "OWNER";

function eventOf(d: FirebaseFirestore.DocumentSnapshot, as: Perspective): IcsEvent {
  const services = ((d.get("items") as { name: string }[] | undefined) ?? []).map((i) => i.name).join(", ");
  const status = d.get("status") as string;
  const cancelled = status.startsWith("CANCELLED") || status === "NO_SHOW";
  const prefix = status === "PENDING" ? "(Onay bekliyor) " : "";
  const summary =
    as === "CUSTOMER" ? `${prefix}${d.get("salonName")} – ${services}`
      : as === "STAFF" ? `${prefix}${d.get("customerName")} – ${services}`
        : `${prefix}${d.get("staffName")}: ${d.get("customerName")} – ${services}`;
  const lines = [`Randevu no: ${d.get("number")}`, `Uzman: ${d.get("staffName")}`, `Hizmet: ${services}`];
  if (as !== "CUSTOMER" && d.get("customerPhoneE164")) lines.push(`Müşteri tel: ${d.get("customerPhoneE164")}`);
  return {
    uid: d.id,
    start: (d.get("startAt") as Timestamp).toDate(),
    end: (d.get("endAt") as Timestamp).toDate(),
    summary,
    location: d.get("salonAddress") ?? null,
    description: lines.join("\n"),
    cancelled,
    updatedAt: (d.get("updatedAt") as Timestamp | undefined)?.toDate() ?? null,
  };
}

/** Kullanıcının müşteri, uzman ve salon sahibi olarak tüm randevuları (son 30 gün ve sonrası). */
export async function collectEvents(uid: string, now = new Date()): Promise<IcsEvent[]> {
  const from = Timestamp.fromMillis(now.getTime() - PAST_DAYS * 86_400_000);
  const owner = await db.collection("salonOwners").doc(uid).get();
  const appts = db.collection("appointments");
  const [asCustomer, asStaff, asOwner] = await Promise.all([
    appts.where("customerUid", "==", uid).where("startAt", ">=", from).orderBy("startAt", "desc").limit(MAX_EVENTS).get(),
    appts.where("staffUid", "==", uid).where("startAt", ">=", from).orderBy("startAt", "desc").limit(MAX_EVENTS).get(),
    owner.exists ? appts.where("salonId", "==", owner.get("salonId")).where("startAt", ">=", from).orderBy("startAt", "desc").limit(MAX_EVENTS).get() : Promise.resolve(null),
  ]);
  const map = new Map<string, IcsEvent>();
  const add = (docs: FirebaseFirestore.QueryDocumentSnapshot[], as: Perspective) => {
    for (const d of docs) {
      if (d.get("status") === "EXPIRED" || map.has(d.id)) continue;
      map.set(d.id, eventOf(d, as));
    }
  };
  // Öncelik: uzman (kendi işi) > müşteri > sahip görünümü.
  add(asStaff.docs, "STAFF");
  add(asCustomer.docs, "CUSTOMER");
  if (asOwner) add(asOwner.docs, "OWNER");
  return [...map.values()].sort((a, b) => a.start.getTime() - b.start.getTime()).slice(-MAX_EVENTS);
}

export const calendarFeed = onRequest({ memory: "256MiB", maxInstances: 10 }, async (req, res) => {
  const m = /\/cal\/([A-Za-z0-9_-]{20,64})\.ics$/.exec(req.path);
  if (req.method !== "GET" || !m) {
    res.status(404).send("Not found");
    return;
  }
  try {
    const tok = await db.collection("calendarTokens").doc(m[1]).get();
    if (!tok.exists) {
      res.status(404).send("Not found");
      return;
    }
    const events = await collectEvents(tok.get("uid"));
    res.set("Content-Type", "text/calendar; charset=utf-8");
    res.set("Cache-Control", "private, max-age=300");
    res.set("X-Robots-Tag", "noindex");
    res.status(200).send(buildIcs("Kuaförüm Cepte", events));
  } catch (e) {
    logger.error("calendarFeed failed", e);
    res.status(500).send("Error");
  }
});
