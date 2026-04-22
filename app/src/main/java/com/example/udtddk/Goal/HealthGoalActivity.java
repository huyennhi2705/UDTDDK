package com.example.udtddk.Goal;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import com.example.udtddk.BaseActivity;
import com.example.udtddk.R;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Locale;

public class HealthGoalActivity extends BaseActivity {

    private static final String TAG    = "HealthGoalActivity";
    private static final String DB_URL = "https://udtddk-default-rtdb.firebaseio.com/";

    // ── Intent extras ──────────────────────────────────────────────
    private String userId;
    private float  currentWeight;
    private float  currentBmi;
    private float  heightCm;

    // ── UI: Header ─────────────────────────────────────────────────
    private TextView tvMetricBmi, tvMetricBmiCat, tvMetricWeight, tvMetricHeight, tvBmiDisplay;

    // ── UI: Form ───────────────────────────────────────────────────
    private LinearLayout layoutForm;
    private CardView cardGoalLose, cardGoalGain, cardGoalMaintain, cardGoalMuscle;
    private TextView badgeLose, badgeGain, badgeMaintain, badgeMuscle;
    private EditText etTargetWeight;
    private TextView tvTargetWeightHint;
    private CardView cardTime4, cardTime8, cardTime12, cardTime16, cardTime24;
    private TextView tvSpeedResult;
    private LinearLayout warningBox;
    private TextView tvWarning;
    private TextView btnSaveRoadmap;

    // ── UI: Summary ────────────────────────────────────────────────
    private LinearLayout layoutSummary;
    private TextView tvSumGoal, tvSumCurrentWeight, tvSumTargetWeight,
            tvSumWeeks, tvSumSpeed, tvSumBmi, tvSumWarning;
    private TextView btnEditRoadmap;

    // ── UI: Loading ────────────────────────────────────────────────
    private ProgressBar progressLoading;

    // ── State ──────────────────────────────────────────────────────
    private String selectedGoal   = null;
    private int    selectedWeeks  = 0;
    private float  targetWeightKg = 0f;

    // ── Firebase ───────────────────────────────────────────────────
    private DatabaseReference dbRef;

