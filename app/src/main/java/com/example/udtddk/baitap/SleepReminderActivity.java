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
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.NotificationCompat;

import com.example.udtddk.R;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class SleepReminderActivity extends AppCompatActivity {

    // ── Constants ──────────────────────────────────────────────────────────────
    private static final String CHANNEL_ID         = "sleep_reminder_channel";
    private static final int    ALARM_REQ_CODE     = 2000;
    private static final int    NOTIF_CONFIRM      = 9002;
    private static final int    OPTIMAL_START_HOUR = 21;
    private static final int    OPTIMAL_END_HOUR   = 22;
    private static final float  MIN_HOURS          = 6.0f;
    private static final float  OPTIMAL_MIN        = 7.0f;
    private static final float  OPTIMAL_MAX        = 9.0f;
    private static final float  MAX_HOURS          = 10.0f;


    private EditText  etSleepHours;
    private TextView  tvSleepTime, tvWakeUpTime, tvSleepAdvice,
            tvDurationWarning, tvTimeSuggestion, tvReminderStatus;
    private TextView  tvLeadTimeLabel;
    private Button    btnSetSleepReminder, btnCancelReminder;
    private CardView  cardAdvice;


    private Button btnLead15, btnLead30, btnLead45, btnLead60;
    private int    reminderBeforeMinutes = 30;


    private int     sleepHour      = 22;
    private int     sleepMinute    = 0;
    private String  userId;
    private boolean reminderActive = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sleep_reminder);


        userId = getIntent().getStringExtra("userId");

        createNotificationChannel();
        bindViews();
        requestNotificationPermission();
        attachListeners();
        applyLeadTimeUI(reminderBeforeMinutes);
        updateSleepDisplay();
        loadFromHealthMetrics();
    }


    private void bindViews() {
        etSleepHours        = findViewById(R.id.etSleepHours);
        tvSleepTime         = findViewById(R.id.tvSleepTime);
        tvWakeUpTime        = findViewById(R.id.tvWakeUpTime);
        tvSleepAdvice       = findViewById(R.id.tvSleepAdvice);
        tvDurationWarning   = findViewById(R.id.tvDurationWarning);
        tvTimeSuggestion    = findViewById(R.id.tvTimeSuggestion);
        tvReminderStatus    = findViewById(R.id.tvReminderStatus);
        btnSetSleepReminder = findViewById(R.id.btnSetSleepReminder);
        btnCancelReminder   = findViewById(R.id.btnCancelReminder);
        cardAdvice          = findViewById(R.id.cardAdvice);
        tvLeadTimeLabel     = findViewById(R.id.tvLeadTimeLabel);
        btnLead15           = findViewById(R.id.btnLead15);
        btnLead30           = findViewById(R.id.btnLead30);
        btnLead45           = findViewById(R.id.btnLead45);
        btnLead60           = findViewById(R.id.btnLead60);
    }


    private void attachListeners() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());


        btnLead15.setOnClickListener(v -> { reminderBeforeMinutes = 15; applyLeadTimeUI(15); updateStatusLabel(); });
        btnLead30.setOnClickListener(v -> { reminderBeforeMinutes = 30; applyLeadTimeUI(30); updateStatusLabel(); });
        btnLead45.setOnClickListener(v -> { reminderBeforeMinutes = 45; applyLeadTimeUI(45); updateStatusLabel(); });
        btnLead60.setOnClickListener(v -> { reminderBeforeMinutes = 60; applyLeadTimeUI(60); updateStatusLabel(); });


        findViewById(R.id.btnPickSleepTime).setOnClickListener(v ->
                new android.app.TimePickerDialog(this, (view, h, m) -> {
                    sleepHour = h; sleepMinute = m;
                    updateSleepDisplay();
                }, sleepHour, sleepMinute, true).show()
        );


        setQuickSleep(R.id.btnSleep21,   21, 0);
        setQuickSleep(R.id.btnSleep2130, 21, 30);
        setQuickSleep(R.id.btnSleep22,   22, 0);
        setQuickSleep(R.id.btnSleep2230, 22, 30);

        // Thời lượng nhanh
        setQuickDuration(R.id.btn6h, "6");
        setQuickDuration(R.id.btn7h, "7");
        setQuickDuration(R.id.btn8h, "8");
        setQuickDuration(R.id.btn9h, "9");

        // Watcher thời lượng
        etSleepHours.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged   (CharSequence s, int a, int b, int c) { updateSleepDisplay(); }
            public void afterTextChanged(android.text.Editable s) {}
        });

        btnSetSleepReminder.setOnClickListener(v -> onSetReminder());
        btnCancelReminder  .setOnClickListener(v -> onCancelReminder());
    }

    private void setQuickSleep(int btnId, int h, int m) {
        Button b = findViewById(btnId);
        if (b != null) b.setOnClickListener(v -> { sleepHour = h; sleepMinute = m; updateSleepDisplay(); });
    }

    private void setQuickDuration(int btnId, String hours) {
        Button b = findViewById(btnId);
        if (b != null) b.setOnClickListener(v -> {
            etSleepHours.setText(hours);
            updateSleepDisplay();
        });
    }


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

                        boolean filled = false;

                        // Giờ ngủ
                        try {
                            String sleepStr = latest.child("sleepTime").getValue(String.class);
                            if (sleepStr != null) {
                                String[] p = sleepStr.split(":");
                                if (p.length == 2) {
                                    sleepHour   = Integer.parseInt(p[0].trim());
                                    sleepMinute = Integer.parseInt(p[1].trim());
                                    filled = true;
                                }
                            }
                        } catch (NumberFormatException e) {
                            e.printStackTrace();
                        }
                        Object durObj = latest.child("sleepDuration").getValue();
                        if (durObj != null && etSleepHours.getText().toString().isEmpty()) {
                            try {
                                float dur = Float.parseFloat(durObj.toString());
                                etSleepHours.setText(String.format(
                                        java.util.Locale.getDefault(), "%.1f", dur));
                            } catch (NumberFormatException e) {
                                e.printStackTrace();
                            }
                        }

                        if (filled) {
                            updateSleepDisplay();
                            Toast.makeText(SleepReminderActivity.this,
                                    "🌙 Đã lấy giờ ngủ từ chỉ số sức khoẻ của bạn",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override public void onCancelled(DatabaseError e) {}
                });
    }


    private void applyLeadTimeUI(int selected) {
        int ac = Color.parseColor("#2A5298"), ic = Color.parseColor("#1E3550");
        int at = Color.WHITE,                it = Color.parseColor("#A8C8E8");
        setChipStyle(btnLead15, selected == 15, ac, ic, at, it);
        setChipStyle(btnLead30, selected == 30, ac, ic, at, it);
        setChipStyle(btnLead45, selected == 45, ac, ic, at, it);
        setChipStyle(btnLead60, selected == 60, ac, ic, at, it);
        if (tvLeadTimeLabel != null)
            tvLeadTimeLabel.setText("🔔 Nhắc trước " + selected + " phút so với giờ ngủ");
    }

    private void setChipStyle(Button btn, boolean active, int ac, int ic, int at, int it) {
        if (btn == null) return;
        btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(active ? ac : ic));
        btn.setTextColor(active ? at : it);
    }

    // ── Cập nhật hiển thị giờ ngủ ─────────────────────────────────────────────
    private void updateSleepDisplay() {
        tvSleepTime.setText(String.format("%02d:%02d", sleepHour, sleepMinute));
        float hours = parseFloat(etSleepHours.getText().toString(), -1f);
        if (hours > 0) {
            Calendar wake = Calendar.getInstance();
            wake.set(Calendar.HOUR_OF_DAY, sleepHour);
            wake.set(Calendar.MINUTE,      sleepMinute);
            wake.add(Calendar.MINUTE, (int) (hours * 60));
            tvWakeUpTime.setText(String.format("%02d:%02d",
                    wake.get(Calendar.HOUR_OF_DAY), wake.get(Calendar.MINUTE)));
            updateDurationWarning(hours);
            updateTimeSuggestion();
            buildSleepAdvice(hours);
        } else {
            tvWakeUpTime.setText("--:--");
            tvDurationWarning.setVisibility(View.GONE);
            tvTimeSuggestion .setVisibility(View.GONE);
        }
        updateStatusLabel();
    }

    private void updateDurationWarning(float hours) {
        String msg = null;
        if      (hours < MIN_HOURS)   msg = "⚠️ " + String.format("%.1f", hours) + " tiếng quá ít – ngủ tối thiểu 6 tiếng để phục hồi cơ thể.";
        else if (hours < OPTIMAL_MIN) msg = "💡 " + String.format("%.1f", hours) + " tiếng – OK nhưng lý tưởng là 7–9 tiếng.";
        else if (hours > MAX_HOURS)   msg = "⚠️ " + String.format("%.1f", hours) + " tiếng quá nhiều – ngủ >10 tiếng thường xuyên không tốt.";
        else if (hours > OPTIMAL_MAX) msg = "💡 " + String.format("%.1f", hours) + " tiếng – hơi nhiều, thử giảm xuống 7–9 tiếng.";
        if (msg != null) { tvDurationWarning.setText(msg); tvDurationWarning.setVisibility(View.VISIBLE); }
        else             { tvDurationWarning.setVisibility(View.GONE); }
    }

    private void updateTimeSuggestion() {
        StringBuilder sb = new StringBuilder();
        if      (sleepHour < OPTIMAL_START_HOUR) sb.append("💡 Ngủ trước 21:00 có thể dậy muộn hơn lý tưởng.\n");
        else if (sleepHour <= OPTIMAL_END_HOUR)  sb.append("✅ Khung giờ 21:00–22:59 – rất tốt cho giấc ngủ!\n");
        else if (sleepHour == 23)                sb.append("⚠️ 23:xx – hơi muộn, cố gắng ngủ trước 23:00.\n");
        else                                     sb.append("🚨 Sau 00:00 – quá muộn, ảnh hưởng lớn tới sức khoẻ!\n");

        int remindH = sleepHour, remindM = sleepMinute - reminderBeforeMinutes;
        if (remindM < 0) { remindM += 60; remindH = (remindH - 1 + 24) % 24; }
        sb.append("🔔 Sẽ nhắc lúc ").append(String.format("%02d:%02d", remindH, remindM))
                .append(" (trước ").append(reminderBeforeMinutes).append(" phút)");

        tvTimeSuggestion.setText(sb.toString().trim());
        tvTimeSuggestion.setVisibility(View.VISIBLE);
    }

    private void buildSleepAdvice(float hours) {
        if (cardAdvice == null) return;
        int cycles = (int) (hours / 1.5f);
        String hoursStr = String.format("%.1f", hours);
        StringBuilder sb = new StringBuilder();
        sb.append("😴 Giờ ngủ: ").append(String.format("%02d:%02d", sleepHour, sleepMinute)).append("\n");
        sb.append("⏱️ Thời lượng: ").append(hoursStr).append(" tiếng → ").append(cycles).append(" chu kỳ ngủ\n");
        if      (hours < OPTIMAL_MIN)  sb.append("   → Thiếu ngủ – hãy cố thêm ít nhất 1 tiếng\n");
        else if (hours <= OPTIMAL_MAX) sb.append("   → Lý tưởng – đủ giấc phục hồi tốt\n");
        else                           sb.append("   → Hơi nhiều, thử dậy sớm hơn 30–60 phút\n");
        if      (cycles >= 5) sb.append("   → Tốt! Đủ để phục hồi cơ thể\n");
        else if (cycles >= 4) sb.append("   → Tạm ổn, cố thêm 1 chu kỳ nữa\n");
        else                  sb.append("   → Ít quá, cố ngủ đủ 5 chu kỳ (7.5 tiếng)\n");
        sb.append("\n💡 Chuẩn bị trước khi ngủ:\n");
        sb.append("• Tắt màn hình điện thoại 30 phút trước giờ ngủ\n");
        sb.append("• Giữ phòng tối, mát (18–22°C) và yên tĩnh\n");
        sb.append("• Tránh caffeine sau 15:00\n");
        sb.append("• Ngủ và dậy đúng giờ mỗi ngày, kể cả cuối tuần");
        tvSleepAdvice.setText(sb.toString());
        cardAdvice.setVisibility(View.VISIBLE);
    }

    // ── Đặt nhắc nhở ──────────────────────────────────────────────────────────
    private void onSetReminder() {
        String hoursStr = etSleepHours.getText().toString().trim();
        if (hoursStr.isEmpty()) {
            Toast.makeText(this, "Nhập số tiếng muốn ngủ", Toast.LENGTH_SHORT).show();
            return;
        }
        float hours = parseFloat(hoursStr, -1f);
        if (hours <= 0) {
            Toast.makeText(this, "Số tiếng ngủ phải > 0", Toast.LENGTH_SHORT).show();
            return;
        }

        Calendar remindCal = Calendar.getInstance();
        remindCal.set(Calendar.HOUR_OF_DAY, sleepHour);
        remindCal.set(Calendar.MINUTE,      sleepMinute);
        remindCal.set(Calendar.SECOND,      0);
        remindCal.set(Calendar.MILLISECOND, 0);
        remindCal.add(Calendar.MINUTE, -reminderBeforeMinutes);
        if (remindCal.before(Calendar.getInstance())) remindCal.add(Calendar.DAY_OF_YEAR, 1);

        AlarmManager am  = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = buildAlarmPendingIntent();
        if (am != null)
            am.setRepeating(AlarmManager.RTC_WAKEUP,
                    remindCal.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pi);

        reminderActive = true;
        updateStatusLabel();

        // Thông báo xác nhận ngay + lưu vào thong_bao
        showConfirmationNotification(hours);

        String remindStr = String.format("%02d:%02d",
                remindCal.get(Calendar.HOUR_OF_DAY), remindCal.get(Calendar.MINUTE));
        Toast.makeText(this,
                "🌙 Đã đặt nhắc lúc " + remindStr + " (trước " + reminderBeforeMinutes + " phút)",
                Toast.LENGTH_LONG).show();
        saveSleepToHealthMetrics();
    }

    // ── Huỷ nhắc nhở ──────────────────────────────────────────────────────────
    private void onCancelReminder() {
        AlarmManager am  = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = buildAlarmPendingIntent();
        if (am != null) am.cancel(pi);
        pi.cancel();
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) { nm.cancel(NOTIF_CONFIRM); nm.cancel(ALARM_REQ_CODE); }
        reminderActive = false;
        updateStatusLabel();
        Toast.makeText(this, "🚫 Đã hủy nhắc nhở đi ngủ!", Toast.LENGTH_SHORT).show();
    }

    // ── Thông báo xác nhận đặt nhắc ───────────────────────────────────────────
    // FIX: thông báo xác nhận lưu vào thong_bao với type="sleep" → hiện đúng tab
    private void showConfirmationNotification(float hours) {
        Calendar wake = Calendar.getInstance();
        wake.set(Calendar.HOUR_OF_DAY, sleepHour);
        wake.set(Calendar.MINUTE,      sleepMinute);
        wake.add(Calendar.MINUTE, (int) (hours * 60));
        String wakeStr = String.format("%02d:%02d",
                wake.get(Calendar.HOUR_OF_DAY), wake.get(Calendar.MINUTE));

        int remindH = sleepHour, remindM = sleepMinute - reminderBeforeMinutes;
        if (remindM < 0) { remindM += 60; remindH = (remindH - 1 + 24) % 24; }

        String title = "🌙 Nhắc đi ngủ đã được đặt!";
        String body  = String.format(
                "Sẽ nhắc lúc %02d:%02d (trước %d phút)\nNgủ %02d:%02d → Thức %s · %.0f tiếng",
                remindH, remindM, reminderBeforeMinutes,
                sleepHour, sleepMinute, wakeStr, hours);

        Intent tapIntent = new Intent(this, SleepReminderActivity.class);
        tapIntent.putExtra("userId", userId);
        PendingIntent tapPi = PendingIntent.getActivity(this, 0, tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder nb = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(tapPi)
                .setAutoCancel(true)
                .setColor(Color.parseColor("#2A5298"));

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF_CONFIRM, nb.build());

        // FIX: lưu xác nhận vào thong_bao với type="sleep" để hiện ngay trong tab Thông báo
        saveConfirmToThongBao(title, body, ReminderReceiver.TYPE_SLEEP);
    }

    // FIX: hàm lưu xác nhận vào Firebase thong_bao
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
            FirebaseDatabase.getInstance("https://udtddk-default-rtdb.firebaseio.com/")
                    .getReference("nguoi_dung")
                    .child(userId)
                    .child("lich_su")
                    .child("thong_bao")
                    .push()
                    .setValue(record);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private PendingIntent buildAlarmPendingIntent() {
        Intent intent = new Intent(this, ReminderReceiver.class);
        intent.putExtra("type",        ReminderReceiver.TYPE_SLEEP);
        intent.putExtra("sleepTime",   String.format("%02d:%02d", sleepHour, sleepMinute));
        // FIX: luôn truyền userId để ReminderReceiver lưu được Firebase
        intent.putExtra("userId",      userId != null ? userId : "");
        intent.putExtra("channelId",   CHANNEL_ID);
        intent.putExtra("leadMinutes", reminderBeforeMinutes);
        return PendingIntent.getBroadcast(this, ALARM_REQ_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void updateStatusLabel() {
        int remindH = sleepHour, remindM = sleepMinute - reminderBeforeMinutes;
        if (remindM < 0) { remindM += 60; remindH = (remindH - 1 + 24) % 24; }
        if (reminderActive) {
            tvReminderStatus.setText("🟢 Đang hoạt động – nhắc lúc "
                    + String.format("%02d:%02d", remindH, remindM)
                    + " (trước " + reminderBeforeMinutes + " phút)");
            tvReminderStatus.setTextColor(Color.parseColor("#4CAF50"));
        } else {
            tvReminderStatus.setText("⚫ Chưa có nhắc nhở nào được đặt");
            tvReminderStatus.setTextColor(Color.parseColor("#5A7A9A"));
        }
    }
    private void saveSleepToHealthMetrics() {
        if (userId == null || userId.isEmpty()) return;

        float hours = parseFloat(etSleepHours.getText().toString(), 0f);
        if (hours <= 0) return;

        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .format(new java.util.Date());

        Map<String, Object> data = new HashMap<>();
        data.put("ThoiGianNgu", hours);
        data.put("Ngay", System.currentTimeMillis());
        data.put("NgayTao", today);

        FirebaseDatabase.getInstance("https://udtddk-default-rtdb.firebaseio.com/")
                .getReference("NguoiDung")
                .child(userId)
                .child("ChiSoSucKhoe")
                .child(today)
                .updateChildren(data)
                .addOnSuccessListener(a ->
                        Toast.makeText(this, "🌙 Đã lưu " + hours + " giờ ngủ", Toast.LENGTH_SHORT).show());
    }
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Nhắc Đi Ngủ", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Thông báo nhắc nhở giờ đi ngủ");
            ch.enableLights(true);
            ch.setLightColor(Color.parseColor("#2A5298"));
            ch.enableVibration(true);
            ch.setVibrationPattern(new long[]{0, 500, 300, 500});
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED)
                requestPermissions(
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 102);
        }
    }

    private float parseFloat(String s, float fb) {
        try { return Float.parseFloat(s.trim()); } catch (Exception e) { return fb; }
    }
}