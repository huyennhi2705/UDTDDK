package com.example.udtddk;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.example.udtddk.history.HistoryActivity;
import com.example.udtddk.ai.ChatActivity;
import com.example.udtddk.auth.LoginActivity;
import com.example.udtddk.bmi.BmiGaugeView;
import com.example.udtddk.profile.ProfileActivity;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import android.util.Log;
import com.google.firebase.messaging.FirebaseMessaging;
import java.util.Calendar;
import java.util.Locale;

public class MainActivity extends BaseActivity {
    // test git
    private TextView tvUserName, tvWeight, tvHeight, tvAge, tvAvatar, tvBmiValue, tvBmiStatus;
    private EditText etWeightInput, etHeightInput;
    private ImageView btnNotification;
    private Button btnCalcBmi;

    private BmiGaugeView bmiGauge;

    private FirebaseDatabase database;
    private DatabaseReference db;

    private String userId;
    private float currentBmi = 0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Firebase
        database = FirebaseDatabase.getInstance("https://udtddk-default-rtdb.firebaseio.com/");
        db = database.getReference("NguoiDung");

        // Ánh xạ view
        tvUserName    = findViewById(R.id.tvUsername);
        btnNotification = findViewById(R.id.btnNotification);
        tvWeight      = findViewById(R.id.tvWeight);
        tvHeight      = findViewById(R.id.tvHeight);
        tvAge         = findViewById(R.id.tvAge);
        tvAvatar      = findViewById(R.id.tvAvatar);
        tvBmiValue    = findViewById(R.id.tvBmiValue);
        tvBmiStatus   = findViewById(R.id.tvBmiStatus);
        etWeightInput = findViewById(R.id.etWeightInput);
        etHeightInput = findViewById(R.id.etHeightInput);
        btnCalcBmi    = findViewById(R.id.btnCalcBmi);
        bmiGauge      = findViewById(R.id.bmiGauge);

        userId = getIntent().getStringExtra("NguoiDungId");

        if (userId == null) {
            userId = getSharedPreferences("USER", MODE_PRIVATE)
                    .getString("NguoiDungId", null);
        }

        // Load dữ liệu từ Firebase
        loadUserData();

        // Nút tính lại BMI
        btnCalcBmi.setOnClickListener(v -> {
            String weightStr = etWeightInput.getText().toString().trim();
            String heightStr = etHeightInput.getText().toString().trim();

            if (TextUtils.isEmpty(weightStr)) {
                etWeightInput.setError("Nhập cân nặng");
                etWeightInput.requestFocus();
                return;
            }

            if (TextUtils.isEmpty(heightStr)) {
                etHeightInput.setError("Nhập chiều cao");
                etHeightInput.requestFocus();
                return;
            }


            float weight = Float.parseFloat(weightStr);
            float height = Float.parseFloat(heightStr);


            calculateBMI(weightStr, heightStr);

            tvWeight.setText(weightStr.contains(".")
                    ? weightStr.replaceAll("\\.?0+$", "")
                    : weightStr);
            tvHeight.setText(heightStr.contains(".")
                    ? heightStr.replaceAll("\\.?0+$", "")
                    : heightStr);

            // Lưu CSDL
            updateUserBody(weight, height);

            Toast.makeText(this, "Đã cập nhật chỉ số!", Toast.LENGTH_SHORT).show();
        });

        // Phần này là hiển thị thông báo
        String finalUserId = userId;
        btnNotification.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, HistoryActivity.class);
            intent.putExtra("userId", finalUserId);
            startActivity(intent);
        });
        // Bottom nav
        setupBottomNav(R.id.nav_home);

        // Mở chat AI
        String finalUserId1 = userId;
        findViewById(R.id.btnOpenChat).setOnClickListener(v -> {
            Intent intent = new Intent(this, ChatActivity.class);
            intent.putExtra("userId", finalUserId1);
            intent.putExtra("BMI", currentBmi);
            startActivity(intent);
        });

        // Bấm avatar -> sang profile
        String finalUserId2 = userId;
        tvAvatar.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ProfileActivity.class);
            intent.putExtra("userId", finalUserId2);
            startActivity(intent);
        });
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.e("FCM_TOKEN", "Lỗi lấy token", task.getException());
                        return;
                    }

                    String token = task.getResult();
                    Log.e("FCM_TOKEN", "TOKEN = " + token);

