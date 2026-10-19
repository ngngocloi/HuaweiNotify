package com.ngocloi.huaweinotify;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

/** Platform-independent validation used by the app and its desktop instructions. */
public final class Core {
    private static final Pattern PACKAGE = Pattern.compile("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+");
    private Core() { }

    public static boolean validPackage(String value) {
        return value != null && value.length() <= 240 && PACKAGE.matcher(value).matches();
    }

    public static List<String> packages(Collection<String> values) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String value : values) {
            if (!validPackage(value)) throw new IllegalArgumentException("Tên gói không hợp lệ");
            unique.add(value);
        }
        return new ArrayList<>(unique);
    }

    public static String adbInstructions(Collection<String> values) {
        List<String> clean = packages(values);
        StringBuilder out = new StringBuilder();
        out.append("HUAWEI NOTIFY BETA — CẤU HÌNH ADB\n\n");
        out.append("Chỉ áp dụng trong môi trường Android/EMUI/HarmonyOS còn hỗ trợ APK.\n");
        out.append("Kết nối đúng một máy, bật USB debugging và chấp nhận khóa RSA trên máy.\n");
        out.append("Mở PowerShell trong thư mục mã nguồn. Chạy helper để tự lưu trạng thái và hoàn tác:\n\n");
        out.append(".\\tools\\huawei-notify.cmd diagnose\n");
        if (!clean.isEmpty()) {
            out.append(".\\tools\\huawei-notify.cmd apply --packages ");
            for (int i = 0; i < clean.size(); i++) {
                if (i > 0) out.append(' ');
                out.append(clean.get(i));
            }
            out.append("\n");
        }
        out.append(".\\tools\\huawei-notify.cmd restore\n\n");
        out.append("Cần Python 3 và Android SDK Platform Tools trên máy tính. macOS/Linux: python3 tools/huawei_notify.py ...\n\n");
        out.append("Helper chỉ thêm gói đã cài vào danh sách miễn Doze, kiểm tra lại kết quả và lưu các mục đã thêm.\n");
        out.append("Không thay thế Auto-launch / Secondary launch / Run in background của Huawei.\n");
        out.append("Không cài Google, không chứng minh đăng ký FCM/HMS thành công.\n\n");
        out.append("Kiểm tra model thủ công:\nadb shell getprop ro.product.model\n");
        out.append("adb shell getprop ro.build.display.id\n");
        out.append("adb shell getprop ro.build.version.emui\n\n");
        out.append("Các gói đã chọn:\n");
        for (String pkg : clean) out.append(pkg).append('\n');
        return out.toString();
    }
}
