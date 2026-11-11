# tiu – Quản lý chi tiêu cho sinh viên (Android)

Kotlin · XML Views · MVVM · Firebase Auth + Cloud Firestore · minSdk 26 · compileSdk 35 · AGP 9.3 · Gradle 9.5

Báo cáo Tuần 3 (ERD, kiến trúc, CRUD): [docs/Tuan3_CSDL_KienTruc.md](docs/Tuan3_CSDL_KienTruc.md)

## Chạy dự án

1. Mở thư mục `Tiu/` bằng Android Studio.
2. **Kết nối Firebase** (mỗi nhóm làm một lần):
   1. Vào <https://console.firebase.google.com> → *Add project*.
   2. *Add app* → Android, package name **`vn.edu.dut.tiu`** → tải `google-services.json` → chép vào `app/google-services.json` (file này đã nằm trong `.gitignore`, không đưa lên Git).
   3. *Authentication* → *Sign-in method* → bật **Email/Password**.
   4. *Firestore Database* → *Create database*.
   5. Đưa Security Rules + index lên: chép nội dung `firestore.rules` vào tab *Rules* trên Console, **hoặc** dùng Firebase CLI:
      ```bash
      firebase deploy --only firestore
      ```
3. Chạy app trên máy ảo / điện thoại → Đăng ký tài khoản → dùng thử Thêm / Sửa / Xóa.

> File `app/google-services.json.example` chỉ là mẫu để build được. Nếu dùng file giả, app sẽ biên dịch được nhưng không kết nối được Firebase.

## Lệnh hữu ích

```bash
./gradlew assembleDebug        # build APK
./gradlew testDebugUnitTest    # chạy unit test
```

## Cách thao tác (CRUD)

| | Thêm | Xem | Sửa | Xóa |
|---|---|---|---|---|
| Giao dịch | Nút **+** | Tab Nhật ký | Chạm giao dịch | Nhấn giữ / nút Xóa trong form |
| Ngân sách | Chạm thẻ % hoặc "+ Tạo ngân sách danh mục" | Tab Ngân sách | Chạm thẻ | "Xóa ngân sách" trong hộp thoại |
| Danh mục | Tài khoản → Quản lý danh mục → "+ Thêm" | Như trên | Chạm | Nhấn giữ (chặn nếu đang dùng) |
