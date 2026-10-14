# QR Picker – Chọn ảnh mã QR và mở link (dùng cho giả lập Android)

Ứng dụng không cần camera: chọn ảnh chứa mã QR từ thư viện → giải mã → tự mở link.

## Cách 1: Build bằng Android Studio
1. Giải nén, mở thư mục `QrPicker` bằng Android Studio (bản Koala trở lên).
2. Chờ Gradle sync xong.
3. Menu **Build → Build App Bundle(s)/APK(s) → Build APK(s)**.
4. APK nằm ở `app/build/outputs/apk/debug/app-debug.apk`.

Hoặc dòng lệnh: `./gradlew assembleDebug` (Windows: `gradlew.bat assembleDebug`).

## Cách 2: Build trên GitHub (không cần cài Android SDK)
1. Tạo repo GitHub, đẩy toàn bộ thư mục lên.
2. Vào tab **Actions** → workflow **Build APK** tự chạy.
3. Xong thì tải file APK ở mục **Artifacts**.

## Cài lên giả lập
- Kéo thả file APK vào cửa sổ giả lập (Android Emulator, LDPlayer, Nox, MEmu, BlueStacks đều hỗ trợ), hoặc
- `adb install app-debug.apk`

## Đưa ảnh QR vào giả lập
- Kéo thả ảnh vào cửa sổ giả lập (thường lưu vào thư mục Download/Pictures), hoặc
- `adb push qr.png /sdcard/Download/`
  rồi `adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Download/qr.png`
  để ảnh hiện trong Thư viện.

## Sử dụng
- Bấm **Chọn ảnh mã QR** → chọn ảnh → link tự mở trong trình duyệt.
- Hoặc từ Thư viện ảnh bấm **Chia sẻ → QR Picker**.
- Bỏ tick "Tự động mở link" nếu muốn xem nội dung trước rồi mới bấm **Mở link**.
- Nội dung không phải link (văn bản, Wi‑Fi...) sẽ hiển thị để sao chép.
