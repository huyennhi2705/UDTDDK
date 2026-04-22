package com.example.udtddk.baitap;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.os.Bundle;
import androidx.cardview.widget.CardView;

import com.example.udtddk.BaseActivity;
import com.example.udtddk.Goal.HealthGoalActivity;
import com.example.udtddk.R;
import com.example.udtddk.models.Workout;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class BaiTapActivity extends BaseActivity {

    private static final String DB_URL = "https://udtddk-default-rtdb.firebaseio.com/";
    private static final int REQUEST_GOAL = 1001;

    // Goal Realtime
    private DatabaseReference goalRef;
    private ValueEventListener goalListener;

    // UI
    private TextView tvBmiSummary, tvBmiCategorySummary, tvGoal;
    private LinearLayout workoutList;

    // Data
    private float currentWeight = 0f;
    private float heightCm = 0f;
    private float bmi = 0f;
    private String currentBmiCategory = null;

    // Workout
    private ValueEventListener workoutListener;
    private DatabaseReference workoutsRef;

    private String userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bai_tap);

        // Bind views
        tvBmiSummary = findViewById(R.id.tvBmiSummary);
        tvBmiCategorySummary = findViewById(R.id.tvBmiCategorySummary);
        tvGoal = findViewById(R.id.tvGoal);
        workoutList = findViewById(R.id.workoutList);

        workoutsRef = FirebaseDatabase.getInstance(DB_URL).getReference("BaiTap");

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Lấy userId
        userId = getIntent().getStringExtra("NguoiDungId");
        if (userId == null || userId.isEmpty()) {
            userId = getSharedPreferences("USER", MODE_PRIVATE)
                    .getString("NguoiDungId", null);
        }

        if (userId == null || userId.isEmpty()) {
            Toast.makeText(this, "Bạn chưa đăng nhập", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setupBottomNav(R.id.nav_exercise);
        setupMoreForYouCards();

        // Load dữ liệu ban đầu
        loadAllData();

        // Mở HealthGoalActivity để tạo/chỉnh sửa mục tiêu
        tvGoal.setOnClickListener(v -> {
            Intent intent = new Intent(BaiTapActivity.this, HealthGoalActivity.class);
            intent.putExtra("NguoiDungId", userId);
            intent.putExtra("BMI", bmi);
            intent.putExtra("CanNang", currentWeight);
            intent.putExtra("ChieuCao", heightCm);

            startActivityForResult(intent, REQUEST_GOAL);   // Chỉ dùng ForResult
        });
    }

    // ====================== Nhận kết quả từ HealthGoalActivity ======================
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_GOAL && resultCode == RESULT_OK) {
            if (data != null && data.getBooleanExtra("goal_updated", false)) {
                Log.d("BaiTapActivity", "Mục tiêu vừa được cập nhật → reload toàn bộ dữ liệu");
                loadAllData();   // Reload BMI + Goal + Workout
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (userId != null && !userId.isEmpty() && currentBmiCategory != null) {
            // Nếu đã có BMI thì chỉ reload mục tiêu realtime
            loadGoalRealtime();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        removeWorkoutListener();
        removeGoalListener();
    }


    private void loadAllData() {
        DatabaseReference root = FirebaseDatabase.getInstance(DB_URL).getReference();

        root.child("NguoiDung").child(userId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        if (!snapshot.exists()) {
                            Toast.makeText(BaiTapActivity.this, "Không có dữ liệu người dùng", Toast.LENGTH_SHORT).show();
                            tvGoal.setText("Chưa chọn mục tiêu");
                            return;
                        }

                        try {
                            Object wObj = snapshot.child("CanNang").getValue();
                            Object hObj = snapshot.child("ChieuCao").getValue();

                            if (wObj == null || hObj == null) {
                                tvGoal.setText("Chưa chọn mục tiêu");
                                return;
                            }

                            currentWeight = Float.parseFloat(wObj.toString().replace(',', '.'));
                            heightCm = Float.parseFloat(hObj.toString().replace(',', '.'));

                            float heightM = heightCm / 100f;
                            bmi = currentWeight / (heightM * heightM);

                            Log.d("BaiTapActivity", "CanNang=" + currentWeight +
                                    ", ChieuCao=" + heightCm + ", BMI=" + bmi);

                            showBmiInfo(bmi);

                            // Load mục tiêu sau khi có BMI
                            loadGoalRealtime();

                        } catch (Exception e) {
                            e.printStackTrace();
                            tvGoal.setText("Chưa chọn mục tiêu");
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        Log.e("BaiTapActivity", "loadAllData cancelled: " + error.getMessage());
                        tvGoal.setText("Chưa chọn mục tiêu");
                    }
                });
    }



    private void loadGoalRealtime() {
        removeGoalListener();

        DatabaseReference mucTieuRef = FirebaseDatabase.getInstance(DB_URL)
                .getReference("MucTieu")
                .child(userId);

        goalListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                String latestGoal = null;
                long latestTime = 0;

                for (DataSnapshot child : snapshot.getChildren()) {
                    Long ngayTao = child.child("NgayTao").getValue(Long.class);
                    if (ngayTao == null) ngayTao = 0L;

                    if (ngayTao > latestTime) {
                        latestTime = ngayTao;
                        latestGoal = child.child("LoaiMucTieu").getValue(String.class);
                    }
                }

                if (latestGoal != null) {
                    Log.d("BaiTapActivity", "✅ Goal mới: " + latestGoal);
                    applyGoalUI(latestGoal);
                } else {
                    tvGoal.setText("Chưa chọn mục tiêu");
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e("BaiTapActivity", "loadGoalRealtime error: " + error.getMessage());
            }
        };

        mucTieuRef.addValueEventListener(goalListener);
    }

    private void removeGoalListener() {
        if (goalListener != null) {
            DatabaseReference mucTieuRef = FirebaseDatabase.getInstance(DB_URL)
                    .getReference("MucTieu")
                    .child(userId);

            mucTieuRef.removeEventListener(goalListener);
            goalListener = null;
        }
    }

    private void showBmiInfo(float bmi) {
        tvBmiSummary.setText(String.format("%.1f", bmi));

        if (bmi < 18.5f) {
            currentBmiCategory = "Thiếu cân";
            tvBmiCategorySummary.setTextColor(Color.parseColor("#5BBFFA"));
        } else if (bmi < 25f) {
            currentBmiCategory = "Bình thường";
            tvBmiCategorySummary.setTextColor(Color.parseColor("#2E8B57"));
        } else if (bmi < 30f) {
            currentBmiCategory = "Thừa cân";
            tvBmiCategorySummary.setTextColor(Color.parseColor("#FFC107"));
        } else {
            currentBmiCategory = "Béo phì";
            tvBmiCategorySummary.setTextColor(Color.parseColor("#E53935"));
        }
        tvBmiCategorySummary.setText(currentBmiCategory);
    }


    private void loadWorkoutsRealtime(String bmiCategory) {
        removeWorkoutListener();
        workoutListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                workoutList.removeAllViews();
                boolean found = false;

                for (DataSnapshot ds : snapshot.getChildren()) {
                    try {
                        Workout w = ds.getValue(Workout.class);
                        if (w == null) continue;
                        if (!bmiCategory.equals(w.getPhanLoaiTheoBMI())) continue;

                        found = true;
                        workoutList.addView(createWorkoutCard(w));
                    } catch (Exception ignored) {}
                }
                if (!found) {
                    TextView tv = new TextView(BaiTapActivity.this);
                    tv.setText("Chưa có bài tập cho nhóm " + bmiCategory);
                    tv.setPadding(0, 24, 0, 24);
                    tv.setTextColor(Color.parseColor("#7A9E8A"));
                    workoutList.addView(tv);
                }
            }
            @Override
            public void onCancelled(DatabaseError error) {
                Log.e("BaiTapActivity", "loadWorkouts cancelled: " + error.getMessage());
            }
        };
        workoutsRef.addValueEventListener(workoutListener);
    }

    private void removeWorkoutListener() {
        if (workoutListener != null) {
            workoutsRef.removeEventListener(workoutListener);
            workoutListener = null;
        }
    }

    private View createWorkoutCard(Workout w) {
        int dp4 = dp(4);
        int dp6 = dp(6);
        int dp8 = dp(8);
        int dp10 = dp(10);
        int dp12 = dp(12);
        int dp14 = dp(14);
        int dp16 = dp(16);
        int dp20 = dp(20);
        int dp24 = dp(24);

        // Màu accent theo độ khó
        String level = w.getMucDo() != null ? w.getMucDo() : "";
        int accentColor;
        int accentBg;
        String levelEmoji;

        switch (level) {
            case "Khó":
                accentColor = Color.parseColor("#E53935");
                accentBg = Color.parseColor("#FFEBEE");
                levelEmoji = "🔴";
                break;
            case "Trung bình":
                accentColor = Color.parseColor("#FB8C00");
                accentBg = Color.parseColor("#FFF3E0");
                levelEmoji = "🟠";
                break;
            default: // Dễ
                accentColor = Color.parseColor("#2E8B57");
                accentBg = Color.parseColor("#E8F5E9");
                levelEmoji = "🟢";
                break;
        }

        // Card ngoài
        CardView card = new CardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, dp(14));
        card.setLayoutParams(cardParams);
        card.setRadius(dp(18));
        card.setCardElevation(dp(3));
        card.setCardBackgroundColor(Color.WHITE);
        card.setUseCompatPadding(true);

        // Layout chính
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // Accent bar
        View accentBar = new View(this);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(4));
        accentBar.setLayoutParams(barParams);
        GradientDrawable barGrad = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{accentColor, Color.parseColor("#E8F5E9")});
        accentBar.setBackground(barGrad);

        LinearLayout rowTop = new LinearLayout(this);
        rowTop.setOrientation(LinearLayout.HORIZONTAL);
        rowTop.setGravity(Gravity.CENTER_VERTICAL);
        rowTop.setPadding(dp20, dp14, dp16, dp(6));

        TextView tvName = new TextView(this);
        tvName.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        tvName.setText(w.getTenBaiTap() != null ? w.getTenBaiTap() : "");
        tvName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tvName.setTextColor(Color.parseColor("#0F2D1F"));
        tvName.setTypeface(null, Typeface.BOLD);
        tvName.setMaxLines(2);

        TextView tvLevel = new TextView(this);
        LinearLayout.LayoutParams lvParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lvParams.setMarginStart(dp8);
        tvLevel.setLayoutParams(lvParams);
        tvLevel.setText(levelEmoji + " " + level);
        tvLevel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvLevel.setTextColor(accentColor);
        tvLevel.setTypeface(null, Typeface.BOLD);
        tvLevel.setPadding(dp8, dp4, dp8, dp4);

        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(accentBg);
        badgeBg.setCornerRadius(dp(20));
        tvLevel.setBackground(badgeBg);

        rowTop.addView(tvName);
        rowTop.addView(tvLevel);

        // Divider
        View divider = new View(this);
        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        divParams.setMargins(dp20, dp(4), dp20, dp(4));
        divider.setLayoutParams(divParams);
        divider.setBackgroundColor(Color.parseColor("#F0F7F4"));


        LinearLayout rowBottom = new LinearLayout(this);
        rowBottom.setOrientation(LinearLayout.HORIZONTAL);
        rowBottom.setGravity(Gravity.CENTER_VERTICAL);
        rowBottom.setPadding(dp20, dp(6), dp16, dp14);

        TextView tvDuration = makeChip("⏱ " + w.getThoiGianTap() + " phút",
                Color.parseColor("#1565C0"), Color.parseColor("#E3F2FD"));
        LinearLayout.LayoutParams durParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        durParams.setMarginEnd(dp8);
        tvDuration.setLayoutParams(durParams);

        TextView tvCal = makeChip("🔥 " + w.getLuongCaloTieuHao() + " kcal",
                Color.parseColor("#BF360C"), Color.parseColor("#FBE9E7"));
        LinearLayout.LayoutParams calParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        calParams.setMarginEnd(dp8);
        tvCal.setLayoutParams(calParams);

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvArrow = new TextView(this);
        tvArrow.setText("›");
        tvArrow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        tvArrow.setTextColor(Color.parseColor("#2E8B57"));
        tvArrow.setTypeface(null, Typeface.BOLD);

        rowBottom.addView(tvDuration);
        rowBottom.addView(tvCal);
        rowBottom.addView(spacer);
        rowBottom.addView(tvArrow);


        root.addView(accentBar);
        root.addView(rowTop);
        root.addView(divider);
        root.addView(rowBottom);
        card.addView(root);


        TypedValue outValue = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        root.setForeground(getDrawable(outValue.resourceId));
        root.setClickable(true);
        root.setFocusable(true);

        root.setOnClickListener(v -> {
            Intent intent = new Intent(this, WorkoutDetailActivity.class);
            intent.putExtra("TenBaiTap", w.getTenBaiTap());
            intent.putExtra("MoTa", w.getMoTa());
            intent.putExtra("DuongDanVideo", w.getDuongDanVideo());
            intent.putExtra("MucDo", w.getMucDo());
            intent.putExtra("PhanLoaiTheoBMI", w.getPhanLoaiTheoBMI());
            intent.putExtra("ThoiGianTap", w.getThoiGianTap());
            intent.putExtra("LuongCaloTieuHao", w.getLuongCaloTieuHao());
            startActivity(intent);
        });

        return card;
    }

    private TextView makeChip(String text, int textColor, int bgColor) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tv.setTextColor(textColor);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setPadding(dp(10), dp(5), dp(10), dp(5));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(dp(20));
        tv.setBackground(bg);
        return tv;
    }

    private int dp(int dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics()));
    }


    private void applyGoalUI(String goal) {
        String overrideCategory = null;

        switch (goal) {
            case "giam_can":
            case "lose":
                tvGoal.setText("🔥 Giảm cân");
                overrideCategory = "Thừa cân";
                break;
            case "tang_can":
            case "gain":
                tvGoal.setText("💪 Tăng cân");
                overrideCategory = "Thiếu cân";
                break;
            case "duy_tri":
            case "maintain":
                tvGoal.setText("⚖️ Duy trì");
                overrideCategory = "Bình thường";
                break;
            case "tang_co":
            case "muscle":
                tvGoal.setText("🏋️ Tăng cơ");
                overrideCategory = "Bình thường";
                break;
            default:
                tvGoal.setText("Chưa chọn mục tiêu");
                break;
        }

        String categoryToLoad = overrideCategory != null ? overrideCategory : currentBmiCategory;
        if (categoryToLoad != null) {
            loadWorkoutsRealtime(categoryToLoad);
        }
    }


    private void setupMoreForYouCards() {
        findViewById(R.id.cardWaterReminder).setOnClickListener(v -> {
            Intent intent = new Intent(this, WaterReminderActivity.class);
            intent.putExtra("NguoiDungId", userId);
            startActivity(intent);
        });

        findViewById(R.id.cardSleepReminder).setOnClickListener(v -> {
            Intent intent = new Intent(this, SleepReminderActivity.class);
            intent.putExtra("NguoiDungId", userId);
            startActivity(intent);
        });

        findViewById(R.id.cardHealthMetrics).setOnClickListener(v -> {
            Intent intent = new Intent(this, HealthMetricsActivity.class);
            intent.putExtra("NguoiDungId", userId);
            intent.putExtra("BMI", bmi);
            startActivity(intent);
        });
    }

    @Override
    protected String getUserId() {
        return userId;
    }

    @Override
    protected float getCurrentBmi() {
        return bmi;
    }
}