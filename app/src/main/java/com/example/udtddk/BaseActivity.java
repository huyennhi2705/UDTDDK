package com.example.udtddk;

import android.content.Intent;

import androidx.appcompat.app.AppCompatActivity;

import com.example.udtddk.baitap.BaiTapActivity;
import com.example.udtddk.history.HistoryActivity;
import com.example.udtddk.profile.ProfileActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public abstract class BaseActivity extends AppCompatActivity {

    protected void setupBottomNav(int selectedItemId) {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        if (bottomNav == null) return;

        bottomNav.setSelectedItemId(selectedItemId);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == selectedItemId) return false; // đang ở trang này rồi

            String userId = getUserId();
            float bmi     = getCurrentBmi();
            Intent intent = null;

            if (id == R.id.nav_home) {
                intent = new Intent(this, MainActivity.class);

            } else if (id == R.id.nav_exercise) {
                intent = new Intent(this, BaiTapActivity.class);

            } else if (id == R.id.nav_history) {
                intent = new Intent(this, HistoryActivity.class);

            } else if (id == R.id.nav_profile) {
                intent = new Intent(this, ProfileActivity.class);
            }

            if (intent != null) {
                intent.putExtra("userId", userId);
                intent.putExtra("bmi", bmi);
                // Không tạo lại Activity nếu đã tồn tại
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                overridePendingTransition(0, 0); // không có hiệu ứng
            }

            return true;
        });
    }

    // Override ở từng Activity con
    protected String getUserId() { return ""; }
    protected float getCurrentBmi() { return 0f; }
}