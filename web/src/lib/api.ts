import type { AiReview } from "../types";
import { auth } from "./firebase";

const apiBase = (import.meta.env.VITE_API_BASE_URL || "").replace(/\/$/, "");

async function authenticatedFetch(path: string, init: RequestInit) {
  const token = await auth.currentUser?.getIdToken();
  const headers = new Headers(init.headers);
  if (token) headers.set("Authorization", `Bearer ${token}`);
  const response = await fetch(`${apiBase}${path}`, { ...init, headers });
  if (!response.ok) {
    const message = await response.text();
    try {
      const payload = JSON.parse(message) as { error?: string };
      throw new Error(payload.error || message || `Máy chủ trả về lỗi ${response.status}`);
    } catch (error) {
      if (error instanceof SyntaxError) {
        throw new Error(message || `Máy chủ trả về lỗi ${response.status}`);
      }
      throw error;
    }
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

export async function analyzeImages(beforeFile: File, afterFile: File): Promise<AiReview> {
  const body = new FormData();
  body.append("before", beforeFile);
  body.append("after", afterFile);
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
