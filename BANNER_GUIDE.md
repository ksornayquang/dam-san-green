# Huong Dan Thay Banner & Logo - Dam San Green

## Banner Hien Thi O Dau?

Banner/branding hien dang duoc ho tro qua:

- `layout_banner.xml`: component banner co gradient fallback va nut thay banner cho admin.
- `SettingsActivity`: man hinh admin nhap app name, school name, banner URL va logo URL.
- `SettingsService`: doc/ghi Firestore document `settings/branding`.

## Cau Truc Firestore

Tao collection/document sau trong Firebase Firestore:

```json
{
  "settings": {
    "branding": {
      "bannerUrl": "https://res.cloudinary.com/.../banner.jpg",
      "logoUrl": "https://res.cloudinary.com/.../logo.png",
      "schoolName": "Truong PTDTNT THPT Dam San",
      "appName": "Dam San Green"
    }
  }
}
```

Trong Firestore Console, cau truc tuong ung la:

- Collection: `settings`
- Document: `branding`
- Fields: `bannerUrl`, `logoUrl`, `schoolName`, `appName`

## Cach Thay Banner

1. Chuan bi anh banner kich thuoc khuyen nghi 1920 x 600 px.
2. Upload anh len Cloudinary hoac CDN dang tin cay.
3. Dang nhap bang tai khoan admin trong app.
4. Mo Admin Panel, bam nut cai dat o header.
5. Dan `bannerUrl`, kiem tra preview, bam `Luu cai dat`.

Neu `bannerUrl` de trong, app se dung gradient fallback `bg_login_top.xml`.

## Cach Thay Logo

App chi dung logo tu Cloudinary/Firestore:

1. Upload logo len Cloudinary hoac CDN dang tin cay.
2. Luu URL vao field `logoUrl` trong Firestore `settings/branding`.
3. Neu `logoUrl` de trong hoac tai loi, app se an logo thay vi hien logo local.

## Luu Y Van Hanh

- Firestore can duoc bat trong Firebase Console truoc khi dung SettingsActivity.
- Security Rules nen gioi han write `settings/branding` cho admin.
- Banner nen duoi 500KB de load nhanh tren mang di dong.
- Cloudinary dang la noi luu anh chinh cua project; khong can Firebase Storage neu khong doi kien truc upload.
