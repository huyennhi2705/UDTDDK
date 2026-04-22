    package com.example.udtddk.baitap;

    import android.app.DatePickerDialog;
    import android.app.TimePickerDialog;
    import android.graphics.Color;
    import android.os.Bundle;
    import android.text.TextUtils;
    import android.view.LayoutInflater;
    import android.view.View;
    import android.widget.EditText;
    import android.widget.ImageView;
    import android.widget.LinearLayout;
    import android.widget.TextView;
    import android.widget.Toast;

    import androidx.annotation.NonNull;
    import androidx.appcompat.app.AppCompatActivity;
    import androidx.cardview.widget.CardView;

    import com.example.udtddk.R;
    import com.example.udtddk.models.HealthRecord;
    import com.google.android.material.bottomnavigation.BottomNavigationView;
    import com.google.firebase.auth.FirebaseAuth;
    import com.google.firebase.auth.FirebaseUser;
    import com.google.firebase.database.DataSnapshot;
    import com.google.firebase.database.DatabaseError;
    import com.google.firebase.database.DatabaseReference;
    import com.google.firebase.database.FirebaseDatabase;
    import com.google.firebase.database.ValueEventListener;

    import java.text.ParseException;
    import java.text.SimpleDateFormat;
    import java.util.ArrayList;
    import java.util.Calendar;
    import java.util.Collections;
    import java.util.Date;
    import java.util.List;
    import java.util.Locale;

    public class HealthMetricsActivity extends AppCompatActivity {


        private ImageView btnBack;
        private LinearLayout btnPickDate, btnSleepTime, btnWakeTime;
        private CardView btnSave, chipWeekly, chipMonthly;
        private CardView tabByDay, tabByMonth, cardResult, cardHistory;
        private TextView tvSelectedDate, tvLastUpdated;
        private TextView tvSleepTime, tvWakeTime;
        private TextView tvChipWeekly, tvChipMonthly;
        private TextView tvTabByDay, tvTabByMonth;
        private EditText etWater, etHeight, etWeight;

        // Result
        private TextView tvResultBmi, tvResultBmiCategory;
        private TextView tvResultSleepHours, tvResultSleepStatus;
        private TextView tvResultWater, tvResultWaterStatus;
        private TextView tvAdvice;


        private LinearLayout layoutHistoryList;


        private DatabaseReference userRef;
        private String userId;


        private String selectedDate;
        private final Calendar calendar = Calendar.getInstance();
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        private final List<HealthRecord> healthRecords = new ArrayList<>();
        private boolean isWeeklyMode = true;
        private boolean isHistoryByDay = true;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_health_metrics);

            initViews();
            initFirebase();
            setupDefaultValues();
            setupListeners();
            loadAllHealthHistory();
        }


        private void initViews() {
            btnBack = findViewById(R.id.btnBack);
            btnPickDate = findViewById(R.id.btnPickDate);
            btnSleepTime = findViewById(R.id.btnSleepTime);
            btnWakeTime = findViewById(R.id.btnWakeTime);
            btnSave = findViewById(R.id.btnSave);
            chipWeekly = findViewById(R.id.chipWeekly);
            chipMonthly = findViewById(R.id.chipMonthly);
            tabByDay = findViewById(R.id.tabByDay);
            tabByMonth = findViewById(R.id.tabByMonth);
            cardResult = findViewById(R.id.cardResult);
            cardHistory = findViewById(R.id.cardHistory);
            tvSelectedDate = findViewById(R.id.tvSelectedDate);
            tvLastUpdated = findViewById(R.id.tvLastUpdated);
            tvSleepTime = findViewById(R.id.tvSleepTime);
            tvWakeTime = findViewById(R.id.tvWakeTime);
            tvChipWeekly = findViewById(R.id.tvChipWeekly);
            tvChipMonthly = findViewById(R.id.tvChipMonthly);
            tvTabByDay = findViewById(R.id.tvTabByDay);
            tvTabByMonth = findViewById(R.id.tvTabByMonth);
            etWater = findViewById(R.id.etWater);
            etHeight = findViewById(R.id.etHeight);
            etWeight = findViewById(R.id.etWeight);

            tvResultBmi = findViewById(R.id.tvResultBmi);
            tvResultBmiCategory = findViewById(R.id.tvResultBmiCategory);
            tvResultSleepHours = findViewById(R.id.tvResultSleepHours);
            tvResultSleepStatus = findViewById(R.id.tvResultSleepStatus);
            tvResultWater = findViewById(R.id.tvResultWater);
            tvResultWaterStatus = findViewById(R.id.tvResultWaterStatus);
            tvAdvice = findViewById(R.id.tvAdvice);
            layoutHistoryList = findViewById(R.id.layoutHistoryList);

            BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
            if (bottomNav != null) {
                bottomNav.setSelectedItemId(R.id.nav_health);
            }
        }


        private void initFirebase() {
            userId = getIntent().getStringExtra("NguoiDungId");
            if (userId == null || userId.isEmpty()) {
                FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                if (currentUser != null) {
                    userId = currentUser.getUid();
                }
            }
            if (userId == null || userId.isEmpty()) {
                userId = getSharedPreferences("USER", MODE_PRIVATE)
                        .getString("NguoiDungId", null);
            }
            if (userId == null || userId.isEmpty()) {
                Toast.makeText(this, "Chưa đăng nhập", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            userRef = FirebaseDatabase.getInstance("https://udtddk-default-rtdb.firebaseio.com/")
                    .getReference("NguoiDung")
                    .child(userId);
        }


        private void setupDefaultValues() {
            selectedDate = dateFormat.format(new Date());
            tvSelectedDate.setText("Hôm nay");
            tvSleepTime.setText("22:00");
            tvWakeTime.setText("06:00");
        }


        private void setupListeners() {
            btnBack.setOnClickListener(v -> finish());
            btnPickDate.setOnClickListener(v -> showDatePicker());
            btnSleepTime.setOnClickListener(v -> showTimePicker(tvSleepTime));
            btnWakeTime.setOnClickListener(v -> showTimePicker(tvWakeTime));
            btnSave.setOnClickListener(v -> saveHealthData());

            chipWeekly.setOnClickListener(v -> {
                isWeeklyMode = true;
                updateFrequencyChips();
                renderHistory();
            });

            chipMonthly.setOnClickListener(v -> {
                isWeeklyMode = false;
                updateFrequencyChips();
                renderHistory();
            });

            tabByDay.setOnClickListener(v -> {
                isHistoryByDay = true;
                updateHistoryTabs();
                renderHistory();
            });

            tabByMonth.setOnClickListener(v -> {
                isHistoryByDay = false;
                updateHistoryTabs();
                renderHistory();
            });
        }


        private void showDatePicker() {
            Calendar c = Calendar.getInstance();
            new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                Calendar picked = Calendar.getInstance();
                picked.set(year, month, dayOfMonth);
                selectedDate = dateFormat.format(picked.getTime());

                if (selectedDate.equals(dateFormat.format(new Date()))) {
                    tvSelectedDate.setText("Hôm nay");
                } else {
                    tvSelectedDate.setText(selectedDate);
                }
                loadHealthDataByDate(selectedDate);
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        }


        private void showTimePicker(TextView target) {
            Calendar c = Calendar.getInstance();
            new TimePickerDialog(this, (view, hourOfDay, minute) -> {
                String time = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute);
                target.setText(time);
            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show();
        }


        private void saveHealthData() {
            String waterStr = etWater.getText().toString().trim();
            String heightStr = etHeight.getText().toString().trim();
            String weightStr = etWeight.getText().toString().trim();
            String gioNgu = tvSleepTime.getText().toString().trim();
            String gioThuc = tvWakeTime.getText().toString().trim();

            if (TextUtils.isEmpty(waterStr) || TextUtils.isEmpty(heightStr) || TextUtils.isEmpty(weightStr)) {
                Toast.makeText(this, "Vui lòng nhập đầy đủ: Lượng nước, Chiều cao, Cân nặng", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                float luongNuoc = Float.parseFloat(waterStr);
                float chieuCao = Float.parseFloat(heightStr);
                float canNang = Float.parseFloat(weightStr);

                if (chieuCao <= 0 || canNang <= 0 || luongNuoc < 0) {
                    Toast.makeText(this, "Chiều cao và cân nặng phải lớn hơn 0", Toast.LENGTH_SHORT).show();
                    return;
                }

                float thoiGianNgu = calculateSleepHours(gioNgu, gioThuc);
                float bmi = calculateBMI(canNang, chieuCao);

                HealthRecord record = new HealthRecord(selectedDate, canNang, chieuCao, luongNuoc,
                        thoiGianNgu, gioNgu, gioThuc, bmi);

                userRef.child("ChiSoSucKhoe").child(selectedDate)
                        .setValue(record)
                        .addOnSuccessListener(unused -> {
                            userRef.child("CanNang").setValue(canNang);
                            userRef.child("ChieuCao").setValue(chieuCao);
                            userRef.child("LuongNuoc").setValue(luongNuoc);
                            userRef.child("ThoiGianNgu").setValue(thoiGianNgu);
                            userRef.child("BMI").setValue(bmi);

                            showResult(record);
                            loadAllHealthHistory();
                            Toast.makeText(this, "✅ Đã lưu chỉ số sức khỏe thành công!", Toast.LENGTH_SHORT).show();
                        })
                        .addOnFailureListener(e ->
                                Toast.makeText(this, "Lưu thất bại: " + e.getMessage(), Toast.LENGTH_SHORT).show());

            } catch (NumberFormatException e) {
                Toast.makeText(this, "Dữ liệu phải là số hợp lệ (ví dụ: 1580, 165, 52)", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        }

        private void loadHealthDataByDate(String date) {
            userRef.child("ChiSoSucKhoe").child(date)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            if (snapshot.exists()) {
                                HealthRecord record = snapshot.getValue(HealthRecord.class);
                                if (record != null) {
                                    if (record.getNgayTao() == null || record.getNgayTao().isEmpty()) {
                                        record.setNgayTao(date);
                                    }
                                    fillForm(record);
                                    showResult(record);
                                    tvLastUpdated.setVisibility(View.VISIBLE);
                                    tvLastUpdated.setText("Đã có dữ liệu cho ngày: " + date);
                                }
                            } else {
                                clearFormForNewDate();
                                tvLastUpdated.setVisibility(View.VISIBLE);
                                tvLastUpdated.setText("Chưa có dữ liệu cho ngày: " + date);
                                cardResult.setVisibility(View.GONE);
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Toast.makeText(HealthMetricsActivity.this, "Lỗi tải dữ liệu", Toast.LENGTH_SHORT).show();
                        }
                    });
        }

        // =========================================================
        // 9) ĐỔ DỮ LIỆU VÀO FORM
        // =========================================================
        private void fillForm(HealthRecord record) {
            etWater.setText(String.valueOf(record.getLuongNuoc()));
            etHeight.setText(String.valueOf(record.getChieuCao()));
            etWeight.setText(String.valueOf(record.getCanNang()));
            tvSleepTime.setText(record.getGioNgu() != null ? record.getGioNgu() : "22:00");
            tvWakeTime.setText(record.getGioThuc() != null ? record.getGioThuc() : "06:00");
        }

        // =========================================================
        // 10) XÓA FORM KHI CHƯA CÓ DỮ LIỆU
        // =========================================================
        private void clearFormForNewDate() {
            etWater.setText("");
            etHeight.setText("");
            etWeight.setText("");
            tvSleepTime.setText("22:00");
            tvWakeTime.setText("06:00");
            cardResult.setVisibility(View.GONE);
        }

        // =========================================================
        // 11) HIỂN THỊ KẾT QUẢ
        // =========================================================
        private void showResult(HealthRecord record) {
            cardResult.setVisibility(View.VISIBLE);

            tvResultBmi.setText(String.format(Locale.getDefault(), "%.1f", record.getBMI()));
            tvResultBmiCategory.setText(getBmiCategory(record.getBMI()));
            tvResultBmiCategory.setTextColor(getBmiColor(record.getBMI()));

            tvResultSleepHours.setText(String.format(Locale.getDefault(), "%.1f", record.getThoiGianNgu()));
            tvResultSleepStatus.setText(getSleepStatus(record.getThoiGianNgu()));
            tvResultSleepStatus.setTextColor(getSleepColor(record.getThoiGianNgu()));

            tvResultWater.setText(String.format(Locale.getDefault(), "%.0f", record.getLuongNuoc()));
            tvResultWaterStatus.setText(getWaterStatus(record.getLuongNuoc()));
            tvResultWaterStatus.setTextColor(getWaterColor(record.getLuongNuoc()));

            tvAdvice.setText(generateAdvice(record));
        }


        private void loadAllHealthHistory() {
            userRef.child("ChiSoSucKhoe")
                    .addValueEventListener(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            healthRecords.clear();
                            for (DataSnapshot child : snapshot.getChildren()) {
                                HealthRecord record = child.getValue(HealthRecord.class);
                                if (record != null) {
                                    if (record.getNgayTao() == null || record.getNgayTao().isEmpty()) {
                                        record.setNgayTao(child.getKey());
                                    }
                                    healthRecords.add(record);
                                }
                            }
                            Collections.sort(healthRecords, (a, b) -> b.getNgayTao().compareTo(a.getNgayTao()));

                            if (!healthRecords.isEmpty()) {
                                cardHistory.setVisibility(View.VISIBLE);
                                renderHistory();
                            } else {
                                cardHistory.setVisibility(View.GONE);
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Toast.makeText(HealthMetricsActivity.this, "Lỗi tải lịch sử", Toast.LENGTH_SHORT).show();
                        }
                    });
        }


        private void renderHistory() {
            layoutHistoryList.removeAllViews();
            if (healthRecords.isEmpty()) {
                addSimpleHistoryText("Chưa có dữ liệu sức khỏe.");
                return;
            }

            if (isHistoryByDay) {
                renderDailyHistory();
            } else {
                renderMonthlyAnalysis();
            }
        }

        private void renderDailyHistory() {
            int limit = isWeeklyMode ? 7 : 30;
            int count = Math.min(limit, healthRecords.size());
            for (int i = 0; i < count; i++) {
                addHistoryItem(healthRecords.get(i));
            }
            layoutHistoryList.addView(createAnalysisBlock(
                    isWeeklyMode ? compareLastTwoWeeks(healthRecords) : compareLast30Days(healthRecords)
            ));
        }

        private void renderMonthlyAnalysis() {
            String analysis = buildMonthlySummary(healthRecords);
            layoutHistoryList.addView(createAnalysisBlock(analysis));
        }


        private void addHistoryItem(HealthRecord record) {
            View item = LayoutInflater.from(this).inflate(R.layout.item_health_history, layoutHistoryList, false);
            TextView tvDate = item.findViewById(R.id.tvItemDate);
            TextView tvInfo = item.findViewById(R.id.tvItemInfo);

            tvDate.setText(record.getNgayTao());

            String info = "⚖️ " + String.format(Locale.getDefault(), "%.1f", record.getCanNang()) + " kg" +
                    " • 💧 " + String.format(Locale.getDefault(), "%.0f", record.getLuongNuoc()) + " ml" +
                    " • 😴 " + String.format(Locale.getDefault(), "%.1f", record.getThoiGianNgu()) + " giờ" +
                    " • BMI " + String.format(Locale.getDefault(), "%.1f", record.getBMI());

            tvInfo.setText(info);
            layoutHistoryList.addView(item);
        }

        private void addSimpleHistoryText(String text) {
            TextView tv = new TextView(this);
            tv.setText(text);
            tv.setTextSize(13f);
            tv.setTextColor(Color.parseColor("#3D6B52"));
            tv.setPadding(8, 8, 8, 8);
            layoutHistoryList.addView(tv);
        }

        private View createAnalysisBlock(String text) {
            TextView tv = new TextView(this);
            tv.setText(text);
            tv.setTextSize(13f);
            tv.setTextColor(Color.parseColor("#0F2D1F"));
            tv.setBackgroundResource(R.drawable.bg_eval_stat);
            tv.setPadding(24, 24, 24, 24);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.topMargin = 16;
            tv.setLayoutParams(params);
            return tv;
        }


        private void updateFrequencyChips() {
            if (isWeeklyMode) {
                chipWeekly.setCardBackgroundColor(Color.parseColor("#1A6B43"));
                chipMonthly.setCardBackgroundColor(Color.parseColor("#EAF7EF"));
                tvChipWeekly.setTextColor(Color.WHITE);
                tvChipMonthly.setTextColor(Color.parseColor("#7A9E8A"));
            } else {
                chipWeekly.setCardBackgroundColor(Color.parseColor("#EAF7EF"));
                chipMonthly.setCardBackgroundColor(Color.parseColor("#1A6B43"));
                tvChipWeekly.setTextColor(Color.parseColor("#7A9E8A"));
                tvChipMonthly.setTextColor(Color.WHITE);
            }
        }

        private void updateHistoryTabs() {
            if (isHistoryByDay) {
                tabByDay.setCardBackgroundColor(Color.parseColor("#1A6B43"));
                tabByMonth.setCardBackgroundColor(Color.parseColor("#EAF7EF"));
                tvTabByDay.setTextColor(Color.WHITE);
                tvTabByMonth.setTextColor(Color.parseColor("#7A9E8A"));
            } else {
                tabByDay.setCardBackgroundColor(Color.parseColor("#EAF7EF"));
                tabByMonth.setCardBackgroundColor(Color.parseColor("#1A6B43"));
                tvTabByDay.setTextColor(Color.parseColor("#7A9E8A"));
                tvTabByMonth.setTextColor(Color.WHITE);
            }
        }


        private float calculateBMI(float weight, float heightCm) {
            float heightM = heightCm / 100f;
            return weight / (heightM * heightM);
        }

        private float calculateSleepHours(String sleepTime, String wakeTime) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                Date sleep = sdf.parse(sleepTime);
                Date wake = sdf.parse(wakeTime);
                if (sleep == null || wake == null) return 0f;

                long diff = wake.getTime() - sleep.getTime();
                if (diff < 0) diff += 24L * 60 * 60 * 1000; // ngủ qua đêm
                return diff / (1000f * 60f * 60f);
            } catch (ParseException e) {
                return 0f;
            }
        }


        private String getBmiCategory(float bmi) {
            if (bmi < 18.5f) return "Thiếu cân";
            if (bmi < 23f) return "Bình thường";
            if (bmi < 25f) return "Thừa cân";
            return "Béo phì";
        }

        private int getBmiColor(float bmi) {
            if (bmi < 18.5f) return Color.parseColor("#F59E0B");
            if (bmi < 23f) return Color.parseColor("#16A34A");
            if (bmi < 25f) return Color.parseColor("#F97316");
            return Color.parseColor("#DC2626");
        }

        private String getSleepStatus(float sleep) {
            if (sleep < 6) return "Ngủ thiếu";
            if (sleep <= 8) return "Ngủ tốt";
            return "Ngủ hơi nhiều";
        }

        private int getSleepColor(float sleep) {
            if (sleep < 6) return Color.parseColor("#DC2626");
            if (sleep <= 8) return Color.parseColor("#16A34A");
            return Color.parseColor("#F59E0B");
        }

        private String getWaterStatus(float water) {
            if (water < 1500) return "Uống chưa đủ";
            if (water <= 2500) return "Đủ nước";
            return "Rất tốt";
        }

        private int getWaterColor(float water) {
            if (water < 1500) return Color.parseColor("#DC2626");
            if (water <= 2500) return Color.parseColor("#0284C7");
            return Color.parseColor("#16A34A");
        }

        private String generateAdvice(HealthRecord record) {
            StringBuilder advice = new StringBuilder();
            if (record.getBMI() < 18.5f) {
                advice.append("• Bạn đang hơi thiếu cân, nên bổ sung dinh dưỡng hợp lý.\n");
            } else if (record.getBMI() >= 25f) {
                advice.append("• BMI hơi cao, nên tăng vận động và điều chỉnh ăn uống.\n");
            } else {
                advice.append("• BMI đang ở mức khá ổn.\n");
            }

            if (record.getLuongNuoc() < 1500) {
                advice.append("• Bạn nên uống thêm nước trong ngày.\n");
            } else {
                advice.append("• Lượng nước hôm nay khá tốt.\n");
            }

            if (record.getThoiGianNgu() < 6) {
                advice.append("• Bạn nên ngủ sớm hơn để phục hồi cơ thể tốt hơn.");
            } else if (record.getThoiGianNgu() <= 8) {
                advice.append("• Giấc ngủ hôm nay khá ổn.");
            } else {
                advice.append("• Bạn ngủ khá đủ, tiếp tục duy trì.");
            }
            return advice.toString();
        }

        private String getWeightTrend(float first, float last) {
            float diff = last - first;
            if (diff > 0.1f) return "📈 Tăng " + String.format(Locale.getDefault(), "%.1f", diff) + " kg";
            if (diff < -0.1f) return "📉 Giảm " + String.format(Locale.getDefault(), "%.1f", Math.abs(diff)) + " kg";
            return "➖ Cân nặng ổn định";
        }

        private String getWaterTrend(float avgOld, float avgNew) {
            float diff = avgNew - avgOld;
            if (diff > 50) return "💧 Uống nước tốt hơn: +" + String.format(Locale.getDefault(), "%.0f", diff) + " ml/ngày";
            if (diff < -50) return "⚠️ Lượng nước giảm: -" + String.format(Locale.getDefault(), "%.0f", Math.abs(diff)) + " ml/ngày";
            return "➖ Lượng nước uống ổn định";
        }

        private String getSleepTrend(float avgOld, float avgNew) {
            float diff = avgNew - avgOld;
            if (diff > 0.2f) return "😴 Giấc ngủ cải thiện +" + String.format(Locale.getDefault(), "%.1f", diff) + " giờ";
            if (diff < -0.2f) return "⚠️ Giấc ngủ giảm -" + String.format(Locale.getDefault(), "%.1f", Math.abs(diff)) + " giờ";
            return "➖ Giấc ngủ ổn định";
        }

        private float calculateAverageWater(List<HealthRecord> list) {
            if (list == null || list.isEmpty()) return 0f;
            float total = 0f;
            int count = 0;
            for (HealthRecord r : list) {
                if (r.getLuongNuoc() > 0) {
                    total += r.getLuongNuoc();
                    count++;
                }
            }
            return count == 0 ? 0f : total / count;
        }

        private float calculateAverageSleep(List<HealthRecord> list) {
            if (list == null || list.isEmpty()) return 0f;
            float total = 0f;
            int count = 0;
            for (HealthRecord r : list) {
                if (r.getThoiGianNgu() > 0) {
                    total += r.getThoiGianNgu();
                    count++;
                }
            }
            return count == 0 ? 0f : total / count;
        }

        private String compareLastTwoWeeks(List<HealthRecord> records) {
            if (records.size() < 14) return "⚠️ Chưa đủ dữ liệu để so sánh 2 tuần (cần ít nhất 14 ngày).";

            List<HealthRecord> sorted = new ArrayList<>(records);
            Collections.sort(sorted, (a, b) -> a.getNgayTao().compareTo(b.getNgayTao()));

            List<HealthRecord> last14 = sorted.subList(sorted.size() - 14, sorted.size());
            List<HealthRecord> week1 = last14.subList(0, 7);
            List<HealthRecord> week2 = last14.subList(7, 14);

            float weightStart = week1.get(0).getCanNang();
            float weightEnd = week2.get(week2.size() - 1).getCanNang();
            float avgWaterWeek1 = calculateAverageWater(week1);
            float avgWaterWeek2 = calculateAverageWater(week2);
            float avgSleepWeek1 = calculateAverageSleep(week1);
            float avgSleepWeek2 = calculateAverageSleep(week2);

            return "📊 SO SÁNH 2 TUẦN GẦN NHẤT\n\n" +
                    "⚖️ Cân nặng: " + getWeightTrend(weightStart, weightEnd) + "\n\n" +
                    "💧 Nước uống:\n" +
                    "• Tuần trước: " + String.format(Locale.getDefault(), "%.0f", avgWaterWeek1) + " ml/ngày\n" +
                    "• Tuần này: " + String.format(Locale.getDefault(), "%.0f", avgWaterWeek2) + " ml/ngày\n" +
                    "• Xu hướng: " + getWaterTrend(avgWaterWeek1, avgWaterWeek2) + "\n\n" +
                    "😴 Giấc ngủ:\n" +
                    "• Tuần trước: " + String.format(Locale.getDefault(), "%.1f", avgSleepWeek1) + " giờ/ngày\n" +
                    "• Tuần này: " + String.format(Locale.getDefault(), "%.1f", avgSleepWeek2) + " giờ/ngày\n" +
                    "• Xu hướng: " + getSleepTrend(avgSleepWeek1, avgSleepWeek2);
        }

        private String compareLast30Days(List<HealthRecord> records) {
            if (records.size() < 30) return "⚠️ Chưa đủ dữ liệu để so sánh 30 ngày (cần ít nhất 30 ngày).";

            List<HealthRecord> sorted = new ArrayList<>(records);
            Collections.sort(sorted, (a, b) -> a.getNgayTao().compareTo(b.getNgayTao()));

            List<HealthRecord> last30 = sorted.subList(sorted.size() - 30, sorted.size());
            List<HealthRecord> first15 = last30.subList(0, 15);
            List<HealthRecord> last15 = last30.subList(15, 30);

            float weightStart = first15.get(0).getCanNang();
            float weightEnd = last15.get(last15.size() - 1).getCanNang();
            float avgWaterOld = calculateAverageWater(first15);
            float avgWaterNew = calculateAverageWater(last15);
            float avgSleepOld = calculateAverageSleep(first15);
            float avgSleepNew = calculateAverageSleep(last15);

            return "📅 SO SÁNH 30 NGÀY GẦN NHẤT\n\n" +
                    "⚖️ Cân nặng: " + getWeightTrend(weightStart, weightEnd) + "\n\n" +
                    "💧 Nước uống:\n" +
                    "• 15 ngày đầu: " + String.format(Locale.getDefault(), "%.0f", avgWaterOld) + " ml/ngày\n" +
                    "• 15 ngày sau: " + String.format(Locale.getDefault(), "%.0f", avgWaterNew) + " ml/ngày\n" +
                    "• Xu hướng: " + getWaterTrend(avgWaterOld, avgWaterNew) + "\n\n" +
                    "😴 Giấc ngủ:\n" +
                    "• 15 ngày đầu: " + String.format(Locale.getDefault(), "%.1f", avgSleepOld) + " giờ/ngày\n" +
                    "• 15 ngày sau: " + String.format(Locale.getDefault(), "%.1f", avgSleepNew) + " giờ/ngày\n" +
                    "• Xu hướng: " + getSleepTrend(avgSleepOld, avgSleepNew);
        }

        private String buildMonthlySummary(List<HealthRecord> records) {
            if (records.isEmpty()) return "Chưa có dữ liệu để phân tích theo tháng.";

            int count = Math.min(30, records.size());
            List<HealthRecord> lastRecords = records.subList(0, count);

            float avgWeight = 0f, avgWater = 0f, avgSleep = 0f;
            for (HealthRecord r : lastRecords) {
                avgWeight += r.getCanNang();
                avgWater += r.getLuongNuoc();
                avgSleep += r.getThoiGianNgu();
            }
            avgWeight /= count;
            avgWater /= count;
            avgSleep /= count;

            return "📊 PHÂN TÍCH TỔNG QUAN " + count + " NGÀY GẦN NHẤT\n\n" +
                    "⚖️ Cân nặng trung bình: " + String.format(Locale.getDefault(), "%.1f", avgWeight) + " kg\n" +
                    "💧 Nước uống trung bình: " + String.format(Locale.getDefault(), "%.0f", avgWater) + " ml/ngày\n" +
                    "😴 Giấc ngủ trung bình: " + String.format(Locale.getDefault(), "%.1f", avgSleep) + " giờ/ngày\n\n" +
                    (count >= 30 ? compareLast30Days(new ArrayList<>(records)) : "⚠️ Cần thêm dữ liệu để phân tích sâu hơn.");
        }
    }