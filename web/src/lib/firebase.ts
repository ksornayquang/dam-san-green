import { initializeApp, getApps } from "firebase/app";
import {
  browserLocalPersistence,
  getAuth,
  onAuthStateChanged,
  setPersistence,
  signInWithEmailAndPassword,
  signOut as firebaseSignOut,
  type User
} from "firebase/auth";
import {
  get,
  getDatabase,
  onValue,
  push,
  ref,
  set,
  update
} from "firebase/database";
import type { TrashReport, UserProfile } from "../types";

const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY || "AIzaSyChTG7NSMLPdv0loVFPVe1wi2cFMR2g3B8",
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN || "damsangreen-cba4f.firebaseapp.com",
  databaseURL:
    import.meta.env.VITE_FIREBASE_DATABASE_URL ||
    "https://damsangreen-cba4f-default-rtdb.asia-southeast1.firebasedatabase.app",
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID || "damsangreen-cba4f",
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET || "damsangreen-cba4f.firebasestorage.app",
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID || "1024014572187"
};

const app = getApps()[0] ?? initializeApp(firebaseConfig);
export const auth = getAuth(app);
export const database = getDatabase(app);

void setPersistence(auth, browserLocalPersistence);

export function observeAuth(callback: (user: User | null) => void) {
  return onAuthStateChanged(auth, callback);
}

export async function login(email: string, password: string) {
  return signInWithEmailAndPassword(auth, email, password);
}

export async function logout() {
  await firebaseSignOut(auth);
}

function classFromEmail(email: string | null): string {
  const local = email?.split("@")[0]?.replace(/[ -]/g, "").toUpperCase() ?? "";
  return /^\d{2}A\d+$/.test(local) ? local : "Unknown";
}

export async function loadProfile(user: User): Promise<UserProfile> {
  const snapshot = await get(ref(database, `Users/${user.uid}`));
  const value = snapshot.val() ?? {};
  const className = value.className || classFromEmail(user.email);
  return {
    uid: user.uid,
    className,
    displayName: value.displayName || (className === "Unknown" ? "Người dùng" : `Lớp ${className}`),
    email: value.email || user.email || "",
    role: value.role === "admin" ? "admin" : "student"
  };
}

export function observeReports(callback: (reports: TrashReport[]) => void) {
  return onValue(ref(database, "TrashReports"), (snapshot) => {
    const value = snapshot.val() ?? {};
    const reports = Object.entries(value).map(([id, item]) => ({
      ...(item as Omit<TrashReport, "id">),
      id
    }));
    callback(reports.sort((a, b) => b.timestamp - a.timestamp));
  });
}

export async function createReport(report: Omit<TrashReport, "id">): Promise<string> {
  const reportRef = push(ref(database, "TrashReports"));
  await set(reportRef, { ...report, id: reportRef.key });
  return reportRef.key ?? "";
}

export async function updateReportStatus(reportId: string, status: "approved" | "rejected") {
  await update(ref(database, `TrashReports/${reportId}`), { status });
}
