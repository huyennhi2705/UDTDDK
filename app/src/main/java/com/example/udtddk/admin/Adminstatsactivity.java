package com.example.udtddk.admin;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.udtddk.R;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Adminstatsactivity extends AppCompatActivity {

    private static final String DB_URL = "https://udtddk-default-rtdb.firebaseio.com/";

    private TextView tvStatTotal, tvStatAvgBmi, tvStatWorkout;
    private LinearLayout barContainer;
    private Button btnReport;
    private ImageView btnBack;
    private DatabaseReference workoutRef;

    private DatabaseReference userRef;
    private int totalWorkouts = 0;
    private int total, under, normal, over, obese, noData;
    private double avgBmi;
    private String reportDate = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_adminstatsactivity);

        tvStatTotal   = findViewById(R.id.tvStatTotal);
        tvStatAvgBmi  = findViewById(R.id.tvStatAvgBmi);
        barContainer  = findViewById(R.id.barContainer);
        btnReport     = findViewById(R.id.btnReport);
        btnBack       = findViewById(R.id.btnBack);
        tvStatWorkout = findViewById(R.id.tvStatWorkout);

        reportDate = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date());

        userRef    = FirebaseDatabase.getInstance(DB_URL).getReference("NguoiDung");
        workoutRef = FirebaseDatabase.getInstance(DB_URL).getReference("BaiTap");

        btnBack.setOnClickListener(v -> finish());
        btnReport.setOnClickListener(v -> showReportDialog());

        loadStats();
        loadWorkoutCount();
    }

    // ─────────────────────────────────────────
    // ĐẾM BÀI TẬP – đúng với CSDL flat list
    // ─────────────────────────────────────────
    private void loadWorkoutCount() {
        workoutRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                totalWorkouts = 0;

                for (DataSnapshot child : snapshot.getChildren()) {
                    // Bỏ qua node _schema (định nghĩa cột, không phải bài tập thực)
                    if ("_schema".equals(child.getKey())) continue;

                    // Mỗi child trực tiếp của BaiTap là 1 bài tập
                    // Kiểm tra có field TenBaiTap để chắc chắn đây là bài tập hợp lệ
                    if (child.hasChild("TenBaiTap")) {
                        totalWorkouts++;
                    }
                }

                updateUI();
            }

            @Override
            public void onCancelled(DatabaseError error) {}
        });
    }

    // ─────────────────────────────────────────
    // LOAD & TÍNH TOÁN NGƯỜI DÙNG
    // ─────────────────────────────────────────
    private void loadStats() {
        userRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {

                total = 0; under = 0; normal = 0;
                over  = 0; obese = 0; noData  = 0;
                double sumBmi = 0;

                for (DataSnapshot ds : snapshot.getChildren()) {
                    // Bỏ qua node _schema
                    if ("_schema".equals(ds.getKey())) continue;

                    // Chỉ tính node có thông tin người dùng thực
                    if (!ds.hasChild("HoTen") && !ds.hasChild("hoTen")) continue;

                    total++;

                    Object bmiObj = ds.child("BMI").getValue();
                    if (bmiObj == null) bmiObj = ds.child("bmi").getValue();

                    double bmi = 0;

                    if (bmiObj != null) {
                        try { bmi = Double.parseDouble(bmiObj.toString()); }
                        catch (Exception ignored) {}
                    }

                    // Nếu chưa có BMI → tự tính từ cân nặng & chiều cao
                    if (bmi <= 0) {
                        Object weightObj = ds.child("CanNang").getValue();
                        if (weightObj == null) weightObj = ds.child("canNang").getValue();

                        Object heightObj = ds.child("ChieuCao").getValue();
                        if (heightObj == null) heightObj = ds.child("chieuCao").getValue();

                        if (weightObj != null && heightObj != null) {
                            try {
                                double weight = Double.parseDouble(weightObj.toString());
                                double height = Double.parseDouble(heightObj.toString());
                                if (height > 0 && weight > 0) {
                                    double h = height / 100.0;
                                    bmi = weight / (h * h);
                                }
                            } catch (Exception ignored) {}
                        }
                    }

                    if (bmi <= 0) {
                        noData++;
                    } else {
                        sumBmi += bmi;
                        if (bmi < 18.5)      under++;
                        else if (bmi < 23)   normal++;
                        else if (bmi < 25)   over++;
                        else                 obese++;
                    }
                }

                int withData = total - noData;
                avgBmi = withData > 0 ? sumBmi / withData : 0;

                updateUI();
            }

            @Override
            public void onCancelled(DatabaseError error) {}
        });
    }

    // ─────────────────────────────────────────
    // CẬP NHẬT UI
    // ─────────────────────────────────────────
    private void updateUI() {
        tvStatTotal.setText(String.valueOf(total));
        tvStatWorkout.setText(String.valueOf(totalWorkouts));
        tvStatAvgBmi.setText(avgBmi > 0
                ? String.format(Locale.getDefault(), "%.1f", avgBmi) : "--");
        drawBarChart();
    }

    // ─────────────────────────────────────────
    // BAR CHART
    // ─────────────────────────────────────────
    private void drawBarChart() {
        barContainer.removeAllViews();

        if (total == 0) {
            TextView empty = new TextView(this);
            empty.setText("Chưa có dữ liệu");
            empty.setTextColor(Color.GRAY);
            barContainer.addView(empty);
            return;
        }

        String[] labels = {"🔵 Thiếu cân", "🟢 Bình thường", "🟡 Thừa cân", "🔴 Béo phì", "⚪ Chưa có data"};
        int[]    counts = {under, normal, over, obese, noData};
        String[] colors = {"#5BBFFA", "#2E8B57", "#F9A825", "#E53935", "#9E9E9E"};

        for (int i = 0; i < labels.length; i++) {
            float percent = (float) counts[i] / total;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);

            TextView tvTop = new TextView(this);
            tvTop.setText(labels[i] + "  (" + counts[i] + " người - "
                    + String.format(Locale.getDefault(), "%.1f%%", percent * 100) + ")");
            tvTop.setTextSize(13f);
            tvTop.setTypeface(null, Typeface.BOLD);
            tvTop.setTextColor(Color.parseColor("#0F2D1F"));
            row.addView(tvTop);

            LinearLayout barBg = new LinearLayout(this);
            barBg.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(20)));
            barBg.setBackgroundColor(Color.parseColor("#EEEEEE"));
            barBg.setPadding(2, 2, 2, 2);

            View bar = new View(this);
            bar.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.MATCH_PARENT, percent));
            bar.setBackgroundColor(Color.parseColor(colors[i]));
            barBg.addView(bar);

            row.addView(barBg);
            row.setPadding(0, 0, 0, dp(10));
            barContainer.addView(row);
        }
    }

    // ─────────────────────────────────────────
    // DIALOG BÁO CÁO
    // ─────────────────────────────────────────
    private void showReportDialog() {
        if (total == 0) {
            Toast.makeText(this, "Chưa có dữ liệu để tạo báo cáo", Toast.LENGTH_SHORT).show();
            return;
        }

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#F4F8F6"));
        scroll.setPadding(dp(16), dp(16), dp(16), dp(16));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        scroll.addView(root);

        LinearLayout header = makeCard("#1B5E20");
        header.setGravity(Gravity.CENTER);
        header.setPadding(dp(20), dp(20), dp(20), dp(20));
        addTextTo(header, "📋 BÁO CÁO THỐNG KÊ", 18f, Color.WHITE, Typeface.BOLD, Gravity.CENTER, 0);
        addTextTo(header, "Hệ thống UDTDDK – Admin", 11f, Color.parseColor("#A5D6A7"), Typeface.NORMAL, Gravity.CENTER, dp(4));
        addTextTo(header, "🕐 " + reportDate, 11f, Color.parseColor("#C8E6C9"), Typeface.NORMAL, Gravity.CENTER, dp(6));
        root.addView(header);
        root.addView(spacer(12));

        root.addView(makeSectionLabel("👥 TỔNG QUAN"));
        root.addView(spacer(6));

        int withData = total - noData;
        LinearLayout row2 = makeHRow();
        row2.addView(makeMiniCard("Tổng người dùng", String.valueOf(total),    "👥", "#1565C0", "#E3F2FD", 1f));
        row2.addView(spacer2(10));
        row2.addView(makeMiniCard("Có dữ liệu BMI",  String.valueOf(withData), "📊", "#2E7D32", "#E8F5E9", 1f));
        root.addView(row2);
        root.addView(spacer(8));

        LinearLayout row3 = makeHRow();
        row3.addView(makeMiniCard("Chưa có BMI",  String.valueOf(noData),       "⚪", "#757575", "#F5F5F5", 1f));
        row3.addView(spacer2(10));
        row3.addView(makeMiniCard("Tổng bài tập", String.valueOf(totalWorkouts),"🏋️", "#6A1B9A", "#F3E5F5", 1f));
        root.addView(row3);
        root.addView(spacer(8));

        LinearLayout row4 = makeHRow();
        row4.addView(makeMiniCard("BMI trung bình",
                avgBmi > 0 ? String.format(Locale.getDefault(), "%.1f", avgBmi) : "--",
                "⚖️", "#E65100", "#FFF3E0", 1f));
        root.addView(row4);
        root.addView(spacer(14));

        root.addView(makeSectionLabel("📊 PHÂN LOẠI BMI"));
        root.addView(spacer(8));

        String[] bmiLabels   = {"🔵 Thiếu cân", "🟢 Bình thường", "🟡 Thừa cân", "🔴 Béo phì", "⚪ Chưa có data"};
        int[]    bmiCounts   = {under, normal, over, obese, noData};
        String[] bmiColors   = {"#1565C0", "#2E7D32", "#F57F17", "#C62828", "#616161"};
        String[] bmiBgColors = {"#E3F2FD", "#E8F5E9", "#FFFDE7", "#FFEBEE", "#F5F5F5"};

        int maxBmiVal = 0;
        for (int c : bmiCounts) if (c > maxBmiVal) maxBmiVal = c;
        if (maxBmiVal == 0) maxBmiVal = 1;

        LinearLayout classCard = makeCard("#FFFFFF");
        classCard.setPadding(dp(16), dp(14), dp(16), dp(14));

        for (int i = 0; i < bmiLabels.length; i++) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams itemP = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            if (i > 0) itemP.topMargin = dp(10);
            item.setLayoutParams(itemP);

            LinearLayout labelRow = makeHRow();
            labelRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView tvL = new TextView(this);
            tvL.setText(bmiLabels[i]);
            tvL.setTextSize(13f);
            tvL.setTextColor(Color.parseColor("#212121"));
            tvL.setTypeface(null, Typeface.BOLD);
            tvL.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            labelRow.addView(tvL);

            TextView tvBadge = new TextView(this);
            tvBadge.setText(bmiCounts[i] + " người");
            tvBadge.setTextSize(12f);
            tvBadge.setTextColor(Color.parseColor(bmiColors[i]));
            tvBadge.setTypeface(null, Typeface.BOLD);
            tvBadge.setBackgroundColor(Color.parseColor(bmiBgColors[i]));
            tvBadge.setPadding(dp(10), dp(4), dp(10), dp(4));
            tvBadge.setGravity(Gravity.CENTER);
            labelRow.addView(tvBadge);
            item.addView(labelRow);

            LinearLayout barBg = new LinearLayout(this);
            barBg.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams barBgP = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(8));
            barBgP.topMargin = dp(5);
            barBg.setLayoutParams(barBgP);
            barBg.setBackgroundColor(Color.parseColor("#EEEEEE"));

            View barFill = new View(this);
            float ratio = (float) bmiCounts[i] / maxBmiVal;
            barFill.setLayoutParams(new LinearLayout.LayoutParams(0, dp(8), ratio));
            barFill.setBackgroundColor(Color.parseColor(bmiColors[i]));
            barBg.addView(barFill);
            item.addView(barBg);

            double pctVal = pct(bmiCounts[i], total);
            TextView tvPct = new TextView(this);
            tvPct.setText(String.format(Locale.getDefault(), "%.1f%% trong tổng số", pctVal));
            tvPct.setTextSize(10f);
            tvPct.setTextColor(Color.parseColor("#9E9E9E"));
            LinearLayout.LayoutParams pctP = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            pctP.topMargin = dp(2);
            tvPct.setLayoutParams(pctP);
            item.addView(tvPct);
            classCard.addView(item);

            if (i < bmiLabels.length - 1) {
                View divider = new View(this);
                LinearLayout.LayoutParams divP = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
                divP.topMargin = dp(10);
                divider.setLayoutParams(divP);
                divider.setBackgroundColor(Color.parseColor("#F0F0F0"));
                classCard.addView(divider);
            }
        }

        root.addView(classCard);
        root.addView(spacer(20));

        LinearLayout btnRow = makeHRow();
        btnRow.setGravity(Gravity.CENTER);

        Button btnExportPdf = new Button(this);
        btnExportPdf.setText("📥 Xuất PDF");
        btnExportPdf.setTextColor(Color.WHITE);
        btnExportPdf.setTypeface(null, Typeface.BOLD);
        btnExportPdf.setBackgroundColor(Color.parseColor("#1565C0"));
        btnExportPdf.setPadding(dp(24), dp(10), dp(24), dp(10));
        LinearLayout.LayoutParams pdfBtnP = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        pdfBtnP.setMarginEnd(dp(8));
        btnExportPdf.setLayoutParams(pdfBtnP);

        Button btnCopy = new Button(this);
        btnCopy.setText("📋 Sao chép");
        btnCopy.setTextColor(Color.parseColor("#1565C0"));
        btnCopy.setTypeface(null, Typeface.BOLD);
        btnCopy.setBackgroundColor(Color.parseColor("#E3F2FD"));
        btnCopy.setPadding(dp(24), dp(10), dp(24), dp(10));
        btnCopy.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        btnRow.addView(btnExportPdf);
        btnRow.addView(btnCopy);
        root.addView(btnRow);
        root.addView(spacer(4));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(scroll)
                .create();
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        btnExportPdf.setOnClickListener(v -> { dialog.dismiss(); exportToPdf(); });
        btnCopy.setOnClickListener(v -> {
            android.content.ClipboardManager cm =
                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(android.content.ClipData.newPlainText("Báo cáo BMI", buildReportText()));
            Toast.makeText(this, "Đã sao chép báo cáo", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
        if (dialog.getWindow() != null)
            dialog.getWindow().setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.93f),
                    android.view.WindowManager.LayoutParams.WRAP_CONTENT);
    }

    // ─────────────────────────────────────────
    // XUẤT PDF
    // ─────────────────────────────────────────
    private void exportToPdf() {
        String html = buildHtmlReport();
        WebView webView = new WebView(this);
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                PrintManager pm = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                String jobName = "BaoCao_Admin_"
                        + new SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(new Date());
                pm.print(jobName,
                        view.createPrintDocumentAdapter(jobName),
                        new PrintAttributes.Builder().build());
            }
        });
    }

    // ─────────────────────────────────────────
    // BUILD HTML BÁO CÁO
    // ─────────────────────────────────────────
    private String buildHtmlReport() {
        int withData = total - noData;
        String avgStr = avgBmi > 0 ? String.format(Locale.getDefault(), "%.2f", avgBmi) : "--";

        String[] bmiLabels = {"Thieu can (BMI < 18.5)", "Binh thuong (18.5–22.9)",
                "Thua can (23.0–24.9)", "Beo phi (BMI >= 25)", "Chua co du lieu"};
        int[]    bmiCounts = {under, normal, over, obese, noData};
        String[] bmiColors = {"#1565C0", "#2E7D32", "#F57F17", "#C62828", "#616161"};
        String[] bmiLight  = {"#E3F2FD", "#E8F5E9", "#FFFDE7", "#FFEBEE", "#F5F5F5"};

        int maxV = 0;
        for (int c : bmiCounts) if (c > maxV) maxV = c;
        if (maxV == 0) maxV = 1;

        StringBuilder bmiRows = new StringBuilder();
        for (int i = 0; i < bmiLabels.length; i++) {
            double pctVal = total > 0 ? (double) bmiCounts[i] / total * 100 : 0;
            int barPct = (int) ((double) bmiCounts[i] / maxV * 100);
            bmiRows.append("<tr style='background:").append(i % 2 == 0 ? "#F9FBF9" : "#FFFFFF").append("'>")
                    .append("<td style='padding:10px 12px'>")
                    .append("<span style='display:inline-block;width:10px;height:10px;border-radius:50%;background:")
                    .append(bmiColors[i]).append(";margin-right:8px'></span>").append(bmiLabels[i]).append("</td>")
                    .append("<td style='padding:10px 12px'>")
                    .append("<div style='background:#eee;border-radius:4px;height:10px;width:100%;overflow:hidden'>")
                    .append("<div style='background:").append(bmiColors[i]).append(";width:").append(barPct).append("%;height:10px'></div></div></td>")
                    .append("<td style='padding:10px 12px;text-align:center;font-weight:bold;color:").append(bmiColors[i]).append("'>").append(bmiCounts[i]).append("</td>")
                    .append("<td style='padding:10px 12px;text-align:center;color:#9E9E9E'>")
                    .append(String.format(Locale.getDefault(), "%.1f%%", pctVal)).append("</td></tr>");
        }

        return "<!DOCTYPE html><html><head><meta charset='UTF-8'>" +
                "<style>body{font-family:Arial,sans-serif;margin:0;padding:0;color:#0F2D1F;font-size:13px}" +
                ".header{background:linear-gradient(135deg,#1B5E20,#2E7D32);color:white;padding:32px 40px;text-align:center}" +
                ".header h1{margin:0;font-size:22px;letter-spacing:1px}" +
                ".header p{margin:6px 0 0;color:#A5D6A7;font-size:12px}" +
                ".header .date{color:#C8E6C9;font-size:11px;margin-top:4px}" +
                ".body{padding:28px 40px}" +
                ".section-title{font-size:13px;font-weight:bold;color:#1B5E20;background:#E8F5E9;padding:8px 14px;border-left:4px solid #2E7D32;margin:20px 0 10px}" +
                ".cards{display:flex;gap:12px;margin-bottom:8px}" +
                ".card{flex:1;border-radius:10px;padding:16px;text-align:center}" +
                ".card .val{font-size:26px;font-weight:bold;margin:6px 0 2px}" +
                ".card .lbl{font-size:10px;color:#757575}" +
                "table{width:100%;border-collapse:collapse}" +
                "th{background:#E8F5E9;color:#1B5E20;padding:10px 12px;text-align:left;font-size:12px}" +
                ".footer{background:#E8F5E9;padding:14px;text-align:center;font-size:11px;color:#2E7D32;margin-top:32px}" +
                "</style></head><body>" +
                "<div class='header'><h1>BAO CAO THONG KE NGUOI DUNG</h1>" +
                "<p>He thong UDTDDK  –  Admin</p>" +
                "<div class='date'>Ngay xuat: " + reportDate + "</div></div>" +
                "<div class='body'>" +
                "<div class='section-title'>TONG QUAN</div>" +
                "<div class='cards'>" +
                "<div class='card' style='background:#F3E5F5'><div style='font-size:22px'>&#127947;</div><div class='val' style='color:#6A1B9A'>" + totalWorkouts + "</div><div class='lbl'>Tong bai tap</div></div>" +
                "<div class='card' style='background:#E3F2FD'><div style='font-size:22px'>&#128101;</div><div class='val' style='color:#1565C0'>" + total + "</div><div class='lbl'>Tong nguoi dung</div></div>" +
                "<div class='card' style='background:#E8F5E9'><div style='font-size:22px'>&#128202;</div><div class='val' style='color:#2E7D32'>" + withData + "</div><div class='lbl'>Co du lieu BMI</div></div>" +
                "<div class='card' style='background:#F5F5F5'><div style='font-size:22px'>&#9898;</div><div class='val' style='color:#757575'>" + noData + "</div><div class='lbl'>Chua co BMI</div></div>" +
                "<div class='card' style='background:#FFF3E0'><div style='font-size:22px'>&#9878;</div><div class='val' style='color:#E65100'>" + avgStr + "</div><div class='lbl'>BMI trung binh</div></div>" +
                "</div>" +
                "<div class='section-title'>PHAN LOAI BMI</div>" +
                "<table><tr><th>Phan loai</th><th style='width:35%'>Bieu do</th>" +
                "<th style='width:80px;text-align:center'>So nguoi</th>" +
                "<th style='width:80px;text-align:center'>Ty le</th></tr>" +
                bmiRows +
                "</table></div>" +
                "<div class='footer'>He thong UDTDDK &nbsp;&bull;&nbsp; Bao cao tu dong &nbsp;&bull;&nbsp; " + reportDate + "</div>" +
                "</body></html>";
    }

    // ─────────────────────────────────────────
    // COPY TEXT
    // ─────────────────────────────────────────
    private String buildReportText() {
        int withData = total - noData;
        return  "════════════════════════════\n" +
                "   BÁO CÁO THỐNG KÊ NGƯỜI DÙNG\n" +
                "════════════════════════════\n" +
                "Thời gian: " + reportDate + "\n\n" +
                "TỔNG QUAN\n" +
                "─────────────────────────\n" +
                "Tổng bài tập      : " + totalWorkouts + " bài\n" +
                "Tổng người dùng   : " + total    + " người\n" +
                "Có dữ liệu BMI    : " + withData + " người\n" +
                "Chưa có BMI       : " + noData   + " người\n\n" +
                "CHỈ SỐ BMI\n" +
                "─────────────────────────\n" +
                "BMI trung bình    : " + fmt(avgBmi) + "\n\n" +
                "PHÂN LOẠI BMI\n" +
                "─────────────────────────\n" +
                "Thiếu cân     : " + under  + " (" + fmt1(pct(under,  total)) + "%)\n" +
                "Bình thường   : " + normal + " (" + fmt1(pct(normal, total)) + "%)\n" +
                "Thừa cân      : " + over   + " (" + fmt1(pct(over,   total)) + "%)\n" +
                "Béo phì       : " + obese  + " (" + fmt1(pct(obese,  total)) + "%)\n" +
                "Chưa có data  : " + noData + " (" + fmt1(pct(noData, total)) + "%)\n\n" +
                "════════════════════════════\n" +
                "   Hệ thống UDTDDK - Admin\n" +
                "════════════════════════════";
    }

    // ─────────────────────────────────────────
    // UI HELPERS
    // ─────────────────────────────────────────
    private LinearLayout makeCard(String bgColor) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Color.parseColor(bgColor));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = dp(4);
        card.setLayoutParams(p);
        return card;
    }

    private LinearLayout makeHRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private LinearLayout makeMiniCard(String label, String value,
                                      String emoji, String accentColor, String bgColor, float weight) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setBackgroundColor(Color.parseColor(bgColor));
        card.setPadding(dp(12), dp(14), dp(12), dp(14));
        card.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight));
        addTextTo(card, emoji, 20f, Color.BLACK,                      Typeface.NORMAL, Gravity.CENTER, 0);
        addTextTo(card, value, 22f, Color.parseColor(accentColor),    Typeface.BOLD,   Gravity.CENTER, 0);
        addTextTo(card, label, 10f, Color.parseColor("#757575"),       Typeface.NORMAL, Gravity.CENTER, 0);
        return card;
    }

    private TextView makeSectionLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setTextColor(Color.parseColor("#1B5E20"));
        tv.setBackgroundColor(Color.parseColor("#E8F5E9"));
        tv.setPadding(dp(14), dp(8), dp(14), dp(8));
        tv.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return tv;
    }

    private void addTextTo(LinearLayout parent, String text, float sizeSp, int color,
                           int typefaceStyle, int gravity, int topMarginPx) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(sizeSp);
        tv.setTextColor(color);
        tv.setTypeface(null, typefaceStyle);
        tv.setGravity(gravity);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = topMarginPx;
        tv.setLayoutParams(lp);
        parent.addView(tv);
    }

    private View spacer(int heightDp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(heightDp)));
        return v;
    }

    private View spacer2(int widthDp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(widthDp), LinearLayout.LayoutParams.MATCH_PARENT));
        return v;
    }

    private double pct(int part, int total) {
        return total > 0 ? (double) part / total * 100 : 0;
    }

    private String fmt(double val) {
        return val > 0 ? String.format(Locale.getDefault(), "%.2f", val) : "--";
    }

    private String fmt1(double val) {
        return String.format(Locale.getDefault(), "%.1f", val);
    }

    private int dp(int val) {
        return (int) (val * getResources().getDisplayMetrics().density);
    }
}