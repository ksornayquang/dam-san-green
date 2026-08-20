import { createRemoteJWKSet, jwtVerify } from "jose";

interface Env {
  CLOUDINARY_CLOUD_NAME: string;
  CLOUDINARY_API_KEY: string;
  CLOUDINARY_API_SECRET: string;
  GEMINI_API_KEY: string;
  GEMINI_MODEL: string;
  FIREBASE_PROJECT_ID: string;
  ALLOWED_ORIGIN: string;
}

const firebaseKeys = createRemoteJWKSet(
  new URL("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com")
);

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const origin = request.headers.get("Origin") || "";
    const cors = corsHeaders(origin, env.ALLOWED_ORIGIN);
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: cors });

    try {
      if (origin && !isAllowedOrigin(origin, env.ALLOWED_ORIGIN)) {
        return text("Nguồn truy cập không được phép.", 403, cors);
      }
      await verifyFirebaseUser(request, env.FIREBASE_PROJECT_ID);

      const url = new URL(request.url);
      if (request.method === "POST" && url.pathname === "/api/upload") {
        return json(await uploadToCloudinary(request, env), 200, cors);
      }
      if (request.method === "POST" && url.pathname === "/api/analyze") {
        return json(await analyzeWithGemini(request, env), 200, cors);
      }
      if (url.pathname === "/health") return json({ ok: true, service: "dam-san-green-api" }, 200, cors);
      return text("Không tìm thấy API.", 404, cors);
    } catch (error) {
      const message = error instanceof Error ? error.message : "Lỗi máy chủ.";
      const status = message.startsWith("AUTH:") ? 401 : message.startsWith("INPUT:") ? 400 : 500;
      return text(message.replace(/^(AUTH|INPUT):\s*/, ""), status, cors);
    }
  }
};

async function verifyFirebaseUser(request: Request, projectId: string) {
  const token = request.headers.get("Authorization")?.replace(/^Bearer\s+/i, "");
  if (!token) throw new Error("AUTH: Cần đăng nhập Firebase.");
  await jwtVerify(token, firebaseKeys, {
    issuer: `https://securetoken.google.com/${projectId}`,
    audience: projectId
  });
}

async function uploadToCloudinary(request: Request, env: Env) {
  requireSecrets(env, ["CLOUDINARY_CLOUD_NAME", "CLOUDINARY_API_KEY", "CLOUDINARY_API_SECRET"]);
  const input = await readImage(request);
  const timestamp = Math.floor(Date.now() / 1000).toString();
  const publicId = `${input.stage}_${Date.now()}_${crypto.randomUUID().slice(0, 8)}`;
  const folder = "damsan_green";
  const signature = await sha1(`folder=${folder}&public_id=${publicId}&timestamp=${timestamp}${env.CLOUDINARY_API_SECRET}`);
  const body = new FormData();
  body.append("file", input.file);
  body.append("api_key", env.CLOUDINARY_API_KEY);
  body.append("timestamp", timestamp);
  body.append("signature", signature);
  body.append("folder", folder);
  body.append("public_id", publicId);
  const response = await fetch(`https://api.cloudinary.com/v1_1/${env.CLOUDINARY_CLOUD_NAME}/image/upload`, { method: "POST", body });
  const payload = await response.json() as { secure_url?: string; error?: { message?: string } };
  if (!response.ok || !payload.secure_url) throw new Error(payload.error?.message || "Cloudinary không nhận ảnh.");
  return { secure_url: payload.secure_url };
}

