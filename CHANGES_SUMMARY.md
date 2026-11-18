# Những thay đổi trong dự án Tiu (Git Workspace Summary)

Tài liệu này tổng hợp toàn bộ các tính năng, cải tiến và sửa lỗi đã được thực hiện trong không gian làm việc (workspace):

## 1. Tính năng Camera & OCR Nhận diện hóa đơn
- **Xử lý ảnh & Trích xuất số tiền**: Thêm `ReceiptAmountParser.kt` để tự động phân tích và trích xuất số tiền từ ảnh chụp hóa đơn.
- **Giao diện Camera (`CameraFragment.kt` & `fragment_camera.xml`)**:
  - Hỗ trợ chế độ chụp ảnh và quét OCR.
  - Các tài nguyên đồ họa mới: Khung ngắm (`bg_receipt_scan_frame`), nút chụp (`bg_shutter`), hiệu ứng lấy nét (`ic_camera_focus`), đèn flash (`ic_flash`).

## 2. Luồng Onboarding 2 bước & Cài đặt thông báo
- **Màn hình chào mừng (`OnboardingStep1Fragment` & `fragment_onboarding_step1.xml`)**:
  - Giao diện thẻ doodle nền kem (`#FFF7E8` - `tiu_paper`), tiêu đề *"Chụp một tấm. Nhớ từng khoản"* và nút *"Bắt đầu với tiu"*.
- **Màn hình bật thông báo (`OnboardingStep2Fragment` / `NotificationOnboardingFragment` & `fragment_notification_onboarding.xml`)**:
  - Giới thiệu tính năng nhắc nhở định kỳ của Tiu (*"Để tiu nhắc bạn đúng lúc"*).
  - **Tùy chỉnh giờ nhắc nhở**: Cho phép chạm trực tiếp vào mốc giờ (ví dụ `tiu · 21:00`) để mở `MaterialTimePicker` thay đổi giờ nhắc nhở linh hoạt trước khi bật.
  - Tích hợp xin quyền hệ thống `POST_NOTIFICATIONS` trên Android 13+.

## 3. Cập nhật Icon ứng dụng (App Logo)
- **Thiết kế chuẩn Figma**: Tinh chỉnh `ic_launcher_foreground.xml`, `ic_launcher.xml` và `ic_launcher_round.xml`.
- Nền thẻ bo tròn màu kem `#FFF7E8`, đồng hồ/xu cam `#FFB780` có viền nét nâu doodle `#4A3427`, chữ `t`, tia cam `#F07D45` và hiệu ứng bóng đổ lệch 1 bên (offset drop shadow).

## 4. Sửa lỗi & Cải thiện Validation Đăng ký tài khoản
- **Cải tiến trong `AuthViewModel.kt`**:
  - Phân tách thông báo lỗi khi xác nhận mật khẩu:
    - Nếu để trống ô xác nhận: Hiển thị `"Vui lòng nhập lại mật khẩu"`.
    - Nếu nhập khác với mật khẩu: Hiển thị `"Mật khẩu nhập lại không khớp"`.

## 5. Cải tiến các màn hình chính (Home, Stats, Budget, Transaction, Account)
- Cập nhật và tối ưu các Fragment: `HomeFragment`, `StatsFragment`, `BudgetFragment`, `TransactionEditorFragment`, `AccountFragment`, `NotificationFragment`.
- Cải thiện trải nghiệm người dùng và giao diện theo hệ thống thiết kế Tiu Design System.
