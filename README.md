# LYRIVA (Android)

Ứng dụng tạo video lyrics TikTok từ nhạc + lời: tạo LRC bằng cách chạm theo nhịp, dựng video 9:16 xuất MP4.
Kotlin + Jetpack Compose, minSdk 29, applicationId `com.lyriva.ninfinity`.

Bản này đã có đủ P0 đến P6, làm theo `lyriva-11-4.html`:

- **Tạo LRC** (3 trang): chọn nhạc/video, kéo cắt đoạn trên sóng nhạc, nhập lời, màn đồng bộ chạm theo nhịp, xuất LRC / LRC nâng cao / SRT / WAV vào `Download/LYRIVA`.
- **Dựng video**: xem thử, Vietsub, `//ẩn//`, romanized, video nền (thu phóng, dời, độ tối), thumbnail + lưới 3 cột + ảnh bìa PNG, xuất **MP4 H.264 + AAC** vào `Movies/LYRIVA`.
- Xuất video chạy trong foreground service, dựng từng khung 30fps nên không rớt khung.
- Mọi màn hình vừa khít một khung hình, không cuộn dọc.

Chưa kiểm chứng trên máy thật (xem mục cuối).

## Đưa lên GitHub

1. Tạo repo **private** mới trên GitHub, ví dụ `lyriva-android`.
2. Giải nén file zip, đẩy toàn bộ nội dung (kể cả thư mục ẩn `.github`) lên nhánh `main`.
3. Vào tab **Actions**, workflow **Build LYRIVA APK** sẽ tự chạy (khoảng 5–8 phút lần đầu).

## Lấy APK

- **Releases** (cột phải trang repo): mỗi lần build xong có một bản `build-N` kèm `LYRIVA-release-buildN.apk`.
- **Actions → lần chạy → Artifacts**: có cả bản debug và release.
- Bản debug có đuôi `.debug` nên cài song song được với bản release.

## Ký APK bằng khóa riêng (nên làm một lần)

Không làm bước này thì APK release vẫn cài được nhưng ký bằng khóa debug dùng chung.

1. **Actions → Tạo khóa ký release → Run workflow** (repo phải để private).
2. Mở lần chạy đó, tải artifact `LYRIVA-signing-secrets`, giải nén `secrets.txt`.
3. **Settings → Secrets and variables → Actions → New repository secret**, tạo 4 secret đúng tên và giá trị trong `secrets.txt`:
   `LYRIVA_KEYSTORE_BASE64`, `LYRIVA_KEYSTORE_PASSWORD`, `LYRIVA_KEY_ALIAS`, `LYRIVA_KEY_PASSWORD`.
4. Xóa artifact `LYRIVA-signing-secrets` và cất `secrets.txt` ở nơi an toàn. **Mất khóa này thì không cập nhật đè được bản đã cài.**

Từ lần build sau, APK release tự được ký bằng khóa này.

## Build trên máy (tùy chọn)

Mở thư mục bằng Android Studio (bản mới), nó tự tạo Gradle wrapper. CI không cần wrapper vì dùng `gradle/actions/setup-gradle`.

## Những điểm cần kiểm chứng khi chạy thật

- Lần build đầu có thể có lỗi biên dịch nhỏ. Gửi log dòng đỏ trong Actions để sửa.
- **Tải font:** URL nằm trong `render/Fonts.kt` (`FontCatalog`), lấy từ kho `google/fonts` trên GitHub. Nếu tải lỗi, sửa URL ở đó.
- **Video nền** lấy khung bằng `MediaMetadataRetriever` nên xuất chậm hơn khi bật nền.
- Bảng cài đặt ở tab Dựng video cao cố định (khoảng 42% màn hình). Máy màn hình ngắn dưới 700dp có thể phải cuộn nhẹ trong bảng.
