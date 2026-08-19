# Firebase Setup Checklist - Dam San Green

## Authentication

- Enable Email/Password sign-in trong Firebase Authentication.
- Tao tai khoan lop theo format `10a1@damsan.edu.vn`, `11a1@damsan.edu.vn`, ...
- Dam bao moi tai khoan lop co email hop le neu muon dung flow reset password.
- Tao it nhat mot user admin va gan role trong Realtime Database:

```json
{
  "Users": {
    "ADMIN_UID": {
      "uid": "ADMIN_UID",
      "email": "admin@damsan.edu.vn",
      "displayName": "Admin",
      "role": "admin"
    }
  }
}
```

## Realtime Database

- Bat Firebase Realtime Database.
- Kiem tra cac node dang duoc app dung:
  - `Users`
  - `TrashReports`
- Rules nen cho phep hoc sinh ghi report cua minh va chi admin duyet/xoa report.

## Firestore

- Bat Cloud Firestore.
- Tao document `settings/branding` voi cac field:
  - `appName`
  - `schoolName`
  - `bannerUrl`
  - `logoUrl`
- Rules nen cho phep tat ca user doc `settings/branding`, nhung chi admin duoc ghi.

## Cloudinary

- Upload banner/logo vao Cloudinary hoac CDN.
- Dan URL vao Admin Panel -> Settings trong app.
- Luu y: project hien dang hardcode Cloudinary API credentials trong `app/build.gradle.kts`. Nen chuyen sang `local.properties` hoac Gradle secrets truoc khi release cong khai.

## Android App

- Kiem tra `app/google-services.json` dung Firebase project hien tai.
- Chay:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat lintDebug
```

- Cai APK debug tu `app/build/outputs/apk/debug/app-debug.apk` len emulator/device de test login, reset password, admin settings va report flow.
