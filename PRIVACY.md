# Quyền riêng tư — 0.1.0 beta

Ứng dụng không có tài khoản, phân tích hành vi, quảng cáo hoặc máy chủ riêng.

- Thông tin model, build, dịch vụ và ứng dụng được xử lý trên thiết bị.
- Danh sách gói được chọn và kết quả thử cục bộ được lưu trong bộ nhớ riêng của ứng dụng.
- Phép thử TCP tới `mtalk.google.com` chỉ chạy khi bấm nút. Nhà cung cấp mạng và máy chủ đích có thể thấy kết nối IP thông thường; ứng dụng không gửi serial, token hay tài khoản.
- Lưu và chia sẻ báo cáo do người dùng chủ động chọn. Nội dung báo cáo có thể gồm tên model/build và các tên gói được chọn.
- APK không đọc serial, IMEI, danh bạ, tin nhắn hoặc nội dung thông báo của ứng dụng khác.
- Helper ADB trên máy tính lưu serial và các gói đã thêm trong tệp trạng thái cục bộ để hoàn tác đúng thiết bị. Giữ tệp này cho tới khi khôi phục xong. Không chia sẻ hoặc commit tệp trạng thái.

Gỡ APK sẽ xóa dữ liệu riêng của ứng dụng nhưng không tự khôi phục các thay đổi ADB. Chạy lệnh `restore` bằng helper và nhật ký đã lưu trước khi xóa tệp trạng thái.
