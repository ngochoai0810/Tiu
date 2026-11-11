# tiu – Quản lý chi tiêu cho sinh viên (Android)

Báo cáo Tuần 3 (ERD, kiến trúc, CRUD): [docs/Tuan3_CSDL_KienTruc.md](docs/Tuan3_CSDL_KienTruc.md)

## Chạy dự án

1. Mở thư mục `Tiu/` bằng Android Studio.
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
