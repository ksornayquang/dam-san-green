# Ký APK release

## 1. Tạo keystore một lần

Chạy trong PowerShell trên máy phát triển:

```powershell
New-Item -ItemType Directory -Force D:\AndroidKeys
keytool -genkeypair -v -keystore D:\AndroidKeys\damsan-green-release.jks -alias damsan-green -keyalg RSA -keysize 2048 -validity 10000
```

Giữ file `.jks`, alias và hai mật khẩu ở nơi an toàn. Không đưa chúng lên Git hoặc gửi trong bài dự thi.

## 2. Điền `local.properties`

Thêm các dòng sau vào file local thật (file này đã được gitignore):

```properties
RELEASE_STORE_FILE=D:/AndroidKeys/damsan-green-release.jks
RELEASE_STORE_PASSWORD=mat_khau_keystore
RELEASE_KEY_ALIAS=damsan-green
RELEASE_KEY_PASSWORD=mat_khau_key
DEMO_MODE_PIN=2026
```

`DEMO_MODE_PIN` dùng để mở chế độ trình diễn BGK. Đổi mã trước khi đóng gói nếu cần.

## 3. Build và kiểm tra APK

```powershell
.\gradlew.bat :app:lintDebug :app:testDebugUnitTest :app:assembleRelease
```

Khi bốn trường signing hợp lệ, `app-release.apk` sẽ được ký tự động trong `app/build/outputs/apk/release/`. Nếu chưa điền, Gradle vẫn tạo `app-release-unsigned.apk` để debug nhưng không nên phát hành.
