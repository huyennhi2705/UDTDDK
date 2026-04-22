package com.example.udtddk.admin;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.example.udtddk.R;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class Admindashboardactivity extends AppCompatActivity {

    private TextView tvTotalUsers, tvUnderCount, tvNormalCount, tvOverCount, tvObeseCount, tvNoDataCount;
    private CardView cardManageUsers, cardManageWorkouts, cardStats;
    private ImageView btnBack;

    private DatabaseReference userRef;
    private DatabaseReference workoutRef;
    private TextView tvTotalWorkouts;
    private static final String DB_URL = "https://udtddk-default-rtdb.firebaseio.com/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admindashboardactivity);

        // Ánh xạ View
        tvTotalUsers = findViewById(R.id.tvTotalUsers);
        tvUnderCount = findViewById(R.id.tvUnderCount);
        tvNormalCount = findViewById(R.id.tvNormalCount);
        tvOverCount = findViewById(R.id.tvOverCount);
        tvObeseCount = findViewById(R.id.tvObeseCount);
        tvNoDataCount = findViewById(R.id.tvNoDataCount);
        tvTotalWorkouts = findViewById(R.id.tvTotalWorkouts);
        cardManageUsers = findViewById(R.id.cardManageUsers);
        cardManageWorkouts = findViewById(R.id.cardManageWorkouts);
        cardStats = findViewById(R.id.cardStats);
        btnBack = findViewById(R.id.btnBack);

        // Firebase
        FirebaseDatabase database = FirebaseDatabase.getInstance("https://udtddk-default-rtdb.firebaseio.com/");
        userRef = database.getReference("NguoiDung");
        workoutRef = database.getReference("BaiTap");
        // Load dashboard
        loadDashboardStats();
        loadWorkoutCount();

        // Back
        btnBack.setOnClickListener(v -> finish());

        // Sang quản lý user
        cardManageUsers.setOnClickListener(v -> {
            Intent intent = new Intent(Admindashboardactivity.this, Adminusseractivity.class);
            startActivity(intent);
        });


        cardManageWorkouts.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Admindashboardactivity.this, Adminworkoutsactivity.class);
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(Admindashboardactivity.this,
                        "Chức năng Quản lý bài tập chưa được triển khai",
                        Toast.LENGTH_LONG).show();
                e.printStackTrace();
            }
        });

        // Thống kê
        cardStats.setOnClickListener(v ->
                startActivity(new Intent(this, Adminstatsactivity.class)));
    }
    private void loadWorkoutCount() {
        workoutRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                int total = (int) snapshot.getChildrenCount();
                tvTotalWorkouts.setText(String.valueOf(total));
            }

            @Override
            public void onCancelled(DatabaseError error) {
            }
        });
    }
    private void loadDashboardStats() {
        userRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                int totalUsers = 0;
                int underWeight = 0;
                int normalWeight = 0;
                int overWeight = 0;
                int obese = 0;
                int noData = 0;

                for (DataSnapshot data : snapshot.getChildren()) {

                    if (!data.hasChild("HoTen") && !data.hasChild("hoTen")) {
                        continue;
                    }
                    totalUsers++;
                    double bmi = 0;
                    // lấy BMI
                    Object bmiObj = data.child("BMI").getValue();
                    if (bmiObj != null) {
                        try {
                            bmi = Double.parseDouble(bmiObj.toString());
                        } catch (Exception ignored) {}
                    }
                    // tính lại nếu chưa có
                    if (bmi <= 0) {
                        Object weightObj = data.child("CanNang").getValue();
                        if (weightObj == null) {
                            weightObj = data.child("canNang").getValue();
                        }

                        Object heightObj = data.child("ChieuCao").getValue();
                        if (heightObj == null) {
                            heightObj = data.child("chieuCao").getValue();
                        }

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
                    // phân loại chuẩn
                    if (bmi <= 0) {
                        noData++;
                    } else if (bmi < 18.5) {
                        underWeight++;
                    } else if (bmi < 25) {
                        normalWeight++;
                    } else if (bmi < 30) {
                        overWeight++;
                    } else {
                        obese++;
                    }
                }

                tvTotalUsers.setText(String.valueOf(totalUsers));
                tvUnderCount.setText(String.valueOf(underWeight));
                tvNormalCount.setText(String.valueOf(normalWeight));
                tvOverCount.setText(String.valueOf(overWeight));
                tvObeseCount.setText(String.valueOf(obese));
                tvNoDataCount.setText(String.valueOf(noData));
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(Admindashboardactivity.this,
                        "Lỗi: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }
}