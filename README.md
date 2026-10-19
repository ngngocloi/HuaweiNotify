# HuaweiNotify

Tên ứng dụng và tệp cài đặt: **HuaweiNotifyBeta**. Tải bản thử nghiệm trong thư mục [`apk/`](apk/HuaweiNotifyBeta.apk).

Ứng dụng Android thử nghiệm hỗ trợ kiểm tra và cấu hình thông báo trên Huawei. Giao diện tiếng Việt, Android 6.0 trở lên, không phụ thuộc Google SDK hoặc dịch vụ đám mây riêng.

## Bản 0.1.0 beta làm được gì?

- Đọc hãng, model, Android, build và các thuộc tính EMUI/Harmony mà môi trường hiện tại cung cấp. Không suy đoán model từ serial.
- Kiểm tra sự hiện diện và trạng thái bật của Google Play Services, HMS Core, GBox/GSpace; đọc trạng thái miễn tối ưu pin Android.
- Liệt kê ứng dụng nhìn thấy được, nhận diện một số thành phần FCM/GCM và mở các trang thông báo, chạy nền Huawei, tối ưu pin.
- Tạo hướng dẫn ADB theo những gói người dùng chọn. Helper trên máy tính thực sự thêm các gói đã cài vào danh sách miễn Doze, xác minh kết quả và ghi nhật ký hoàn tác.
- Kiểm tra TCP tới máy chủ Google khi người dùng bấm nút; thử thông báo cục bộ ngay hoặc theo lịch; lưu/chia sẻ báo cáo khi được yêu cầu.

Ứng dụng không tự bật các công tắc riêng của Huawei, không cài GMS và không thay thế dịch vụ FCM/HMS. Kết nối TCP hoặc thông báo cục bộ thành công không chứng minh ứng dụng khác nhận được push.

## Cài và dùng APK

1. Cài `HuaweiNotifyBeta.apk`. Đây là bản thử nghiệm ký bằng khóa phát triển, chưa phát hành trên cửa hàng.
2. Mở **Tổng quan** để xem model và hệ thống. Đối chiếu với **Cài đặt → Giới thiệu điện thoại** trên máy thật.
3. Vào **Ứng dụng**, quét rồi mở thiết lập cho ứng dụng bị mất thông báo. Trong quản lý khởi chạy Huawei, chọn thủ công và bật **Auto-launch**, **Secondary launch**, **Run in background**. Kiểm tra thông báo và dữ liệu nền của chính ứng dụng đó.
4. Nếu dùng FCM, kiểm tra dịch vụ Google đang hoạt động; nếu dùng HMS, kiểm tra HMS Core. Việc có gói dịch vụ chưa chứng minh đã đăng ký push.
5. Gửi tin nhắn thật từ máy khác khi khóa màn hình 5–10 phút, rồi kiểm tra lại bằng Wi-Fi và dữ liệu di động. Ghi nhận app nào lỗi, model và build trong báo cáo.

**Tương thích:** hướng tới EMUI và HarmonyOS còn môi trường Android hỗ trợ APK. Chưa kiểm chứng trên thiết bị Huawei thật. HarmonyOS 5/NEXT trở lên chưa được xác nhận; nếu chạy qua 卓易通/GBox/GSpace, model và quyền đọc được có thể chỉ thuộc môi trường bên trong. APK không có quyền điều khiển hệ điều hành chủ của container.

## Áp dụng ADB trên Windows

