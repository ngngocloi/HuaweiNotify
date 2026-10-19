# Kết quả kiểm tra bản 0.1.0 beta

- Biên dịch Java 8 bằng ECJ 3.36.0, Android SDK 35, Build Tools 35.0.0; D8 desugaring cho minSdk 23.
- Đóng gói APK thành công; package `com.ngocloi.huaweinotify`, versionCode 1, targetSdk 35.
- Chữ ký APK v1/v2/v3 hợp lệ; kiểm tra zipalign và cấu trúc ZIP thành công.
- 9 bài kiểm tra Python cho helper ADB đều qua.
- CoreTest Java kiểm tra đầu vào không an toàn và nội dung lệnh được tạo: qua.
- Chưa chạy Android Lint/Gradle, emulator hoặc kiểm thử thiết bị Huawei thật.
- GitHub Actions đã được kích hoạt nhưng job không khởi chạy do tài khoản GitHub bị khóa bởi vấn đề thanh toán; chưa có kết quả CI.
- Chưa xác minh hiệu quả nhận thông báo FCM/HMS. Cần phép thử trên model và build cụ thể.

APK SHA-256:

`6c1f5519c5a6ae39e10cb6e966cadfb804881e99584b642c8a17962098162f2f`

Khóa ký phát triển; chứng chỉ SHA-256:

`ae38708f9c2e6a907f1f7599aafca3d362cd084770de8ce89646f68d77911b91`