async function analyzeWithGemini(request: Request, env: Env) {
  requireSecrets(env, ["GEMINI_API_KEY"]);
  const { before, after } = await readReportImages(request);
  const beforeBase64 = arrayBufferToBase64(await before.arrayBuffer());
  const afterBase64 = arrayBufferToBase64(await after.arrayBuffer());
  const prompt = `Bạn là chuyên gia môi trường học đường, chịu trách nhiệm xác thực một báo cáo dọn rác.
Ảnh 1 là hiện trạng trước khi dọn; ảnh 2 là minh chứng sau khi người dùng đã tới thùng rác.
Dùng ẢNH 1 làm nguồn chính để nhận dạng tên và nhóm rác; chỉ dùng ẢNH 2 để kiểm tra hành động bỏ rác.
Không được trả "Không xác định" chỉ vì vật rác không còn nhìn rõ trong ẢNH 2.
RECYCLABLE gồm chai nhựa, lon kim loại hoặc nhôm, giấy vụn sạch, bìa carton sạch.
NON_RECYCLABLE gồm khăn giấy/giấy ăn đã dùng hoặc bẩn, túi nilon bẩn, hộp xốp, vỏ kẹo và thức ăn thừa.
Với giấy hoặc khăn giấy nhỏ nhưng nhìn thấy rõ, vẫn phải xác định is_trash=true và gọi tên cụ thể, không trả "Không xác định" chỉ vì vật thể nhỏ.
Chỉ coi after_is_disposed=true khi ảnh 2 thể hiện rõ thùng rác và một trong các tình huống sau: rác đã nằm trong thùng; rác đang được đặt ngay trên miệng thùng; hoặc bàn tay đang đưa chính vật rác đó ngay phía trên miệng thùng trong hành động bỏ rác. Không chấp nhận ảnh chỉ có bàn tay, chỉ có thùng, hoặc rác đứng xa thùng.
Nếu ảnh 1 không có rác, ảnh mờ hoặc ảnh 2 không chứng minh được việc bỏ rác: is_trash=false hoặc after_is_disposed=false, confidence dưới 70.
estimated_kg là tổng khối lượng rác nhìn thấy, ước lượng thận trọng theo kg.
confidence là độ tin cậy 0-100 cho toàn bộ cặp ảnh. Chỉ trả về JSON đúng schema, không markdown và không giải thích.`;
  const body = {
    contents: [{ role: "user", parts: [
      { text: "ẢNH 1 - HIỆN TRẠNG TRƯỚC:" },
      { inline_data: { mime_type: before.type || "image/jpeg", data: beforeBase64 } },
      { text: "ẢNH 2 - MINH CHỨNG SAU:" },
      { inline_data: { mime_type: after.type || "image/jpeg", data: afterBase64 } },
      { text: prompt }
    ] }],
    generationConfig: {
      temperature: 0.15,
      maxOutputTokens: 220,
      responseMimeType: "application/json",
      responseSchema: {
        type: "OBJECT",
        properties: {
          is_trash: { type: "BOOLEAN" },
          trash_name: { type: "STRING" },
          category: { type: "STRING", enum: ["RECYCLABLE", "NON_RECYCLABLE"] },
          estimated_kg: { type: "NUMBER" },
          after_is_disposed: { type: "BOOLEAN" },
          confidence: { type: "NUMBER" },
          reason: { type: "STRING" }
        },
        required: ["is_trash", "trash_name", "category", "estimated_kg", "after_is_disposed", "confidence", "reason"]
      }
    }
  };
  const model = env.GEMINI_MODEL || "gemini-2.5-flash";
  const response = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${env.GEMINI_API_KEY}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body)
  });
  const payload = await response.json() as any;
  if (!response.ok) throw new Error(payload?.error?.message || "Gemini chưa phản hồi hợp lệ.");
  const text = payload?.candidates?.[0]?.content?.parts?.[0]?.text;
  if (!text) throw new Error("Gemini trả về nội dung rỗng.");
  return JSON.parse(text);
}

async function readImage(request: Request): Promise<{ file: File; stage: string }> {
  const data = await request.formData();
  const file = data.get("file");
  if (!(file instanceof File)) throw new Error("INPUT: Không tìm thấy ảnh.");
  if (!file.type.startsWith("image/")) throw new Error("INPUT: Tệp tải lên không phải ảnh.");
  if (file.size > 8 * 1024 * 1024) throw new Error("INPUT: Ảnh vượt quá giới hạn 8 MB.");
  return { file, stage: String(data.get("stage") || "trash").replace(/[^a-z0-9_-]/gi, "") };
}

async function readReportImages(request: Request): Promise<{ before: File; after: File }> {
  const data = await request.formData();
  const before = data.get("before");
  const after = data.get("after");
  for (const [name, file] of [["before", before], ["after", after]] as const) {
    if (!(file instanceof File)) throw new Error(`INPUT: Không tìm thấy ảnh ${name}.`);
    if (!file.type.startsWith("image/")) throw new Error(`INPUT: Ảnh ${name} không hợp lệ.`);
    if (file.size > 8 * 1024 * 1024) throw new Error(`INPUT: Ảnh ${name} vượt quá 8 MB.`);
  }
  return { before: before as File, after: after as File };
}

function requireSecrets(env: Env, keys: (keyof Env)[]) {
  const missing = keys.filter((key) => !env[key]);
  if (missing.length) throw new Error(`Máy chủ thiếu cấu hình: ${missing.join(", ")}`);
}

function isAllowedOrigin(origin: string, allowed: string): boolean {
  const values = allowed.split(",").map((value) => value.trim()).filter(Boolean);
  return values.includes(origin) || values.includes("*");
}

function corsHeaders(origin: string, allowed: string): HeadersInit {
  const selected = isAllowedOrigin(origin, allowed) ? origin : allowed.split(",")[0]?.trim() || "";
  return {
    "Access-Control-Allow-Origin": selected,
    "Access-Control-Allow-Headers": "Authorization, Content-Type",
    "Access-Control-Allow-Methods": "POST, OPTIONS",
    "Vary": "Origin",
    "X-Content-Type-Options": "nosniff"
  };
}

async function sha1(input: string): Promise<string> {
  const hash = await crypto.subtle.digest("SHA-1", new TextEncoder().encode(input));
  return [...new Uint8Array(hash)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

function arrayBufferToBase64(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer);
  let binary = "";
  for (let index = 0; index < bytes.length; index += 0x8000) {
    binary += String.fromCharCode(...bytes.subarray(index, index + 0x8000));
  }
  return btoa(binary);
}

function json(value: unknown, status: number, headers: HeadersInit) {
  return new Response(JSON.stringify(value), { status, headers: { ...headers, "Content-Type": "application/json; charset=utf-8" } });
}

function text(value: string, status: number, headers: HeadersInit) {
  return new Response(value, { status, headers: { ...headers, "Content-Type": "text/plain; charset=utf-8" } });
}
