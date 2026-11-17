import { initializeApp, getApps } from "firebase-admin/app";
import { getFirestore, FieldValue, Timestamp } from "firebase-admin/firestore";
import { getStorage } from "firebase-admin/storage";
import { getAuth } from "firebase-admin/auth";

if (getApps().length === 0) initializeApp();

export const db = getFirestore();
export const bucket = () => getStorage().bucket();
export const adminAuth = () => getAuth();
export { FieldValue, Timestamp };

export const isEmulator = process.env.FUNCTIONS_EMULATOR === "true" || !!process.env.FIRESTORE_EMULATOR_HOST;
