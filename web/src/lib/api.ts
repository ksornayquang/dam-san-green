import type { AiReview } from "../types";
import { auth } from "./firebase";

const apiBase = (import.meta.env.VITE_API_BASE_URL || "").replace(/\/$/, "");

async function authenticatedFetch(path: string, init: RequestInit) {
  if (!apiBase) {
    throw new Error("Chưa cấu hình VITE_API_BASE_URL cho dịch vụ ảnh và AI.");
  }
  const token = await auth.currentUser?.getIdToken();
  const headers = new Headers(init.headers);
  if (token) headers.set("Authorization", `Bearer ${token}`);
  const response = await fetch(`${apiBase}${path}`, { ...init, headers });
  if (!response.ok) {
    const message = await response.text();
    throw new Error(message || `Máy chủ trả về lỗi ${response.status}`);
  }
  return response;
}

export async function uploadImage(file: File, stage: "before" | "after"): Promise<string> {
  const body = new FormData();
  body.append("file", file);
  body.append("stage", stage);
  const response = await authenticatedFetch("/api/upload", { method: "POST", body });
  const payload = (await response.json()) as { secure_url: string };
  return payload.secure_url;
}

export async function analyzeImage(file: File): Promise<AiReview> {
  const body = new FormData();
  body.append("file", file);
  const response = await authenticatedFetch("/api/analyze", { method: "POST", body });
  return response.json() as Promise<AiReview>;
}

export function pointsForEstimatedWaste(kg: number): number {
  if (kg <= 0.02) return 3;
  if (kg <= 0.05) return 5;
  if (kg <= 0.15) return 7;
  if (kg <= 0.35) return 9;
  if (kg <= 0.75) return 12;
  return 15;
}
