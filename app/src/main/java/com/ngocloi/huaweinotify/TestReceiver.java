package com.ngocloi.huaweinotify;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import java.text.DateFormat;
import java.util.Date;

public final class TestReceiver extends BroadcastReceiver {
    static final String CHANNEL = "local_test";

    static void channel(Context c) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "Thử thông báo trên máy", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Chỉ kiểm tra thông báo cục bộ của HuaweiNotifyBeta");
            c.getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    @Override public void onReceive(Context c, Intent intent) {
        long requested = intent.getLongExtra("expected", System.currentTimeMillis());
        long actual = System.currentTimeMillis();
        String message = "Lịch: " + DateFormat.getTimeInstance().format(new Date(requested))
            + " · Nhận: " + DateFormat.getTimeInstance().format(new Date(actual));
        c.getSharedPreferences("guard", Context.MODE_PRIVATE).edit()
            .putString("last_test", message + "\nĐây là phép thử cục bộ; chưa kiểm tra FCM/HMS.").apply();
        channel(c);
        NotificationManager nm = (NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null || (Build.VERSION.SDK_INT >= 24 && !nm.areNotificationsEnabled())) return;
        if (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CHANNEL) : new Notification.Builder(c);
        PendingIntent launch = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        b.setSmallIcon(R.drawable.ic_guard).setContentTitle("Đã nhận thông báo thử cục bộ")
            .setContentText(message).setStyle(new Notification.BigTextStyle().bigText(message + "\nKhông phải thông báo từ FCM/HMS."))
            .setContentIntent(launch).setAutoCancel(true);
        nm.notify(17, b.build());
    }
}
