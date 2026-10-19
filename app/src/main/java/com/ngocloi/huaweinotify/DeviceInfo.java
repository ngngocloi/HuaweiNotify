package com.ngocloi.huaweinotify;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.PowerManager;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Locale;

final class DeviceInfo {
    static final String GMS = "com.google.android.gms";
    static final String HMS = "com.huawei.hwid";

    static boolean installed(Context c, String pkg) {
        try { c.getPackageManager().getApplicationInfo(pkg, 0); return true; }
        catch (PackageManager.NameNotFoundException | SecurityException ignored) { return false; }
    }

    static boolean enabled(Context c, String pkg) {
        try { return c.getPackageManager().getApplicationInfo(pkg, 0).enabled; }
        catch (PackageManager.NameNotFoundException | SecurityException ignored) { return false; }
    }

    static String description(Context c, String pkg) {
        try {
            PackageInfo p = c.getPackageManager().getPackageInfo(pkg, 0);
            String label = c.getPackageManager().getApplicationLabel(p.applicationInfo).toString();
            return label + " · " + p.versionName + (p.applicationInfo.enabled ? " · Đang bật" : " · Đang tắt");
        } catch (PackageManager.NameNotFoundException | SecurityException ignored) { return "Không tìm thấy trong môi trường này"; }
    }

    static String battery(Context c, String pkg) {
        try {
            PowerManager p = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
            return p != null && p.isIgnoringBatteryOptimizations(pkg) ? "Đã miễn tối ưu pin Android" : "Chưa miễn tối ưu pin Android";
        } catch (RuntimeException e) { return "Không đọc được trạng thái pin"; }
    }

    static String network(Context c) {
        try {
            ConnectivityManager cm = (ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return "Không đọc được trạng thái mạng";
            Network n = cm.getActiveNetwork();
            if (n == null) return "Chưa có mạng đang hoạt động";
            NetworkCapabilities caps = cm.getNetworkCapabilities(n);
            if (caps == null) return "Mạng đang kết nối · chưa xác minh Internet";
            String type = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ? "Wi-Fi" : caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ? "Di động" : "Mạng";
            return type + (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ? " · Hệ thống đã xác minh Internet" : " · Hệ thống chưa xác minh Internet");
        } catch (RuntimeException e) { return "Không đọc được trạng thái mạng"; }
    }

    static String property(String key) {
        Process process = null;
        try {
            process = new ProcessBuilder("/system/bin/getprop", key).redirectErrorStream(true).start();
            // getprop is a local read-only command. Bound its lifetime, including on API 23.
            final Process active = process;
            Thread watchdog = new Thread(() -> {
                try { Thread.sleep(1000); if (active.exitValue() == 0) return; }
                catch (IllegalThreadStateException e) { active.destroy(); }
                catch (InterruptedException ignored) { }
            });
            watchdog.setDaemon(true); watchdog.start();
            String value;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream()))) { value = r.readLine(); }
            return value == null ? "" : value.trim();
        } catch (Exception ignored) { return ""; }
        finally { if (process != null) process.destroy(); }
    }

    static boolean huawei() {
        return (Build.MANUFACTURER + " " + Build.BRAND).toLowerCase(Locale.ROOT).contains("huawei");
    }

    static String report(Context c, String emui, String harmony) {
        return "HuaweiNotifyBeta 0.1.0-beta\n"
            + "Hãng: " + Build.MANUFACTURER + " / " + Build.BRAND + "\n"
            + "Model: " + Build.MODEL + "\nDevice: " + Build.DEVICE + "\n"
            + "Android: " + Build.VERSION.RELEASE + " / API " + Build.VERSION.SDK_INT + "\n"
            + "Build: " + Build.DISPLAY + "\n"
            + "EMUI property: " + (emui.isEmpty() ? "Không cung cấp" : emui) + "\n"
            + "Harmony property: " + (harmony.isEmpty() ? "Không cung cấp" : harmony) + "\n"
            + "Chỉ phản ánh môi trường Android đang chạy APK; không xác minh hệ điều hành chủ của container.\n\n"
            + "Mạng: " + network(c) + "\n"
            + "GMS: " + description(c, GMS) + "\n"
            + "GMS pin: " + battery(c, GMS) + "\n"
            + "HMS: " + description(c, HMS) + "\n"
            + "GBox: " + description(c, "com.gbox.android") + "\n"
            + "GSpace: " + description(c, "com.gspace.android") + "\n\n"
            + "Chưa xác minh token, đăng ký push hay khả năng nhận FCM/HMS của ứng dụng khác.\n"
            + "Không thu thập serial, IMEI, tài khoản hoặc nội dung thông báo.\n";
    }
}
