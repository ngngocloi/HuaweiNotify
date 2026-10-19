package com.ngocloi.huaweinotify;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int BG = 0xff0b1220, CARD = 0xff142237, WHITE = 0xffeff6ff;
    private static final int MUTED = 0xffa8b8ce, ACCENT = 0xff4de1c1, AMBER = 0xffffce7a;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<AppEntry> apps = new ArrayList<>();
    private Set<String> selected = new LinkedHashSet<>();
    private LinearLayout body;
    private SharedPreferences prefs;
    private String tab = "home", emui = "", harmony = "", probe = "Chưa chạy phép thử kết nối.";
    private String filter = "", exportText = "";
    private boolean scanRunning, scanComplete, probeRunning;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        prefs = getSharedPreferences("guard", MODE_PRIVATE);
        if (state != null) { tab = state.getString("tab", "home"); filter = state.getString("filter", ""); exportText = state.getString("export", ""); }
        selected = new LinkedHashSet<>(prefs.getStringSet("selected", new HashSet<>()));
        if (!prefs.getBoolean("selection_initialized", false)) {
            for (String pkg : new String[]{DeviceInfo.GMS, DeviceInfo.HMS}) if (DeviceInfo.installed(this, pkg)) selected.add(pkg);
            saveSelection();
        }
        worker.execute(() -> {
            String e = DeviceInfo.property("ro.build.version.emui");
            String h = DeviceInfo.property("hw_sc.build.platform.version");
            if (h.isEmpty()) h = DeviceInfo.property("ro.harmony.build.version");
            final String hv = h;
            runOnUiThread(() -> { if (isDestroyed()) return; emui = e; harmony = hv; if (tab.equals("home")) render(); });
        });
        render();
    }

    @Override public void onResume() { super.onResume(); if (prefs != null) render(); }
    @Override public void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); out.putString("tab", tab); out.putString("filter", filter); out.putString("export", exportText); }
    @Override public void onDestroy() { worker.shutdownNow(); super.onDestroy(); }

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    private GradientDrawable background(int color, int radius) {
        GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(radius)); return g;
    }

    private TextView text(LinearLayout parent, String value, int size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setLineSpacing(dp(3), 1); if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setTextIsSelectable(true);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.bottomMargin = dp(8);
        parent.addView(t, p); return t;
    }

    private LinearLayout card(String title, String subtitle) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(18), dp(18), dp(14)); box.setBackground(background(CARD, 20));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.bottomMargin = dp(14); body.addView(box, p);
        if (!title.isEmpty()) text(box, title, 19, WHITE, true);
        if (!subtitle.isEmpty()) text(box, subtitle, 14, MUTED, false);
        return box;
    }

    private Button button(LinearLayout box, String label, Runnable action, boolean primary) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(14);
        b.setTextColor(primary ? BG : WHITE); b.setBackground(background(primary ? ACCENT : 0xff263950, 12));
        b.setMinHeight(dp(48)); b.setPadding(dp(14), dp(8), dp(14), dp(8));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(8); p.bottomMargin = dp(3);
        box.addView(b, p); b.setOnClickListener(v -> action.run()); return b;
    }

    private void render() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets i = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                v.setPadding(i.left, i.top, i.right, i.bottom);
            } else v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout header = new LinearLayout(this); header.setOrientation(LinearLayout.VERTICAL); header.setPadding(dp(22),dp(18),dp(22),dp(8));
        root.addView(header); text(header, "HUAWEI  /  NOTIFY BETA", 12, ACCENT, true);
        text(header, "Thông báo, rõ trạng thái.", 25, WHITE, true);
        text(header, "0.1.0 beta · Kiểm tra & hỗ trợ cấu hình", 12, MUTED, false);
        LinearLayout tabs = new LinearLayout(this); tabs.setPadding(dp(14),0,dp(14),dp(10)); root.addView(tabs);
        String[] ids = {"home", "apps", "tools"}, labels = {"Tổng quan", "Ứng dụng", "Công cụ"};
        for (int n=0; n<ids.length; n++) {
            final String id=ids[n]; Button b=new Button(this); b.setText(labels[n]); b.setAllCaps(false); b.setTextSize(13);
            b.setTextColor(tab.equals(id) ? BG : MUTED); b.setBackground(background(tab.equals(id) ? ACCENT : CARD,12));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(46),1); p.setMargins(dp(4),0,dp(4),0); tabs.addView(b,p);
            b.setOnClickListener(v -> { tab=id; render(); if (tab.equals("apps") && !scanComplete) scanApps(); });
        }
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        body=new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(18),dp(8),dp(18),dp(24)); scroll.addView(body);
        setContentView(root);
        if (tab.equals("home")) renderHome(); else if (tab.equals("apps")) renderApps(); else renderTools();
    }

    private void renderHome() {
        LinearLayout device = card(Build.MODEL, Build.MANUFACTURER + " · Android " + Build.VERSION.RELEASE + " · API " + Build.VERSION.SDK_INT);
        text(device,"Build: " + Build.DISPLAY,13,MUTED,false);
        if (!emui.isEmpty()) text(device,"EMUI property: " + emui,13,WHITE,false);
        if (!harmony.isEmpty()) text(device,"Harmony property: " + harmony,13,WHITE,false);
        text(device,"Đây là model và hệ thống mà APK đọc được trực tiếp. Không suy đoán mẫu máy từ serial.",13,MUTED,false);
        if (!DeviceInfo.huawei()) text(device,"Môi trường này không báo hãng Huawei. Nếu chạy trong GBox/GSpace/卓易通, thông tin và quyền có thể chỉ thuộc container.",13,AMBER,false);
        LinearLayout scope=card("Phạm vi hỗ trợ", "Dành cho môi trường Android trên Huawei: EMUI và HarmonyOS còn chạy APK.");
        text(scope,"HarmonyOS 5/NEXT trở lên: chưa xác minh. Chạy APK qua lớp tương thích không đồng nghĩa có thể chỉnh hệ thống Huawei bên ngoài.",13,AMBER,false);
        LinearLayout status=card("Các điều kiện cần kiểm tra",DeviceInfo.network(this));
        text(status,"Google Play Services",15,WHITE,true); text(status,DeviceInfo.description(this,DeviceInfo.GMS),13,MUTED,false);
        if (DeviceInfo.installed(this,DeviceInfo.GMS)) text(status,DeviceInfo.battery(this,DeviceInfo.GMS),13,AMBER,false);
        text(status,"HMS Core",15,WHITE,true); text(status,DeviceInfo.description(this,DeviceInfo.HMS),13,MUTED,false);
        text(status,"Có gói dịch vụ chưa chứng minh máy đã đăng ký nhận push. microG có thể dùng cùng tên gói; bản này chưa kiểm tra chứng nhận GMS hoặc token.",13,MUTED,false);
        LinearLayout steps=card("Thiết lập trên Huawei", "Thực hiện cho ứng dụng bị trễ thông báo và dịch vụ push mà ứng dụng đó dùng.");
        text(steps,"1. Bật thông báo cho ứng dụng.\n2. Trong App launch, chọn quản lý thủ công rồi bật Auto-launch, Secondary launch và Run in background.\n3. Xem lại tối ưu pin và quyền dùng dữ liệu nền.\n4. Thử nhận tin nhắn thật khi khóa màn hình.",14,WHITE,false);
        button(steps,"Mở quản lý chạy nền Huawei",this::huaweiGuide,true);
        button(steps,"Xem & chọn ứng dụng",() -> {tab="apps"; render(); if(!scanComplete) scanApps();},false);
        button(steps,"Chia sẻ thông tin model / hệ thống",() -> share(report()),false);
    }

    private void renderApps() {
        LinearLayout intro=card("Ứng dụng cần nhận thông báo", "Dấu chọn dùng để tạo cấu hình ADB. App không tự thay đổi quyền hay kết luận đã sửa xong.");
        text(intro,scanRunning ? "Đang quét…" : (scanComplete ? apps.size()+" ứng dụng nhìn thấy được · "+selected.size()+" gói được chọn" : "Chưa quét ứng dụng"),13,ACCENT,true);
        button(intro,"Quét lại danh sách",this::scanApps,false).setEnabled(!scanRunning);
        EditText search=new EditText(this); search.setSingleLine(true); search.setTextColor(WHITE); search.setHintTextColor(MUTED);
        search.setHint("Tên hoặc tên gói ứng dụng"); search.setText(filter); intro.addView(search);
        button(intro,"Lọc danh sách",() -> {filter=search.getText().toString().trim(); render();},false);
        text(intro,"Danh sách phụ thuộc quyền nhìn thấy gói của Android. Nhận diện thành phần FCM chỉ là dấu hiệu kỹ thuật; không xác minh push đang hoạt động.",12,MUTED,false);
        String lower=filter.toLowerCase(Locale.ROOT); int count=0;
        for(AppEntry app: apps) {
            if(!lower.isEmpty() && !(app.label+" "+app.pkg).toLowerCase(Locale.ROOT).contains(lower)) continue;
            count++;
            LinearLayout item=card(app.label,app.pkg);
            text(item,DeviceInfo.battery(this,app.pkg),12,app.exempt?ACCENT:AMBER,false);
            if(app.fcm) text(item,"Có thành phần liên quan FCM/GCM",12,ACCENT,false);
            CheckBox check=new CheckBox(this); check.setText("Chọn cho cấu hình ADB"); check.setTextColor(WHITE); check.setChecked(selected.contains(app.pkg)); item.addView(check);
            check.setOnCheckedChangeListener((v,on) -> {if(on) selected.add(app.pkg); else selected.remove(app.pkg); saveSelection();});
            button(item,"Thiết lập ứng dụng",() -> appMenu(app),false);
        }
        if(scanComplete && count==0) text(body,"Không tìm thấy ứng dụng phù hợp. Hãy xóa bộ lọc hoặc kiểm tra ứng dụng bên trong container của bạn.",14,MUTED,false);
    }

    private void renderTools() {
        LinearLayout adb=card("Cấu hình ADB trên máy tính",selected.size()+" gói được chọn · Không cần root");
        text(adb,"Helper đi kèm mã nguồn có thể thêm các gói đã chọn vào danh sách miễn tối ưu pin Android. Cần máy tính, USB debugging và xác nhận kết nối trên điện thoại. Không thay thế quản lý chạy nền Huawei.",14,MUTED,false);
        button(adb,"Xem hướng dẫn cho các gói đã chọn",() -> showText("Cấu hình ADB",Core.adbInstructions(selected)),true);
        button(adb,"Lưu hướng dẫn thành tệp .txt",() -> export(Core.adbInstructions(selected),"HuaweiNotifyBeta-ADB.txt"),false);
        LinearLayout network=card("Kiểm tra đường đến FCM", "Chỉ chạy khi bấm nút. Mở kết nối TCP tới mtalk.google.com, không gửi token hay tài khoản.");
        text(network,probe,13,MUTED,false);
        button(network,probeRunning?"Đang kiểm tra…":"Kiểm tra kết nối Google",this::probeNetwork,false).setEnabled(!probeRunning);
        text(network,"TCP kết nối được chưa chứng minh FCM nhận tin; thất bại cũng có thể do mạng, DNS hoặc firewall.",12,AMBER,false);
        LinearLayout local=card("Thử thông báo cục bộ", "Chỉ kiểm tra thông báo của HuaweiNotifyBeta; không kiểm tra thông báo Zalo, Messenger, Gmail hoặc FCM/HMS.");
        text(local,notificationsAllowed()?"Quyền thông báo của Guard: đang bật":"Quyền thông báo của Guard: đang tắt",13,notificationsAllowed()?ACCENT:AMBER,false);
        button(local,"Bật quyền thông báo cho Guard",this::askNotifications,false);
        button(local,"Gửi thông báo thử ngay",() -> test(false),true);
        button(local,"Lên lịch thử sau khoảng 2 phút",() -> test(true),false);
        button(local,"Hủy lịch thử",this::cancelTest,false);
        text(local,prefs.getString("last_test","Chưa có kết quả thử."),12,MUTED,false);
        text(local,"Lịch thử không chính xác tuyệt đối và có thể bị hệ thống trì hoãn. Sau khi lên lịch, khóa màn hình để quan sát.",12,MUTED,false);
        LinearLayout report=card("Báo cáo & quyền riêng tư", "Không đọc serial, IMEI, tài khoản hoặc nội dung thông báo của ứng dụng khác.");
        text(report,"Báo cáo chỉ được chia sẻ hoặc lưu khi bạn bấm nút. Danh sách ứng dụng được quét cục bộ. App không có dịch vụ chạy thường trực.",13,MUTED,false);
        button(report,"Xem / sao chép báo cáo",() -> showText("Báo cáo chẩn đoán",report()),false);
        button(report,"Lưu báo cáo .txt",() -> export(report(),"HuaweiNotifyBeta-report.txt"),false);
        button(report,"Hướng dẫn chính thức của Huawei",() -> open(new Intent(Intent.ACTION_VIEW,Uri.parse("https://consumer.huawei.com/en/support/content/en-us15850069/"))),false);
    }

    private void scanApps() {
        if(scanRunning) return; scanRunning=true; render();
        worker.execute(() -> {
            List<AppEntry> found=new ArrayList<>();
            try {
                PackageManager pm=getPackageManager(); LinkedHashSet<String> names=new LinkedHashSet<>();
                Intent launcher=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
                for(ResolveInfo r:pm.queryIntentActivities(launcher,0)) if(r.activityInfo!=null) names.add(r.activityInfo.packageName);
                for(String p:new String[]{DeviceInfo.GMS,DeviceInfo.HMS,"com.google.android.gsf","com.android.vending","com.gbox.android","com.gspace.android"})
                    if(DeviceInfo.installed(this,p)) names.add(p);
                for(ResolveInfo r:pm.queryIntentServices(new Intent("com.google.firebase.MESSAGING_EVENT"),0)) if(r.serviceInfo!=null) names.add(r.serviceInfo.packageName);
                for(ResolveInfo r:pm.queryBroadcastReceivers(new Intent("com.google.android.c2dm.intent.RECEIVE"),0)) if(r.activityInfo!=null) names.add(r.activityInfo.packageName);
                for(String p:names) {
                    if(!Core.validPackage(p) || p.equals(getPackageName())) continue;
                    try {
                        PackageInfo pi=pm.getPackageInfo(p,PackageManager.GET_SERVICES|PackageManager.GET_RECEIVERS);
                        if(!pi.applicationInfo.enabled) continue;
                        boolean fcm=false;
                        if(pi.services!=null) for(ServiceInfo service:pi.services) if(service.name!=null && (service.name.toLowerCase(Locale.ROOT).contains("firebase")||service.name.toLowerCase(Locale.ROOT).contains("gcm"))) fcm=true;
                        if(pi.receivers!=null) for(ActivityInfo receiver:pi.receivers) if(receiver.name!=null && (receiver.name.toLowerCase(Locale.ROOT).contains("firebase")||receiver.name.toLowerCase(Locale.ROOT).contains("gcm"))) fcm=true;
                        found.add(new AppEntry(p,pm.getApplicationLabel(pi.applicationInfo).toString(),fcm,DeviceInfo.battery(this,p).startsWith("Đã")));
                    } catch(PackageManager.NameNotFoundException|SecurityException ignored) { }
                }
                Collections.sort(found,(a,b) -> {
                    boolean ac=a.pkg.equals(DeviceInfo.GMS)||a.pkg.equals(DeviceInfo.HMS), bc=b.pkg.equals(DeviceInfo.GMS)||b.pkg.equals(DeviceInfo.HMS);
                    return ac!=bc?(ac?-1:1):a.label.compareToIgnoreCase(b.label);
                });
            } catch(RuntimeException ignored) { }
            runOnUiThread(() -> {if(isDestroyed())return; apps.clear(); apps.addAll(found); scanRunning=false;scanComplete=true; if(tab.equals("apps"))render();});
        });
    }

    private void saveSelection() { prefs.edit().putStringSet("selected",new HashSet<>(selected)).putBoolean("selection_initialized",true).apply(); }

    private void appMenu(AppEntry a) {
        new AlertDialog.Builder(this).setTitle(a.label).setItems(new String[]{"Quyền thông báo","Quản lý chạy nền Huawei","Tối ưu pin Android","Thông tin ứng dụng"},(d,n) -> {
            if(n==0) notificationSettings(a.pkg);
            else if(n==1) huaweiGuide();
            else if(n==2) open(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            else open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+a.pkg)));
        }).setNegativeButton("Đóng",null).show();
    }

    private void huaweiGuide() {
        new AlertDialog.Builder(this).setTitle("Quản lý chạy nền Huawei")
            .setMessage("Tìm ứng dụng cần sửa → tắt Manage automatically → bật Auto-launch, Secondary launch và Run in background.\n\nNếu ROM không cho mở trực tiếp, vào Cài đặt → Ứng dụng và dịch vụ → Khởi chạy ứng dụng.\n\nGuard không đọc được trạng thái ba công tắc này nên không tự đánh dấu hoàn tất.")
            .setPositiveButton("Mở cài đặt",(d,w) -> {
                String[] targets={"com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity","com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity","com.huawei.systemmanager.optimize.process.ProtectActivity"};
                for(String cls:targets) if(tryOpen(new Intent().setComponent(new ComponentName("com.huawei.systemmanager",cls)))) return;
                open(new Intent(Settings.ACTION_APPLICATION_SETTINGS));
            }).setNegativeButton("Đóng",null).show();
    }

    private boolean tryOpen(Intent intent) {
        try{startActivity(intent);return true;}catch(android.content.ActivityNotFoundException|SecurityException e){return false;}
    }
    private void open(Intent intent) {if(!tryOpen(intent)) toast("Không mở được mục này trên ROM hiện tại. Hãy mở từ Cài đặt của máy.");}

    private void notificationSettings(String pkg) {
        if(Build.VERSION.SDK_INT>=26 && tryOpen(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,pkg)))return;
        open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+pkg)));
    }

    private boolean notificationsAllowed() {
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return false;
        NotificationManager m=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(m==null || (Build.VERSION.SDK_INT>=24 && !m.areNotificationsEnabled()))return false;
        if(Build.VERSION.SDK_INT>=26 && m.getNotificationChannel(TestReceiver.CHANNEL)!=null)
            return m.getNotificationChannel(TestReceiver.CHANNEL).getImportance()!=NotificationManager.IMPORTANCE_NONE;
        return true;
    }

    private void askNotifications() {
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},10);
        else notificationSettings(getPackageName());
    }

    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants) {
        super.onRequestPermissionsResult(request,permissions,grants);render();
    }

    private PendingIntent testIntent(long at) {
        return PendingIntent.getBroadcast(this,17,new Intent(this,TestReceiver.class).putExtra("expected",at),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }

    private void test(boolean delayed) {
        if(!notificationsAllowed()){askNotifications();toast("Bật quyền rồi bấm thử lại.");return;}
        TestReceiver.channel(this);
        if(!delayed){new TestReceiver().onReceive(this,new Intent().putExtra("expected",System.currentTimeMillis()));render();return;}
        AlarmManager manager=(AlarmManager)getSystemService(ALARM_SERVICE);
        if(manager==null){toast("Không có dịch vụ lập lịch.");return;}
        manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,SystemClock.elapsedRealtime()+120000,testIntent(System.currentTimeMillis()+120000));
        prefs.edit().putString("last_test","Đã đặt lịch sau khoảng 2 phút. Có thể trễ do hệ thống. Chưa có kết quả nhận.").apply();
        toast("Đã đặt lịch thử. Bạn có thể khóa màn hình.");render();
    }

    private void cancelTest() {
        AlarmManager m=(AlarmManager)getSystemService(ALARM_SERVICE);if(m!=null)m.cancel(testIntent(0));
        prefs.edit().putString("last_test","Đã hủy lịch thử.").apply();render();
    }

    private void probeNetwork() {
        if(probeRunning)return;probeRunning=true;render();
        worker.execute(() -> {
            StringBuilder result=new StringBuilder();
            for(int port:new int[]{5228,5229,5230}){
                try(Socket socket=new Socket()){
                    socket.connect(new InetSocketAddress("mtalk.google.com",port),2500);
                    result.append("TCP ").append(port).append(": kết nối được\n");
                }catch(Exception e){result.append("TCP ").append(port).append(": chưa kết nối được (").append(e.getClass().getSimpleName()).append(")\n");}
            }
            result.append("Chưa kiểm tra token hoặc việc nhận thông báo FCM.");
            runOnUiThread(() -> {if(isDestroyed())return;probe=result.toString();probeRunning=false;if(tab.equals("tools"))render();});
        });
    }

    private String report() {
        StringBuilder r=new StringBuilder(DeviceInfo.report(this,emui,harmony));
        r.append("\nThông báo của Guard: ").append(notificationsAllowed()?"Đang bật":"Đang tắt").append('\n');
        r.append("Thử cục bộ: ").append(prefs.getString("last_test","Chưa thử")).append("\n\n").append(probe).append("\n\nCác gói đã chọn:\n");
        for(String pkg:Core.packages(selected))r.append(pkg).append(" — ").append(DeviceInfo.battery(this,pkg)).append('\n');
        return r.toString();
    }

    private void share(String value) {
        try {startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,value),"Chia sẻ báo cáo"));}
        catch(android.content.ActivityNotFoundException e){toast("Máy chưa có ứng dụng nhận chia sẻ.");}
    }

    private void showText(String title,String value) {
        ScrollView scroll=new ScrollView(this); TextView t=new TextView(this);t.setText(value);t.setTextSize(13);t.setTextIsSelectable(true);t.setPadding(dp(18),dp(12),dp(18),dp(12));scroll.addView(t);
        new AlertDialog.Builder(this).setTitle(title).setView(scroll).setPositiveButton("Sao chép",(d,w) -> {
            ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText(title,value));toast("Đã sao chép.");
        }).setNeutralButton("Chia sẻ",(d,w) -> share(value)).setNegativeButton("Đóng",null).show();
    }

    private void export(String value,String name) {
        exportText=value;
        try{startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/plain").putExtra(Intent.EXTRA_TITLE,name),11);}
        catch(android.content.ActivityNotFoundException e){showText(name,value);}
    }

    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==11&&result==RESULT_OK&&data!=null&&data.getData()!=null){
            try(OutputStream out=getContentResolver().openOutputStream(data.getData())){
                if(out==null)throw new java.io.IOException();out.write(exportText.getBytes(StandardCharsets.UTF_8));toast("Đã lưu tệp.");
            }catch(Exception e){toast("Chưa lưu được tệp.");}
        }
    }

    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}

    private static final class AppEntry{
        final String pkg,label;final boolean fcm,exempt;
        AppEntry(String pkg,String label,boolean fcm,boolean exempt){this.pkg=pkg;this.label=label;this.fcm=fcm;this.exempt=exempt;}
    }
}
