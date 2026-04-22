package com.example.udtddk.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.udtddk.MainActivity;
import com.example.udtddk.R;

public class WelcomeActivity extends AppCompatActivity {

    Button btnStart;
    TextView tvSkip, tvLogin;  // ✅ thêm tvLogin riêng

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        btnStart = findViewById(R.id.btnGetStarted);
        tvSkip   = findViewById(R.id.tvSkip);

        // ✅ Nút "Get Started" → Đăng ký
        btnStart.setOnClickListener(v -> {
            startActivity(new Intent(WelcomeActivity.this, LoginActivity.class));
        });

        // ✅ Nút "Bỏ qua" → Đăng nhập (hoặc MainActivity nếu muốn)
        tvSkip.setOnClickListener(v -> {
            startActivity(new Intent(WelcomeActivity.this, LoginActivity.class));
        });
    }
}