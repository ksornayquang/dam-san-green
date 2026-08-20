export type TrashType = "recyclable" | "household";
export type ReportStatus = "pending" | "approved" | "rejected";

export interface UserProfile {
  uid: string;
  className: string;
  displayName: string;
  email: string;
  role: "student" | "admin";
  demo?: boolean;
}

export interface TrashReport {
  id: string;
  className: string;
  reporterName: string;
  imageUrl: string;
  image_before_url: string;
  image_after_url: string;
  trash_type: TrashType;
  latitude: number;
  longitude: number;
  timestamp: number;
  address: string;
  demoMode: boolean;
  points: number;
  status: ReportStatus;
  aiIsTrash: boolean;
  aiTrashName: string;
  aiCategory: "RECYCLABLE" | "NON_RECYCLABLE" | "";
  aiReviewStatus: string;
  aiEstimatedKg: number;
  aiReason: string;
  aiAutoApproved: boolean;
  aiAfterIsDisposed: boolean;
  aiAnalyzedAt: number;
}

export interface AiReview {
  is_trash: boolean;
  trash_name: string;
  category: "RECYCLABLE" | "NON_RECYCLABLE";
  estimated_kg: number;
  after_is_disposed: boolean;
  confidence: number;
  reason: string;
}

export interface ReportDraft {
  savedAt: number;
  reporterName: string;
  trashType: TrashType | null;
  beforeFile: File | null;
  afterFile: File | null;
  latitude: number | null;
  longitude: number | null;
}

export interface Ranking {
  className: string;
  totalPoints: number;
  reportCount: number;
  lastActivity: number;
}
