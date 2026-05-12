package com.example.udtddk.baitap;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.NotificationCompat;

import com.example.udtddk.R;
import com.example.udtddk.history.HistoryActivity;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class WaterReminderActivity extends AppCompatActivity {


    private static final String CHANNEL_ID      = "water_reminder_channel";
    private static final int    ALARM_REQ_CODE  = 3000;
    private static final int    NOTIF_CONFIRM   = 9001;
    private static final float  TARGET_LITERS   = 2.0f;
    private static final int    SAFE_START_HOUR = 5;
    private static final int    SAFE_END_HOUR   = 21;
    private static final float  MIN_INTERVAL    = 0.5f;
    private static final float  MAX_INTERVAL    = 3.0f;

    // ── Views ──────────────────────────────────────────────────────────────────
    private EditText    etWaterLiters, etWaterInterval;
    private TextView    tvWakeTime, tvWaterAdvice, tvWaterWarning,
            tvTimeSuggestion, tvWaterProgress, tvReminderStatus;
    private TextView    tvLeadTimeLabel;
    private Button      btnSetWaterReminder, btnCancelReminder;
    private CardView    cardAdvice;
    private ProgressBar progressWater;

    // ── Chip nhắc trước X phút ─────────────────────────────────────────────────
    private Button btnLead15, btnLead30, btnLead45, btnLead60;
    private int    leadTimeMinutes = 15;

    // ── State ──────────────────────────────────────────────────────────────────
    private int     wakeHour       = 6;
    private int     wakeMinute     = 0;
    private String  userId;
    private boolean reminderActive = false;

    // ══════════════════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_water_reminder);

        createNotificationChannel();
        bindViews();
        requestNotificationPermission();
        attachListeners();
        applyLeadTimeUI(leadTimeMinutes);
        updateAll();
        loadFromHealthMetrics();
        userId = getIntent().getStringExtra("NguoiDungId");

        if (userId == null || userId.isEmpty()) {
            userId = getSharedPreferences("USER", MODE_PRIVATE)
                    .getString("NguoiDungId", null);
        }
        if (userId == null || userId.isEmpty()) {
            Toast.makeText(this, "Không tìm thấy user", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
    }

    // ── Bind views ─────────────────────────────────────────────────────────────
    private void bindViews() {
        // FIX: lấy userId ở đây để đảm bảo luôn có trước khi dùng
        userId              = getIntent().getStringExtra("userId");
        etWaterLiters       = findViewById(R.id.etWaterLiters);
        etWaterInterval     = findViewById(R.id.etWaterInterval);
        tvWakeTime          = findViewById(R.id.tvWakeTime);
        tvWaterAdvice       = findViewById(R.id.tvWaterAdvice);
        tvWaterWarning      = findViewById(R.id.tvWaterWarning);
        tvTimeSuggestion    = findViewById(R.id.tvTimeSuggestion);
        tvWaterProgress     = findViewById(R.id.tvWaterProgress);
        tvReminderStatus    = findViewById(R.id.tvReminderStatus);
        btnSetWaterReminder = findViewById(R.id.btnSetWaterReminder);
        btnCancelReminder   = findViewById(R.id.btnCancelReminder);
        cardAdvice          = findViewById(R.id.cardAdvice);
        progressWater       = findViewById(R.id.progressWater);
        tvLeadTimeLabel     = findViewById(R.id.tvLeadTimeLabel);
        btnLead15           = findViewById(R.id.btnLead15);
        btnLead30           = findViewById(R.id.btnLead30);
        btnLead45           = findViewById(R.id.btnLead45);
        btnLead60           = findViewById(R.id.btnLead60);
    }

    // ── Listeners ──────────────────────────────────────────────────────────────
    private void attachListeners() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Interval quick-select
        findViewById(R.id.btn30min).setOnClickListener(v -> fillInterval("0.5"));
        findViewById(R.id.btn1h)   .setOnClickListener(v -> fillInterval("1"));
        findViewById(R.id.btn2h)   .setOnClickListener(v -> fillInterval("2"));
        findViewById(R.id.btn3h)   .setOnClickListener(v -> fillInterval("3"));

        // Chip nhắc trước
        btnLead15.setOnClickListener(v -> { leadTimeMinutes = 15; applyLeadTimeUI(15); });
        btnLead30.setOnClickListener(v -> { leadTimeMinutes = 30; applyLeadTimeUI(30); });
        btnLead45.setOnClickListener(v -> { leadTimeMinutes = 45; applyLeadTimeUI(45); });
        btnLead60.setOnClickListener(v -> { leadTimeMinutes = 60; applyLeadTimeUI(60); });

        // Wake-time picker
        findViewById(R.id.btnPickWakeTime).setOnClickListener(v ->
                new android.app.TimePickerDialog(this, (view, h, m) -> {
                    wakeHour = h; wakeMinute = m;
                    tvWakeTime.setText(String.format("%02d:%02d", h, m));
                    updateAll();
                }, wakeHour, wakeMinute, true).show()
        );

        android.text.TextWatcher watcher = new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged   (CharSequence s, int a, int b, int c) { updateAll(); }
            public void afterTextChanged(android.text.Editable s) {}
        };
        etWaterLiters  .addTextChangedListener(watcher);
        etWaterInterval.addTextChangedListener(watcher);

        btnSetWaterReminder.setOnClickListener(v -> onSetReminder());

        btnCancelReminder  .setOnClickListener(v -> onCancelReminder());
    }

    // ── Auto-load từ HealthMetrics ─────────────────────────────────────────────
    private void loadFromHealthMetrics() {
        if (userId == null || userId.isEmpty()) return;
        FirebaseDatabase.getInstance("https://udtddk-default-rtdb.firebaseio.com/")
                .getReference("nguoi_dung").child(userId).child("Chi_So_Suc_Khoe")
                .orderByChild("timestamp").limitToLast(1)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snap) {
                        if (!snap.exists()) return;
                        DataSnapshot latest = null;
                        for (DataSnapshot c : snap.getChildren()) latest = c;
                        if (latest == null) return;

                        // Pre-fill lượng nước (ml → L)
                        Object waterObj = latest.child("water").getValue();
                        if (waterObj != null && etWaterLiters.getText().toString().isEmpty()) {
                            try {
                                float waterMl = Float.parseFloat(waterObj.toString());
                                etWaterLiters.setText(String.format(java.util.Locale.getDefault(),
                                        "%.1f", waterMl / 1000f));
                                Toast.makeText(WaterReminderActivity.this,
                                        "💧 Đã lấy lượng nước từ chỉ số sức khoẻ",
                                        Toast.LENGTH_SHORT).show();
                            } catch (NumberFormatException ignored) {}
                        }

                        // Pre-fill giờ thức dậy
                        String wakeStr = latest.child("wakeTime").getValue(String.class);
                        if (wakeStr != null) {
                            try {
                                String[] p = wakeStr.split(":");
                                if (p.length == 2) {
                                    wakeHour   = Integer.parseInt(p[0].trim());
                                    wakeMinute = Integer.parseInt(p[1].trim());
                                    tvWakeTime.setText(wakeStr);
                                    updateAll();
                                }
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                    @Override public void onCancelled(DatabaseError e) {}
                });
    }

    private void fillInterval(String value) { etWaterInterval.setText(value); updateAll(); }

    private void applyLeadTimeUI(int selected) {
        int ac = Color.parseColor("#155C3A"), ic = Color.parseColor("#E8F5E9");
        int at = Color.WHITE,                it = Color.parseColor("#155C3A");
        setChipStyle(btnLead15, selected == 15, ac, ic, at, it);
        setChipStyle(btnLead30, selected == 30, ac, ic, at, it);
        setChipStyle(btnLead45, selected == 45, ac, ic, at, it);
        setChipStyle(btnLead60, selected == 60, ac, ic, at, it);
        if (tvLeadTimeLabel != null)
            tvLeadTimeLabel.setText("⏱️ Nhắc trước " + selected + " phút so với giờ uống");
    }

    private void setChipStyle(Button btn, boolean active, int ac, int ic, int at, int it) {
        if (btn == null) return;
        btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(active ? ac : ic));
        btn.setTextColor(active ? at : it);
    }


    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Nhắc Uống Nước", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Thông báo nhắc nhở uống nước định kỳ");
            ch.enableLights(true);
            ch.setLightColor(Color.CYAN);
            ch.enableVibration(true);
            ch.setVibrationPattern(new long[]{0, 300, 200, 300});
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED)
                requestPermissions(
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
        }
    }


    private void updateAll() {
        float drank    = parseFloat(etWaterLiters  .getText().toString(), 0f);
        float interval = parseFloat(etWaterInterval.getText().toString(), -1f);
        updateProgressBar(drank);
        updateWaterWarning(drank);
        updateTimeSuggestion(interval);
        updateScheduleAdvice(drank, interval);
    }

    private void updateProgressBar(float drank) {
        int pct = (int) Math.min((drank / TARGET_LITERS) * 100f, 100f);
        progressWater.setProgress(pct);
        tvWaterProgress.setText(String.format("%.1f / %.0fL", drank, TARGET_LITERS));
    }

    private void updateWaterWarning(float drank) {
        int currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String msg = null; String color = "#FFF8E1"; String text = "#E65100";
        if (drank == 0f && currentHour >= 12) {
            msg = "🚨 Bạn chưa uống nước hôm nay!\nHãy uống ngay một ly!";
        } else if (drank < 0.5f && currentHour >= 10) {
            msg = "⚠️ Lượng nước rất thấp! Cần ít nhất 2 lít mỗi ngày.";
        } else if (drank >= TARGET_LITERS && drank <= TARGET_LITERS * 1.5f) {
            msg = "✅ Tuyệt vời! Bạn đã uống đủ nước hôm nay!";
            color = "#E8F5E9"; text = "#2E7D32";
        } else if (drank > TARGET_LITERS * 1.5f) {
            msg = "💡 Uống quá nhiều một lúc không tốt – hãy chia đều trong ngày.";
            color = "#E3F2FD"; text = "#1565C0";
        }
        if (msg != null) {
            tvWaterWarning.setBackgroundColor(Color.parseColor(color));
            tvWaterWarning.setTextColor(Color.parseColor(text));
            tvWaterWarning.setText(msg);
            tvWaterWarning.setVisibility(View.VISIBLE);
        } else {
            tvWaterWarning.setVisibility(View.GONE);
        }
    }

    private void updateTimeSuggestion(float interval) {
        StringBuilder sb = new StringBuilder();
        boolean badTime = (wakeHour < SAFE_START_HOUR || wakeHour > SAFE_END_HOUR);
        if (badTime) sb.append("⚠️ Khung giờ ")
                .append(String.format("%02d:%02d", wakeHour, wakeMinute))
                .append(" không phù hợp.\n💡 Nên bắt đầu từ 06:00 – 07:00.\n");
        if (interval > 0) {
            if (interval < MIN_INTERVAL) sb.append("⚠️ Khoảng cách quá ngắn – tối thiểu 30 phút.");
            else if (interval > MAX_INTERVAL) sb.append("⚠️ Khoảng cách quá dài – nên nhắc mỗi 1–2 giờ.");
        }
        if (sb.length() > 0) {
            tvTimeSuggestion.setText(sb.toString().trim());
            tvTimeSuggestion.setVisibility(View.VISIBLE);
        } else {
            tvTimeSuggestion.setVisibility(View.GONE);
        }
    }

    private void updateScheduleAdvice(float drank, float intervalHours) {
        if (intervalHours <= 0) { cardAdvice.setVisibility(View.GONE); return; }
        float remain = Math.max(TARGET_LITERS - drank, 0f);
        StringBuilder sb = new StringBuilder();
        sb.append("📌 Mục tiêu: ").append(TARGET_LITERS).append("L/ngày\n");
        sb.append("✅ Đã uống : ").append(String.format("%.1f", drank)).append("L\n");
        sb.append(remain > 0
                ? "💧 Còn thiếu: " + String.format("%.1f", remain) + "L\n"
                : "🎉 Đã đạt mục tiêu!\n");
        sb.append("\n🕐 Lịch uống nước (nhắc trước ").append(leadTimeMinutes).append(" phút):\n");

        Calendar cur = Calendar.getInstance();
        cur.set(Calendar.HOUR_OF_DAY, wakeHour);
        cur.set(Calendar.MINUTE, wakeMinute);
        cur.set(Calendar.SECOND, 0);
        Calendar end = Calendar.getInstance();
        end.set(Calendar.HOUR_OF_DAY, SAFE_END_HOUR);
        end.set(Calendar.MINUTE, 30);
        long intervalMs = (long) (intervalHours * 60 * 60 * 1000L);
        int count = 0;
        while (!cur.after(end) && count < 12) {
            Calendar remind = (Calendar) cur.clone();
            remind.add(Calendar.MINUTE, -leadTimeMinutes);
            int rh = remind.get(Calendar.HOUR_OF_DAY), rm = remind.get(Calendar.MINUTE);
            int dh = cur   .get(Calendar.HOUR_OF_DAY), dm = cur   .get(Calendar.MINUTE);
            sb.append(String.format("  🔔 Nhắc %02d:%02d → 💧 Uống %02d:%02d\n", rh, rm, dh, dm));
            cur.setTimeInMillis(cur.getTimeInMillis() + intervalMs);
            count++;
        }
        sb.append("\n💡 Mỗi lần uống 150–300ml, không uống ồ ạt.");
        tvWaterAdvice.setText(sb.toString());
        cardAdvice.setVisibility(View.VISIBLE);
    }


    private void onSetReminder() {
        String intervalStr = etWaterInterval.getText().toString().trim();
        if (intervalStr.isEmpty()) {
            Toast.makeText(this, "Nhập khoảng thời gian nhắc", Toast.LENGTH_SHORT).show();
            return;
        }
        float intervalHours = parseFloat(intervalStr, -1f);
        if (intervalHours <= 0) {
            Toast.makeText(this, "Khoảng thời gian phải > 0", Toast.LENGTH_SHORT).show();
            return;
        }

        float drank  = parseFloat(etWaterLiters.getText().toString(), 0f);
        float remain = Math.max(TARGET_LITERS - drank, 0f);

        AlarmManager am  = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = buildAlarmPendingIntent(remain);
        long intervalMs  = (long) (intervalHours * 60 * 60 * 1000L);

        Calendar startCal = Calendar.getInstance();
        startCal.set(Calendar.HOUR_OF_DAY, wakeHour);
        startCal.set(Calendar.MINUTE,      wakeMinute);
        startCal.set(Calendar.SECOND,      0);
        startCal.set(Calendar.MILLISECOND, 0);
        startCal.add(Calendar.MINUTE, -leadTimeMinutes);
        if (startCal.before(Calendar.getInstance())) startCal.add(Calendar.DAY_OF_YEAR, 1);

        if (am != null)
            am.setRepeating(AlarmManager.RTC_WAKEUP,
                    startCal.getTimeInMillis(), intervalMs, pi);

        reminderActive = true;
        updateStatusLabel();


        showConfirmationNotification(remain, intervalHours);

        Toast.makeText(this,
                "💧 Đã đặt nhắc!\nNhắc trước " + leadTimeMinutes + " phút, bắt đầu lúc "
                        + String.format("%02d:%02d", wakeHour, wakeMinute),
                Toast.LENGTH_LONG).show();
        saveWaterToHealthMetrics();
    }


    private void onCancelReminder() {
        AlarmManager am  = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = buildAlarmPendingIntent(0f);
        if (am != null) am.cancel(pi);
        pi.cancel();
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) { nm.cancel(NOTIF_CONFIRM); nm.cancel(ALARM_REQ_CODE); }
        reminderActive = false;
        updateStatusLabel();
        Toast.makeText(this, "🚫 Đã hủy nhắc nhở uống nước!", Toast.LENGTH_SHORT).show();
    }


    private void showConfirmationNotification(float remain, float intervalHours) {
        Intent tapIntent = new Intent(this, HistoryActivity.class);
        tapIntent.putExtra("userId", userId);
        PendingIntent tapPi = PendingIntent.getActivity(this, 0, tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String title = "💧 Nhắc uống nước đã được đặt!";
        String body  = remain <= 0
                ? "Bạn đã uống đủ nước! Nhắc từ sáng mai lúc "
                + String.format("%02d:%02d", wakeHour, wakeMinute)
                : String.format("Còn %.1fL nữa. Nhắc trước %d phút, cứ %s/lần.",
                remain, leadTimeMinutes, formatInterval(intervalHours));

        NotificationCompat.Builder nb = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(tapPi)
                .setAutoCancel(true)
                .setColor(Color.parseColor("#155C3A"));

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF_CONFIRM, nb.build());


        saveConfirmToThongBao(title, body, ReminderReceiver.TYPE_WATER);
    }


    private void saveConfirmToThongBao(String title, String body, String type) {
        if (userId == null || userId.isEmpty()) return;
        try {
            Map<String, Object> record = new HashMap<>();
            record.put("title",     title);
            record.put("content",   body);
            record.put("type",      type);
            record.put("timestamp", System.currentTimeMillis());
            record.put("date",
                    new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                            .format(new java.util.Date()));

            // ✅ PATH ĐÚNG theo JSON thực tế: NguoiDung/{userId}/LicSuMucTieu/thong_bao
            FirebaseDatabase.getInstance("https://udtddk-default-rtdb.firebaseio.com/")
                    .getReference("NguoiDung")          // ← chữ N hoa, đúng với DB
                    .child(userId)
                    .child("LicSuMucTieu")              // ← đúng với JSON
                    .child("thong_bao")
                    .push()
                    .setValue(record);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private PendingIntent buildAlarmPendingIntent(float remain) {
        Intent intent = new Intent(this, ReminderReceiver.class);
        intent.putExtra("type",        ReminderReceiver.TYPE_WATER);
        intent.putExtra("remainWater", remain);
        // FIX: luôn truyền userId vào intent để ReminderReceiver lưu được Firebase
        intent.putExtra("userId",      userId != null ? userId : "");
        intent.putExtra("channelId",   CHANNEL_ID);
        intent.putExtra("leadMinutes", leadTimeMinutes);
        return PendingIntent.getBroadcast(this, ALARM_REQ_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void saveWaterToHealthMetrics() {
        if (userId == null || userId.isEmpty()) {
            Toast.makeText(this, "Chưa có userId", Toast.LENGTH_SHORT).show();
            return;
        }

        float liters = parseFloat(etWaterLiters.getText().toString(), 0f);
        if (liters <= 0) {
            Toast.makeText(this, "Vui lòng nhập lượng nước hợp lệ", Toast.LENGTH_SHORT).show();
            return;
        }

        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .format(new java.util.Date());

        Map<String, Object> data = new HashMap<>();
        data.put("LuongNuoc", liters * 1000);           // lưu theo ml
        data.put("Ngay", System.currentTimeMillis());
        data.put("NgayTao", today);
        data.put("CanNang", parseFloat(etWaterLiters.getText().toString(), 0f)); // tạm dùng liters nếu chưa có

        FirebaseDatabase.getInstance("https://udtddk-default-rtdb.firebaseio.com/")
                .getReference("NguoiDung")
                .child(userId)
                .child("ChiSoSucKhoe")
                .child(today)
                .updateChildren(data)
                .addOnSuccessListener(a -> {
                    Toast.makeText(this, "💧 Đã lưu " + (int)(liters * 1000) + " ml vào chỉ số sức khoẻ", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "❌ Lỗi lưu: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
    private void updateStatusLabel() {
        if (reminderActive) {
            tvReminderStatus.setText("🟢 Đang nhắc – trước " + leadTimeMinutes
                    + " phút, từ " + String.format("%02d:%02d", wakeHour, wakeMinute));
            tvReminderStatus.setTextColor(Color.parseColor("#2E7D32"));
        } else {
            tvReminderStatus.setText("⚫ Chưa có nhắc nhở nào được đặt");
            tvReminderStatus.setTextColor(Color.parseColor("#78909C"));
        }
    }

    private float  parseFloat(String s, float fb) {
        try { return Float.parseFloat(s.trim()); } catch (Exception e) { return fb; }
    }
    private String formatInterval(float h) {
        if (h == 0.5f)    return "30 phút";
        if (h == (int) h) return (int) h + " tiếng";
        return h + " tiếng";
    }
}
