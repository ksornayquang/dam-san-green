# Completion Report - Dam San Green

## Da Hoan Thanh

- Don sach emoji hien thi trong `app/src/main`: layouts, menu, Kotlin status text va mission data.
- Doi cac diem UI con lai sang vector drawable/icon san co: admin, guest, report, leaderboard, school info, notification/menu bottom sheet.
- Them base style `DS`, `DS.Text`, `DS.Button` de Android resource linker xu ly dung style chain.
- Sua `ic_settings.xml` thanh Android Vector Drawable hop le.
- Them flow quen mat khau:
  - `ForgotPasswordActivity.kt`
  - `activity_forgot_password.xml`
  - Link `Quen mat khau?` trong login
  - Dang ky activity trong manifest
- Them flow admin settings/branding:
  - `SettingsActivity.kt`
  - `activity_settings.xml`
  - Nut cai dat trong Admin Panel
  - Luu Firestore qua `SettingsService`
- Cap nhat `BANNER_GUIDE.md` theo Firestore `settings/branding`.
- Them `FIREBASE_SETUP_CHECKLIST.md`.

## Kiem Tra Da Chay

```powershell
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
```

Ket qua: ca hai lenh deu thanh cong.

## Luu Y Con Lai

- Can cau hinh Firestore rules de chi admin duoc ghi `settings/branding`.
- Firebase Auth password reset chi gui email neu tai khoan lop co email hop le va project da cau hinh email template/sender.
- Cloudinary/Gemini credentials van dang hardcode trong `app/build.gradle.kts`; nen dua sang secret/local config truoc khi release.
- `SettingsActivity` da luu `logoUrl`, nhung cac man hinh hien tai van chu yeu dung logo local. Co the noi `logoUrl` vao splash/login/banner neu muon branding dong hoan toan.