    // ══════════════════════════════════════════════════════════════
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_health_goal);

        userId        = getIntent().getStringExtra("NguoiDungId");
        currentWeight = getIntent().getFloatExtra("CanNang", 0f);
        currentBmi    = getIntent().getFloatExtra("BMI", 0f);
        heightCm      = getIntent().getFloatExtra("ChieuCao", 0f);
        if (userId == null || userId.trim().isEmpty()) userId = "guest";

        if (!"guest".equals(userId)) {
            dbRef = FirebaseDatabase
                    .getInstance(DB_URL)
                    .getReference("MucTieu")
                    .child(userId);
        }

        bindViews();
        populateHeader();
        setupGoalCards();
        setupTimeCards();
        setupTargetWeightInput();
        setupSaveButton();
        setupEditButton();

        loadUserInfo();
        loadSavedRoadmap();
    }

    // ══════════════════════════════════════════════════════════════
    //  BIND VIEWS
    // ══════════════════════════════════════════════════════════════
    private void bindViews() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        tvMetricBmi    = findViewById(R.id.tvMetricBmi);
        tvMetricBmiCat = findViewById(R.id.tvMetricBmiCat);
        tvMetricWeight = findViewById(R.id.tvMetricWeight);
        tvMetricHeight = findViewById(R.id.tvMetricHeight);
        tvBmiDisplay   = findViewById(R.id.tvBmiDisplay);

        layoutForm       = findViewById(R.id.layoutForm);
        cardGoalLose     = findViewById(R.id.cardGoalLose);
        cardGoalGain     = findViewById(R.id.cardGoalGain);
        cardGoalMaintain = findViewById(R.id.cardGoalMaintain);
        cardGoalMuscle   = findViewById(R.id.cardGoalMuscle);
        badgeLose        = findViewById(R.id.badgeLose);
        badgeGain        = findViewById(R.id.badgeGain);
        badgeMaintain    = findViewById(R.id.badgeMaintain);
        badgeMuscle      = findViewById(R.id.badgeMuscle);

        etTargetWeight     = findViewById(R.id.etTargetWeight);
        tvTargetWeightHint = findViewById(R.id.tvTargetWeightHint);
        cardTime4          = findViewById(R.id.cardTime4);
        cardTime8          = findViewById(R.id.cardTime8);
        cardTime12         = findViewById(R.id.cardTime12);
        cardTime16         = findViewById(R.id.cardTime16);
        cardTime24         = findViewById(R.id.cardTime24);
        tvSpeedResult      = findViewById(R.id.tvSpeedResult);
        warningBox         = findViewById(R.id.warningBox);
        tvWarning          = findViewById(R.id.tvWarning);
        btnSaveRoadmap     = findViewById(R.id.btnSaveRoadmap);

        layoutSummary      = findViewById(R.id.layoutSummary);
        tvSumGoal          = findViewById(R.id.tvSumGoal);
        tvSumCurrentWeight = findViewById(R.id.tvSumCurrentWeight);
        tvSumTargetWeight  = findViewById(R.id.tvSumTargetWeight);
        tvSumWeeks         = findViewById(R.id.tvSumWeeks);
        tvSumSpeed         = findViewById(R.id.tvSumSpeed);
        tvSumBmi           = findViewById(R.id.tvSumBmi);
        tvSumWarning       = findViewById(R.id.tvSumWarning);
        btnEditRoadmap     = findViewById(R.id.btnEditRoadmap);

        progressLoading    = findViewById(R.id.progressLoading);
    }

    // ══════════════════════════════════════════════════════════════
    //  HEADER
    // ══════════════════════════════════════════════════════════════
    private void populateHeader() {
        if (currentBmi > 0) {
            tvMetricBmi.setText(String.format(Locale.US, "%.1f", currentBmi));
            tvMetricBmiCat.setText(bmiCategory(currentBmi));
            tvBmiDisplay.setText(String.format(Locale.US,
                    "Khoảng BMI lý tưởng: 18.5 – 24.9  •  Của bạn: %.1f (%s)",
                    currentBmi, bmiCategory(currentBmi)));
        }
        if (currentWeight > 0)
            tvMetricWeight.setText(String.format(Locale.US, "%.1f kg", currentWeight));
        if (heightCm > 0)
            tvMetricHeight.setText(String.format(Locale.US, "%.0f cm", heightCm));

        if (currentBmi >= 25f)
            badgeLose.setVisibility(View.VISIBLE);
        else if (currentBmi > 0 && currentBmi < 18.5f)
            badgeGain.setVisibility(View.VISIBLE);
        else if (currentBmi >= 18.5f) {
            badgeMaintain.setVisibility(View.VISIBLE);
            badgeMuscle.setVisibility(View.VISIBLE);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  GOAL CARDS
    // ══════════════════════════════════════════════════════════════
    private void setupGoalCards() {
        cardGoalLose.setOnClickListener(v     -> selectGoal("giam_can"));
        cardGoalGain.setOnClickListener(v     -> selectGoal("tang_can"));
        cardGoalMaintain.setOnClickListener(v -> selectGoal("duy_tri"));
        cardGoalMuscle.setOnClickListener(v   -> selectGoal("tang_co"));
    }

    private void loadUserInfo() {
        if (userId == null || userId.equals("guest")) return;

        DatabaseReference userRef = FirebaseDatabase
                .getInstance(DB_URL)
                .getReference("NguoiDung")   // ✅ ĐÚNG THEO CSDL
                .child(userId);

        userRef.get().addOnSuccessListener(snapshot -> {
            if (!snapshot.exists()) {
                Log.e(TAG, "❌ Không có dữ liệu user");
                return;
            }

            Log.d(TAG, "DATA: " + snapshot.getValue());

            // ✅ Đọc đúng field theo CSDL
            currentWeight = (float) safeDouble(snapshot.child("CanNang").getValue());
            heightCm      = (float) safeDouble(snapshot.child("ChieuCao").getValue());

            // ✅ Tính BMI
            if (heightCm > 0 && currentWeight > 0) {
                float h = heightCm / 100f;
                currentBmi = currentWeight / (h * h);
            }

            Log.d(TAG, "CanNang=" + currentWeight +
                    " ChieuCao=" + heightCm +
                    " BMI=" + currentBmi);

            populateHeader(); // ✅ cập nhật UI SAU khi có data

        }).addOnFailureListener(e ->
                Log.e(TAG, "❌ Lỗi load user", e)
        );
    }
    private void selectGoal(String goal) {
        selectedGoal = goal;
        highlightGoalCard(goal);

        if (currentWeight > 0 && heightCm > 0) {
            float h   = heightCm / 100f;
            float min = (float) (18.5 * h * h);
            float max = (float) (24.9 * h * h);
            switch (goal) {
                case "giam_can":
                    tvTargetWeightHint.setText(String.format(Locale.US,
                            "💡 Gợi ý: %.1f – %.1f kg để đạt BMI lý tưởng",
                            min, Math.min(max, currentWeight - 0.5f)));
                    break;
                case "tang_can":
                    tvTargetWeightHint.setText(String.format(Locale.US,
                            "💡 Gợi ý: %.1f – %.1f kg để đạt BMI lý tưởng",
                            Math.max(min, currentWeight + 0.5f), max));
                    break;
                case "duy_tri":
                    tvTargetWeightHint.setText(String.format(Locale.US,
                            "💡 Giữ nguyên cân nặng hiện tại: %.1f kg", currentWeight));
                    break;
                case "tang_co":
                    tvTargetWeightHint.setText(String.format(Locale.US,
                            "💡 Tăng cơ thường tăng 0.5–1 kg/tháng. Cân nặng hiện tại: %.1f kg",
                            currentWeight));
                    break;
            }
        }
        recalcAndValidate();
    }

    // ══════════════════════════════════════════════════════════════
    //  TIME CARDS
    // ══════════════════════════════════════════════════════════════
    private void setupTimeCards() {
        cardTime4.setOnClickListener(v  -> selectTime(4));
        cardTime8.setOnClickListener(v  -> selectTime(8));
        cardTime12.setOnClickListener(v -> selectTime(12));
        cardTime16.setOnClickListener(v -> selectTime(16));
        cardTime24.setOnClickListener(v -> selectTime(24));
    }

    private void selectTime(int weeks) {
        selectedWeeks = weeks;
        highlightTimeCard(weeks);
        recalcAndValidate();
    }

    // ══════════════════════════════════════════════════════════════
    //  TARGET WEIGHT INPUT
    // ══════════════════════════════════════════════════════════════
    private void setupTargetWeightInput() {
        etTargetWeight.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                String txt = s.toString().trim();
                try { targetWeightKg = txt.isEmpty() ? 0f : Float.parseFloat(txt); }
                catch (NumberFormatException e) { targetWeightKg = 0f; }
                recalcAndValidate();
            }
        });
    }

    // ══════════════════════════════════════════════════════════════
    //  RECALC
    // ══════════════════════════════════════════════════════════════
    private void recalcAndValidate() {
        if (targetWeightKg > 0 && selectedWeeks > 0 && currentWeight > 0) {
            float perWeek = Math.abs(targetWeightKg - currentWeight) / selectedWeeks;
            tvSpeedResult.setText(String.format(Locale.US,
                    "📈 Tốc độ thay đổi: %.2f kg/tuần  (≈ %.1f kg/tháng)",
                    perWeek, perWeek * 4));
            tvSpeedResult.setVisibility(View.VISIBLE);
        } else {
            tvSpeedResult.setVisibility(View.GONE);
        }

        String warning = buildWarning();
        if (warning == null) {
            warningBox.setVisibility(View.GONE);
        } else {
            tvWarning.setText(warning);
            warningBox.setVisibility(View.VISIBLE);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  BUILD WARNING
    // ══════════════════════════════════════════════════════════════
    private String buildWarning() {
        if (targetWeightKg <= 0 || selectedWeeks <= 0 || currentWeight <= 0) return null;

        float delta   = targetWeightKg - currentWeight;
        float perWeek = Math.abs(delta) / selectedWeeks;

        StringBuilder sb = new StringBuilder();

        if ("giam_can".equals(selectedGoal) && delta >= 0)
            sb.append("⚠️ Cân nặng mục tiêu phải THẤP hơn cân nặng hiện tại khi chọn Giảm cân.\n\n");

        if ("tang_can".equals(selectedGoal) && delta <= 0)
            sb.append("⚠️ Cân nặng mục tiêu phải CAO hơn cân nặng hiện tại khi chọn Tăng cân.\n\n");

        if (perWeek < 0.3f) {
            sb.append("ℹ️ Tốc độ khá chậm (<0.3 kg/tuần).\n");
            sb.append("→ Bạn có thể tăng nhẹ để đạt hiệu quả tốt hơn.\n\n");
        } else if (perWeek <= 1.0f) {
            sb.append("✅ Tốc độ an toàn (0.5–1 kg/tuần).\n");
            sb.append("→ Giữ được cơ bắp, ít bị tăng cân lại.\n\n");
        } else if (perWeek <= 1.5f) {
            sb.append("⚠️ Tốc độ hơi nhanh (1–1.5 kg/tuần).\n");
            sb.append("→ Cần theo dõi sức khỏe, tránh mất cơ.\n\n");
        } else {
            sb.append("❌ Tốc độ quá nhanh (>1.5 kg/tuần).\n");
            sb.append("→ Nguy cơ:\n");
            sb.append("   • Mất nước và cơ\n");
            sb.append("   • Mệt mỏi, chóng mặt\n");
            sb.append("   • Dễ tăng cân lại (yo-yo)\n\n");
        }

        if (heightCm > 0) {
            float h = heightCm / 100f;
            float targetBmi = targetWeightKg / (h * h);
            if (targetBmi < 17f)
                sb.append(String.format(Locale.US, "⚠️ BMI mục tiêu %.1f — quá thấp, không an toàn.\n\n", targetBmi));
            else if (targetBmi > 30f)
                sb.append(String.format(Locale.US, "⚠️ BMI mục tiêu %.1f — thuộc mức béo phì.\n\n", targetBmi));
        }

        sb.append("💡 Gợi ý:\n");
        sb.append("• Ăn đủ chất, không nhịn ăn cực đoan\n");
        sb.append("• Tập luyện nhẹ đến vừa (đi bộ, cardio, gym cơ bản)\n");
        sb.append("• Ngủ đủ giấc để cơ thể hồi phục tốt\n");

        return sb.toString().trim();
    }

    // ══════════════════════════════════════════════════════════════
    //  VALIDATE FORM
    // ══════════════════════════════════════════════════════════════
    private boolean validateForm() {
        if (selectedGoal == null) {
            Toast.makeText(this, "❗ Vui lòng chọn loại mục tiêu", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (targetWeightKg <= 0) {
            Toast.makeText(this, "❗ Vui lòng nhập cân nặng mục tiêu", Toast.LENGTH_SHORT).show();
            etTargetWeight.requestFocus();
            return false;
        }
        if (targetWeightKg < 30f || targetWeightKg > 200f) {
            Toast.makeText(this, "❗ Cân nặng mục tiêu không hợp lệ (30–200 kg)", Toast.LENGTH_SHORT).show();
            etTargetWeight.requestFocus();
            return false;
        }
        if (selectedWeeks == 0) {
            Toast.makeText(this, "❗ Vui lòng chọn thời gian thực hiện", Toast.LENGTH_SHORT).show();
            return false;
        }
        // ✅ Chỉ kiểm tra so sánh khi currentWeight đã load xong (> 0)
        if (currentWeight > 0) {
            if ("giam_can".equals(selectedGoal) && targetWeightKg >= currentWeight) {
                Toast.makeText(this, "❗ Cân nặng mục tiêu phải nhỏ hơn cân nặng hiện tại", Toast.LENGTH_LONG).show();
                return false;
            }
            if ("tang_can".equals(selectedGoal) && targetWeightKg <= currentWeight) {
                Toast.makeText(this, "❗ Cân nặng mục tiêu phải lớn hơn cân nặng hiện tại", Toast.LENGTH_LONG).show();
                return false;
            }
        }
        return true;

    }

    // ══════════════════════════════════════════════════════════════
    //  SAVE BUTTON
    // ══════════════════════════════════════════════════════════════
    private void setupSaveButton() {
        btnSaveRoadmap.setOnClickListener(v -> {
            Log.d(TAG, "🔘 Nút lưu được nhấn | NguoiDungId=" + userId
                    + " | dbRef=" + (dbRef != null ? "OK" : "NULL")
                    + " | CanNang=" + currentWeight
                    + " | MucTieu=" + selectedGoal
                    + " | SoTuan=" + selectedWeeks
                    + " | CanNangMucTieu=" + targetWeightKg);

            if (!validateForm()) return;
            persistToFirebase();
        });
    }
    private void persistToFirebase() {
        if (!validateForm()) return;

        float perWeek = selectedWeeks > 0 ? Math.abs(targetWeightKg - currentWeight) / selectedWeeks : 0f;
        String warnText = buildWarning();
        int canhBao = (warnText != null && !warnText.isEmpty()) ? 1 : 0;

        HashMap<String, Object> data = new HashMap<>();
        data.put("NguoiDungId", userId);
        data.put("LoaiMucTieu", selectedGoal);
        data.put("CanNangMucTieu", targetWeightKg);
        data.put("SoTuan", selectedWeeks);
        data.put("NgayTao", System.currentTimeMillis());
        data.put("ThoiGian", System.currentTimeMillis());
        data.put("TocDo", String.format(Locale.US, "%.2f kg/tuần", perWeek));
        data.put("BMIHienTai", currentBmi);
        data.put("CanhBao", canhBao);

        Log.d(TAG, "🔄 Đang tạo mục tiêu mới cho user: " + userId);

        if (dbRef == null) {
            Toast.makeText(this, " Chưa có userId", Toast.LENGTH_LONG).show();
            return;
        }

        setLoading(true);

        dbRef.push().setValue(data)
                .addOnSuccessListener(unused -> {
                    setLoading(false);
                    Log.d(TAG, " Tạo mục tiêu mới thành công");
                    Toast.makeText(this, " Lưu mục tiêu thành công!", Toast.LENGTH_SHORT).show();

                    pushGoalToHistory();

                    Intent resultIntent = new Intent();
                    resultIntent.putExtra("goal_updated", true);
                    setResult(RESULT_OK, resultIntent);

                    renderSummary(buildWarning());
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Log.e(TAG, " Lưu thất bại: " + e.getMessage(), e);
                    Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
    private void pushGoalToHistory() {
        if (userId == null || userId.equals("guest")) return;


        DatabaseReference historyRef = FirebaseDatabase
                .getInstance(DB_URL)
                .getReference("NguoiDung")
                .child(userId)
                .child("LicSuMucTieu")
                .child("thong_bao")
                .push();

        String content = "Mục tiêu: " + goalLabel(selectedGoal)
                + "\nCân nặng mục tiêu: " + String.format(Locale.US, "%.1f kg", targetWeightKg)
                + "\nThời gian thực hiện: " + selectedWeeks + " tuần";

        HashMap<String, Object> notif = new HashMap<>();
        notif.put("title",     "🎯 Bạn vừa tạo mục tiêu mới");
        notif.put("content",   content);
        notif.put("type",      "health");                        // ✅ HistoryActivity nhận diện đúng màu
        notif.put("timestamp", System.currentTimeMillis());

        historyRef.setValue(notif)
                .addOnSuccessListener(unused ->
                        Log.d(TAG, "✅ Đã đẩy thông báo vào lịch sử"))
                .addOnFailureListener(e ->
                        Log.e(TAG, "❌ Lỗi đẩy lịch sử: " + e.getMessage()));
    }

    // ══════════════════════════════════════════════════════════════
    //  ĐỌC FIREBASE — đọc field tiếng Việt, tương thích ngược
    // ══════════════════════════════════════════════════════════════
    private void loadSavedRoadmap() {
        if (dbRef == null) return;

        setLoading(true);

        // Đọc tất cả mục tiêu của user này và lấy cái mới nhất
        dbRef.get().addOnSuccessListener(snapshot -> {
            setLoading(false);
            if (!snapshot.exists()) return;

            String latestGoal = null;
            long latestTime = 0;

            for (DataSnapshot child : snapshot.getChildren()) {
                long ngayTao = child.child("NgayTao").getValue(Long.class) != null
                        ? child.child("NgayTao").getValue(Long.class) : 0;

                if (ngayTao > latestTime) {
                    latestTime = ngayTao;
                    latestGoal = child.child("LoaiMucTieu").getValue(String.class);
                    targetWeightKg = (float) safeDouble(child.child("CanNangMucTieu").getValue());
                    selectedWeeks  = (int) safeLong(child.child("SoTuan").getValue());
                    currentBmi     = (float) safeDouble(child.child("BMIHienTai").getValue());
                }
            }

            if (latestGoal != null) {
                selectedGoal = latestGoal;
                recalcAndValidate();
                renderSummary(buildWarning());
            }
        }).addOnFailureListener(e -> {
            setLoading(false);
            Log.e(TAG, "Lỗi load mục tiêu", e);
        });
    }
    // ══════════════════════════════════════════════════════════════
    //  HIỂN THỊ MỤC TIÊU ĐÃ LƯU
    // ══════════════════════════════════════════════════════════════
    private void renderSummary(String warning) {
        layoutForm.setVisibility(View.GONE);
        layoutSummary.setVisibility(View.VISIBLE);

        tvSumGoal.setText(goalLabel(selectedGoal));
        tvSumCurrentWeight.setText(String.format(Locale.US, "%.1f kg", currentWeight));
        tvSumTargetWeight.setText(String.format(Locale.US,  "%.1f kg", targetWeightKg));
        tvSumWeeks.setText(selectedWeeks + " tuần");

        float perWeek = selectedWeeks > 0
                ? Math.abs(targetWeightKg - currentWeight) / selectedWeeks : 0f;
        tvSumSpeed.setText(String.format(Locale.US,
                "%.2f kg/tuần  (≈ %.1f kg/tháng)", perWeek, perWeek * 4));
        tvSumBmi.setText(String.format(Locale.US,
                "%.1f  (%s)", currentBmi, bmiCategory(currentBmi)));

        if (warning != null && !warning.isEmpty()) {
            tvSumWarning.setText(warning);
            tvSumWarning.setTextColor(ContextCompat.getColor(this, R.color.orange_warning));
        } else {
            tvSumWarning.setText("✅ Lộ trình an toàn, không có cảnh báo.");
            tvSumWarning.setTextColor(ContextCompat.getColor(this, R.color.green_primary));
        }
        tvSumWarning.setVisibility(View.VISIBLE);
    }

    // ══════════════════════════════════════════════════════════════
    //  EDIT BUTTON
    // ══════════════════════════════════════════════════════════════
    private void setupEditButton() {
        btnEditRoadmap.setOnClickListener(v -> {
            layoutSummary.setVisibility(View.GONE);
            layoutForm.setVisibility(View.VISIBLE);
        });
    }

    // ══════════════════════════════════════════════════════════════
    //  LOADING STATE
    // ══════════════════════════════════════════════════════════════
    private void setLoading(boolean loading) {
        progressLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnSaveRoadmap.setEnabled(!loading);
        btnSaveRoadmap.setAlpha(loading ? 0.5f : 1.0f);
    }

    // ══════════════════════════════════════════════════════════════
    //  HIGHLIGHT HELPERS
    // ══════════════════════════════════════════════════════════════
    private void highlightGoalCard(String goal) {
        int on  = ContextCompat.getColor(this, R.color.green_primary);
        int off = ContextCompat.getColor(this, R.color.white);
        cardGoalLose.setCardBackgroundColor    ("giam_can".equals(goal) ? on : off);
        cardGoalGain.setCardBackgroundColor    ("tang_can".equals(goal) ? on : off);
        cardGoalMaintain.setCardBackgroundColor("duy_tri".equals(goal)  ? on : off);
        cardGoalMuscle.setCardBackgroundColor  ("tang_co".equals(goal)  ? on : off);

        setGoalCardTextColor(cardGoalLose,     "giam_can".equals(goal));
        setGoalCardTextColor(cardGoalGain,     "tang_can".equals(goal));
        setGoalCardTextColor(cardGoalMaintain, "duy_tri".equals(goal));
        setGoalCardTextColor(cardGoalMuscle,   "tang_co".equals(goal));
    }

    private void setGoalCardTextColor(CardView card, boolean selected) {
        if (card.getChildCount() > 0 && card.getChildAt(0) instanceof android.widget.LinearLayout) {
            android.widget.LinearLayout ll = (android.widget.LinearLayout) card.getChildAt(0);
            for (int i = 0; i < ll.getChildCount(); i++) {
                if (ll.getChildAt(i) instanceof TextView) {
                    TextView tv = (TextView) ll.getChildAt(i);
                    tv.setTextColor(selected ? 0xFFFFFFFF : 0xFF0F2D1F);
                }
            }
        }
    }

    private void highlightTimeCard(int weeks) {
        int on  = ContextCompat.getColor(this, R.color.green_primary);
        int off = ContextCompat.getColor(this, R.color.white);
        cardTime4.setCardBackgroundColor (weeks == 4  ? on : off);
        cardTime8.setCardBackgroundColor (weeks == 8  ? on : off);
        cardTime12.setCardBackgroundColor(weeks == 12 ? on : off);
        cardTime16.setCardBackgroundColor(weeks == 16 ? on : off);
        cardTime24.setCardBackgroundColor(weeks == 24 ? on : off);
    }

    // ══════════════════════════════════════════════════════════════
    //  SAFE PARSE HELPERS
    // ══════════════════════════════════════════════════════════════
    private String safeString(Object o) { return o != null ? o.toString() : null; }

    private double safeDouble(Object o) {
        if (o == null) return 0.0;
        try { return Double.parseDouble(o.toString()); }
        catch (NumberFormatException e) { return 0.0; }
    }

    private long safeLong(Object o) {
        if (o == null) return 0L;
        try { return Long.parseLong(o.toString()); }
        catch (NumberFormatException e) { return 0L; }
    }

    // ══════════════════════════════════════════════════════════════
    //  DOMAIN HELPERS
    // ══════════════════════════════════════════════════════════════
    private String bmiCategory(float bmi) {
        if (bmi <= 0)    return "--";
        if (bmi < 16f)   return "Thiếu cân nghiêm trọng";
        if (bmi < 18.5f) return "Thiếu cân";
        if (bmi < 25f)   return "Bình thường";
        if (bmi < 30f)   return "Thừa cân";
        return "Béo phì";
    }

    private String goalLabel(String goal) {
        if (goal == null) return "--";
        switch (goal) {
            case "giam_can": return "🔥 Giảm cân";
            case "tang_can": return "💪 Tăng cân";
            case "duy_tri":  return "⚖️ Duy trì cân nặng";
            case "tang_co":  return "🏋️ Tăng cơ";
            // Tương thích ngược
            case "lose":     return "🔥 Giảm cân";
            case "gain":     return "💪 Tăng cân";
            case "maintain": return "⚖️ Duy trì cân nặng";
            case "muscle":   return "🏋️ Tăng cơ";
            default:         return goal;
        }
    }

    @Override protected String getUserId()     { return userId; }
    @Override protected float  getCurrentBmi() { return currentBmi; }
}