Cần Python 3 và [Android SDK Platform Tools chính thức](https://developer.android.com/tools/releases/platform-tools). Thêm `adb` vào PATH, bật USB debugging trên điện thoại và chấp nhận yêu cầu kết nối RSA. Mặc định chỉ làm việc khi có đúng một thiết bị đã cho phép kết nối.

Mở PowerShell tại thư mục dự án. Lệnh đầu chỉ đọc thông tin:

```powershell
.\tools\huawei-notify.cmd diagnose
```

Thêm các gói đã cài cần miễn Doze; thay danh sách bằng các gói chọn trong APK:

```powershell
.\tools\huawei-notify.cmd apply --packages com.google.android.gms com.huawei.hwid
```

Nếu có nhiều thiết bị, thêm `--serial SERIAL_CUA_THIET_BI`. Không đưa serial hoặc tệp trạng thái lên GitHub.

Helper kiểm tra toàn bộ tên gói và việc cài đặt trước khi sửa. Nó giữ nguyên các mục đã được miễn từ trước, lưu tệp `tools/huawei-state-*.json` trên máy tính, rồi ghi lại mỗi thay đổi đã xác minh. Giữ tệp này để hoàn tác, kể cả khi kết nối bị ngắt giữa chừng:

```powershell
.\tools\huawei-notify.cmd restore
```

macOS/Linux dùng các lệnh tương ứng với `python3 tools/huawei_notify.py diagnose|apply|restore`.

Miễn Doze có thể giúp giảm một số giới hạn nền của Android và có thể tăng mức tiêu thụ pin. Cách này không thay thế các công tắc quản lý chạy nền của Huawei và không giải quyết việc thiếu dịch vụ push hoặc lỗi đăng ký token. Không xóa/tắt PowerGenie, không tắt Doze toàn hệ thống, không gỡ gói hệ thống.

## Build từ mã nguồn

Mở thư mục dự án bằng Android Studio. Cấu hình dùng JDK 17, Gradle 8.9, Android Gradle Plugin 8.7.3, SDK 35; không có thư viện ứng dụng bên ngoài.

```bash
./gradlew :app:assembleDebug :app:lintDebug
```

Trên Windows thay `./gradlew` bằng `gradlew.bat`. APK Gradle nằm ở `app/build/outputs/apk/debug/app-debug.apk`.

Để build không dùng Gradle với Android SDK Build Tools 35.0.0 và JDK:

```bash
python3 tools/build_apk.py --android-jar /path/to/platforms/android-35/android.jar --build-tools /path/to/build-tools/35.0.0
```

Nếu môi trường chỉ có Java runtime, có thể thêm `--ecj /path/to/ecj.jar` với Eclipse Compiler for Java 3.36.0. Công cụ này tạo APK trong `dist/`, ký bằng khóa phát triển `~/.android/debug.keystore`. Giữ cùng khóa để cập nhật đè các bản tự build; bản ký bằng khóa khác có thể cần gỡ bản cũ trước khi cài. Không commit keystore.

Workflow `.github/workflows/android.yml` đã chuẩn bị để build, lint và xuất APK khi mã nguồn được đẩy vào repo GitHub. Chưa chạy GitHub Actions trong phiên tạo bản beta này.

## Kiểm tra

```bash
python3 -m unittest discover -s tests -v
javac -d test-output app/src/main/java/com/ngocloi/huaweinotify/Core.java tests/CoreTest.java
java -cp test-output CoreTest
```

Các kiểm tra tập trung vào dữ liệu đầu vào, tên gói không an toàn, bảo toàn danh sách có sẵn, chạy Apply nhiều lần, mất kết nối sau khi đã sửa và hoàn tác đúng thiết bị. Chưa kiểm tra giao diện/hiệu quả nhận thông báo trên điện thoại hoặc emulator; không thể coi các kiểm tra này là bằng chứng fix hoàn toàn thông báo.

## Quyền riêng tư

APK chỉ yêu cầu Internet, trạng thái mạng và quyền thông báo của chính nó. Không đọc nội dung thông báo của ứng dụng khác, serial, IMEI, tài khoản, danh bạ hay tin nhắn. Không có accessibility service, notification listener, dịch vụ chạy thường trực hoặc mã dùng root. Phép thử mạng chỉ chạy khi bấm nút. Chỉ chia sẻ/lưu báo cáo khi người dùng yêu cầu.

Helper ADB dùng serial để chọn đúng thiết bị và giữ thông tin này trong nhật ký hoàn tác cục bộ. Các tệp trạng thái bị loại khỏi git. APK mẫu HyperOS và ảnh người dùng không được đưa vào dự án.

## Tài liệu tham khảo

- [Huawei: bảo vệ ứng dụng chạy nền](https://consumer.huawei.com/en/support/content/en-us15850069/)
- [Huawei: tìm mã model](https://consumer.huawei.com/sg/support/find-model/)
- [Android: Doze và App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby)
- [Firebase: phụ thuộc Google Play Services](https://firebase.google.com/docs/android/android-play-services)

Mã ứng dụng được viết mới cho Huawei, không phải bản đổi tên của FCMGuard-HyperOS và không dùng khóa `MILLET_NO_RESTRICT_APP` của mẫu HyperOS.