//                    Toast.makeText(this, "Token:\n" + token, Toast.LENGTH_LONG).show();
                });
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(new String[]{
                    android.Manifest.permission.POST_NOTIFICATIONS
            }, 101);
        }
    }



    @Override
    protected String getUserId() {
        return userId;
    }

    @Override
    protected float getCurrentBmi() {
        return currentBmi;
    }

    private void loadUserData() {
        db.child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    Toast.makeText(MainActivity.this, "Không tìm thấy dữ liệu người dùng", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Lấy dữ liệu từ Firebase
                String username = snapshot.child("HoTen").getValue(String.class);
                //  String fullName = snapshot.child("fullName").getValue(String.class);
                String email    = snapshot.child("Email").getValue(String.class);
                String dob      = snapshot.child("NgaySinh").getValue(String.class);

                Object weightObj = snapshot.child("CanNang").getValue();
                Object heightObj = snapshot.child("ChieuCao").getValue();

                String weight = (weightObj != null ? weightObj.toString().replace(',', '.') : "0");
                String height = (heightObj != null ? heightObj.toString().replace(',', '.') : "0");

                if (!weight.matches("\\d+(\\.\\d+)?")) weight = "0";
                if (!height.matches("\\d+(\\.\\d+)?")) height = "0";


                if (!TextUtils.isEmpty(username)) {
                    tvUserName.setText(username);
                } else if (!TextUtils.isEmpty(email)) {
                    tvUserName.setText(email.split("@")[0]);
                } else {
                    tvUserName.setText("Người dùng");
                }

//
                String avatarText = "U";
                //if (!TextUtils.isEmpty(fullName)) {
                //     avatarText = fullName.substring(0, 1).toUpperCase();
                //}
                if (!TextUtils.isEmpty(username)) {
                    avatarText = username.substring(0, 1).toUpperCase();
                } else if (!TextUtils.isEmpty(email)) {
                    avatarText = email.substring(0, 1).toUpperCase();
                }
                tvAvatar.setText(avatarText);

                // Tuổi
                int age = calculateAge(dob);
                tvAge.setText(age > 0 ? String.valueOf(age) : "--");

                // Cân nặng / chiều cao
                tvWeight.setText(weight);
                tvHeight.setText(height);
                etWeightInput.setText(weight);
                etHeightInput.setText(height);

                // BMI
                calculateBMI(weight, height);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(MainActivity.this, "Lỗi tải dữ liệu", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private int calculateAge(String dob) {
        try {
            if (TextUtils.isEmpty(dob)) return 0;

            String[] parts = dob.split("/");
            if (parts.length != 3) return 0;

            int day = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            int year = Integer.parseInt(parts[2]);

            Calendar today = Calendar.getInstance();
            int currentYear = today.get(Calendar.YEAR);
            int currentMonth = today.get(Calendar.MONTH) + 1;
            int currentDay = today.get(Calendar.DAY_OF_MONTH);

            // Nếu năm sinh bị tương lai -> dữ liệu sai
            if (year > currentYear) return 0;

            int age = currentYear - year;

            // Nếu chưa tới sinh nhật năm nay thì trừ 1
            if (currentMonth < month || (currentMonth == month && currentDay < day)) {
                age--;
            }

            return Math.max(age, 0);
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    private void calculateBMI(String weightStr, String heightStr) {
        try {
            float weight = Float.parseFloat(weightStr);
            float heightCm = Float.parseFloat(heightStr);

            if (weight <= 0 || heightCm <= 0) {
                currentBmi = 0f;
                tvBmiValue.setText("0");
                tvBmiStatus.setText("Không hợp lệ");
                safeSetText(R.id.tvWhoClass, "-");
                safeSetText(R.id.tvEvalTitle, "-");
                safeSetText(R.id.tvEvalDesc, "-");
                safeSetText(R.id.tvIdealWeight, "0 – 0 kg");
                return;
            }
            float heightM = heightCm / 100f;
            float bmi = weight / (heightM * heightM);
            currentBmi = bmi;
            tvBmiValue.setText(String.format(Locale.getDefault(), "%.1f", bmi));
            bmiGauge.setBmi(bmi);
            String bmiStatus, whoClass, evalTitle, evalDesc;
            if (bmi < 18.5f) {
                bmiStatus = "Gầy";
                whoClass = "Thiếu cân";
                evalTitle = "Bạn hơi thiếu cân!";
                evalDesc = "Chỉ số BMI " + String.format(Locale.getDefault(), "%.1f", bmi)
                        + " thấp hơn ngưỡng lý tưởng (18.5–24.9). Hãy bổ sung dinh dưỡng phù hợp.";
            }
            else if (bmi < 25f) {
                bmiStatus = "Bình thường";
                whoClass = "Bình thường";
                evalTitle = "Sức khỏe của bạn đang tốt!";
                evalDesc = "Chỉ số BMI " + String.format(Locale.getDefault(), "%.1f", bmi)
                        + " nằm trong ngưỡng lý tưởng (18.5–24.9). Hãy duy trì lối sống hiện tại.";
            } else if (bmi < 30f) {
                bmiStatus = "Thừa cân";
                whoClass = "Thừa cân";
                evalTitle = "Bạn đang thừa cân!";
                evalDesc = "Chỉ số BMI " + String.format(Locale.getDefault(), "%.1f", bmi)
                        + " cao hơn ngưỡng lý tưởng (18.5–24.9). Hãy tập luyện và ăn uống khoa học.";
            }
            else {
                bmiStatus = "Béo phì";
                whoClass = "Béo phì";
                evalTitle = "Cẩn thận, bạn bị béo phì!";
                evalDesc = "Chỉ số BMI " + String.format(Locale.getDefault(), "%.1f", bmi)
                        + " vượt ngưỡng lý tưởng (18.5–24.9). Hãy theo dõi sức khỏe và tham khảo chuyên gia.";
            }
            tvBmiStatus.setText(bmiStatus);
            safeSetText(R.id.tvWhoClass, whoClass);
            safeSetText(R.id.tvEvalTitle, evalTitle);
            safeSetText(R.id.tvEvalDesc, evalDesc);
            float minIdeal = 18.5f * heightM * heightM;
            float maxIdeal = 24.9f * heightM * heightM;
            safeSetText(R.id.tvIdealWeight,
                    String.format(Locale.getDefault(), "%.0f – %.0f kg", minIdeal, maxIdeal));

        } catch (NumberFormatException e) {
            e.printStackTrace();
            Toast.makeText(this, "Dữ liệu cân nặng hoặc chiều cao không hợp lệ", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateUserBody(float weight, float height) {
        if (userId == null) return;

        DatabaseReference userRef = FirebaseDatabase
                .getInstance("https://udtddk-default-rtdb.firebaseio.com/")
                .getReference("NguoiDung")
                .child(userId);

        userRef.child("CanNang").setValue(weight);
        userRef.child("ChieuCao").setValue(height);
    }

    private void safeSetText(int viewId, String text) {
        TextView tv = findViewById(viewId);
        if (tv != null) {
            tv.setText(text);
        }
    }
}