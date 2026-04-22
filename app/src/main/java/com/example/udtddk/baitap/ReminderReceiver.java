package com.example.udtddk.baitap;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.google.firebase.database.FirebaseDatabase;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;


public class ReminderReceiver extends BroadcastReceiver {

    public static final String TYPE_WATER  = "water";
    public static final String TYPE_SLEEP  = "sleep";

    private static final int NOTIF_ID_WATER = 3000;
    private static final int NOTIF_ID_SLEEP = 2000;

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (intent == null) return;

        String type      = intent.getStringExtra("type");
        String userId    = intent.getStringExtra("userId");
        String channelId = intent.getStringExtra("channelId");
        int    leadMin   = intent.getIntExtra("leadMinutes", 30);


        if (type == null || type.isEmpty()) return;


        boolean canSave = (userId != null && !userId.isEmpty());

        if (TYPE_WATER.equals(type)) {
            float remain = intent.getFloatExtra("remainWater", 0f);
            fireWaterNotification(ctx, channelId, remain, leadMin, canSave ? userId : null);
        } else if (TYPE_SLEEP.equals(type)) {
            String sleepTime = intent.getStringExtra("sleepTime");
            fireSleepNotification(ctx, channelId, sleepTime, leadMin, canSave ? userId : null);
        }
    }


    private void fireWaterNotification(Context ctx, String channelId,
                                       float remain, int leadMin, String userId) {
        ensureChannel(ctx, channelId, "Nhắc Uống Nước",
                "Thông báo nhắc nhở uống nước định kỳ", Color.CYAN);

        String title = "💧 Đến giờ uống nước rồi!";
        String body;
        if (remain <= 0) {
            body = "Bạn đã uống đủ nước hôm nay. Tiếp tục duy trì thói quen tốt nhé! 🎉";
        } else {
            body = String.format(Locale.getDefault(),
                    "Hãy uống một ly nước ngay bây giờ!\nCòn %.1f lít nữa để đạt mục tiêu 2L hôm nay.",
                    remain);
        }
        if (leadMin > 0) body += "\n(Nhắc trước " + leadMin + " phút)";

        Intent tapIntent = new Intent(ctx, WaterReminderActivity.class);
        tapIntent.putExtra("userId", userId);
        tapIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent tapPi = PendingIntent.getActivity(ctx, NOTIF_ID_WATER, tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder nb = new NotificationCompat.Builder(ctx, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(tapPi)
                .setAutoCancel(true)
                .setColor(Color.parseColor("#155C3A"))
                .setVibrate(new long[]{0, 300, 200, 300})
                .setLights(Color.CYAN, 500, 500);

        notifySystem(ctx, NOTIF_ID_WATER, nb);

        // FIX: luôn truyền đúng type = "water" để HistoryActivity phân loại đúng tab
        saveToThongBao(userId, TYPE_WATER, title, body);
    }


    private void fireSleepNotification(Context ctx, String channelId,
                                       String sleepTime, int leadMin, String userId) {
        ensureChannel(ctx, channelId, "Nhắc Đi Ngủ",
                "Thông báo nhắc nhở giờ đi ngủ", Color.parseColor("#2A5298"));

        String title = "🌙 Sắp đến giờ đi ngủ!";
        String body  = "Hãy chuẩn bị cho giấc ngủ ngon"
                + (sleepTime != null && !sleepTime.isEmpty() ? " – ngủ lúc " + sleepTime : "")
                + ".\nTắt màn hình, dim đèn và thư giãn nhé!";
        if (leadMin > 0) body += "\n(Nhắc trước " + leadMin + " phút)";

        Intent tapIntent = new Intent(ctx, SleepReminderActivity.class);
        tapIntent.putExtra("userId", userId);
        tapIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent tapPi = PendingIntent.getActivity(ctx, NOTIF_ID_SLEEP, tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder nb = new NotificationCompat.Builder(ctx, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(tapPi)
                .setAutoCancel(true)
                .setColor(Color.parseColor("#2A5298"))
                .setVibrate(new long[]{0, 500, 300, 500})
                .setLights(Color.parseColor("#2A5298"), 700, 700);

        notifySystem(ctx, NOTIF_ID_SLEEP, nb);


        saveToThongBao(userId, TYPE_SLEEP, title, body);
    }


    private void saveToThongBao(String userId, String type, String title, String content) {
        // FIX: không lưu nếu userId null để tránh ghi vào node sai
        if (userId == null || userId.isEmpty()) return;

        try {
            Map<String, Object> record = new HashMap<>();
            record.put("title",     title);
            record.put("content",   content);
            record.put("type",      type);   // "water" hoặc "sleep" — bắt buộc có
            record.put("timestamp", System.currentTimeMillis());
            record.put("date",      new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    .format(new Date()));


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


    private void ensureChannel(Context ctx, String id, String name, String desc, int lightColor) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager)
                    ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && nm.getNotificationChannel(id) == null) {
                NotificationChannel ch = new NotificationChannel(
                        id, name, NotificationManager.IMPORTANCE_HIGH);
                ch.setDescription(desc);
                ch.enableLights(true);
                ch.setLightColor(lightColor);
                ch.enableVibration(true);
                nm.createNotificationChannel(ch);
            }
        }
    }

    private void notifySystem(Context ctx, int id, NotificationCompat.Builder nb) {
        NotificationManager nm = (NotificationManager)
                ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(id, nb.build());
    }
}