# Dam San Green PWA - triển khai GitHub và hosting

## Cấu trúc

- `app/`: bản Android Kotlin hiện tại.
- `web/`: PWA React, dùng được trên Android, iOS và máy tính.
- `worker/`: API Cloudflare Worker bảo vệ Gemini API key và Cloudinary API secret.
- Firebase Auth và Realtime Database được dùng chung giữa Android và PWA.

## 1. Chạy thử giao diện

```powershell
cd D:\DamSanGreen\web
npm install
npm run dev
```

Mở `http://localhost:5173`. Chọn **Xem bản trình diễn dành cho BGK** để kiểm tra toàn bộ UI mà không ghi dữ liệu thật.

## 2. Cấu hình Worker

```powershell
cd D:\DamSanGreen\worker
npm install
npx wrangler login
npx wrangler secret put CLOUDINARY_CLOUD_NAME
npx wrangler secret put CLOUDINARY_API_KEY
npx wrangler secret put CLOUDINARY_API_SECRET
npx wrangler secret put GEMINI_API_KEY
npm run deploy
```

Sau khi deploy, Cloudflare trả về URL dạng `https://dam-san-green-api.<tai-khoan>.workers.dev`.

Sửa `worker/wrangler.toml`:

```toml
ALLOWED_ORIGIN = "https://dam-san-green.pages.dev"
```

Sau đó deploy Worker lại.

## 3. Biến URL Worker thành cấu hình web

Trong Cloudflare Pages, tạo biến môi trường:

```text
VITE_API_BASE_URL=https://dam-san-green-api.<tai-khoan>.workers.dev
```

Các giá trị Firebase công khai đã có mặc định theo dự án hiện tại. Có thể khai báo lại bằng các biến trong `web/.env.example`.

## 4. Đẩy lên GitHub

Không đưa `local.properties`, `.env`, `.dev.vars`, file `.jks` hoặc mật khẩu lên GitHub.

```powershell
cd D:\DamSanGreen
git init
git branch -M main
git add .
git commit -m "Add Dam San Green Android app and PWA"
git remote add origin https://github.com/<ten-github>/dam-san-green.git
git push -u origin main
```

## 5. Deploy Cloudflare Pages

1. Cloudflare Dashboard > **Workers & Pages** > **Create** > **Pages**.
2. Kết nối repository GitHub `dam-san-green`.
3. Root directory: `web`.
4. Build command: `npm run build`.
5. Build output directory: `dist`.
6. Thêm biến `VITE_API_BASE_URL`.
7. Deploy và nhận URL `https://dam-san-green.pages.dev`.

## 6. Firebase Auth

Trong Firebase Console > Authentication > Settings > Authorized domains, thêm:

```text
dam-san-green.pages.dev
```

Nếu dùng tên miền riêng, thêm cả tên miền đó.

## 7. Cài trên điện thoại

- Android Chrome: mở URL > menu > **Cài đặt ứng dụng**.
- iPhone Safari: mở URL > **Chia sẻ** > **Thêm vào Màn hình chính** > bật **Mở dưới dạng ứng dụng web**.

## 8. Checklist trước khi đưa BGK

- Đăng nhập tài khoản lớp thật.
- Chụp được camera sau trên Android và iPhone.
- GPS hoạt động trên HTTPS.
- Bản nháp còn sau khi đóng và mở lại PWA.
- Chế độ BGK cho phép ngoài 500 m và báo cáo có nhãn demo.
- Hai ảnh tải lên Cloudinary.
- Gemini trả đúng JSON và điểm nằm trong 3-15.
- Firebase hiển thị báo cáo và bảng xếp hạng realtime.
- Quét QR mở đúng URL PWA.

## Việc cần làm bằng tài khoản của chủ dự án

Codex không thể tự thực hiện các bước yêu cầu đăng nhập cá nhân: tạo GitHub repository, kết nối Cloudflare, nhập secret và thêm authorized domain trong Firebase Console.
