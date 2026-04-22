package com.example.udtddk.auth;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;

import com.example.udtddk.admin.Admindashboardactivity;
import com.example.udtddk.MainActivity;
import com.example.udtddk.R;
import com.google.firebase.database.*;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private Button btnLogin;
    private TextView tvRegister, tvForgotPassword;
    private ImageView btnTogglePassword;

    private DatabaseReference db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Firebase – trỏ đúng node NguoiDung
        db = FirebaseDatabase
                .getInstance("https://udtddk-default-rtdb.firebaseio.com/")
                .getReference("NguoiDung");

        // Ánh xạ view
        etEmail           = findViewById(R.id.etEmail);
        etPassword        = findViewById(R.id.etPassword);
        btnLogin          = findViewById(R.id.button2);
        tvRegister        = findViewById(R.id.textView6);
        tvForgotPassword  = findViewById(R.id.tvForgotPassword);
        btnTogglePassword = findViewById(R.id.btnTogglePassword);

        // Toggle hiện / ẩn mật khẩu
        btnTogglePassword.setOnClickListener(v -> {
            int visibleType  = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
            int passwordType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;

            boolean isHidden = etPassword.getInputType() == passwordType;
            etPassword.setInputType(isHidden ? visibleType : passwordType);
            btnTogglePassword.setImageResource(R.drawable.ic_eye_off);
            etPassword.setSelection(etPassword.getText().length());
        });

        // Chuyển sang màn đăng ký
        tvRegister.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class))
        );

        // Chuyển sang quên mật khẩu
        tvForgotPassword.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, ForgotPasswordActivity.class))
        );

        btnLogin.setOnClickListener(v -> login());
    }

    // ----------------------------------------------------------------
    // XỬ LÝ ĐĂNG NHẬP
    // ----------------------------------------------------------------
    private void login() {
        String email    = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        // Validate đầu vào
        if (TextUtils.isEmpty(email)) {
            etEmail.setError("Nhập email");
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Email không hợp lệ");
            return;
        }
        if (TextUtils.isEmpty(password)) {
            etPassword.setError("Nhập mật khẩu");
            return;
        }
        if (password.length() < 6) {
            etPassword.setError("Tối thiểu 6 ký tự");
            return;
        }

        btnLogin.setEnabled(false);

        // Query Firebase theo field "Email"
        Query query = db.orderByChild("Email").equalTo(email);
        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                btnLogin.setEnabled(true);

                if (!snapshot.exists() || snapshot.getChildrenCount() == 0) {
                    Toast.makeText(LoginActivity.this,
                            "Email không tồn tại", Toast.LENGTH_SHORT).show();
                    return;
                }

                for (DataSnapshot data : snapshot.getChildren()) {
                    // Đọc mật khẩu – ưu tiên "MatKhau" (chuẩn), fallback "matKhau" (dữ liệu cũ)
                    String dbPassword = data.child("MatKhau").getValue(String.class);
                    if (dbPassword == null) {
                        dbPassword = data.child("matKhau").getValue(String.class);
                    }

                    // Đọc họ tên – tương tự
                    String hoTen = data.child("HoTen").getValue(String.class);
                    if (hoTen == null) {
                        hoTen = data.child("hoTen").getValue(String.class);
                    }

                    // Đọc phân quyền – mặc định "user" nếu chưa có
                    String vaiTro = data.child("VaiTro").getValue(String.class);
                    if (vaiTro == null) {
                        vaiTro = data.child("vaiTro").getValue(String.class);
                    }
                    if (vaiTro == null) {
                        vaiTro = "user";
                    }

                    String userId = data.getKey();

                    if (dbPassword != null && dbPassword.equals(password)) {
                        // Lưu session (bao gồm VaiTro)
                        SharedPreferences pref =
                                getSharedPreferences("USER", MODE_PRIVATE);
                        pref.edit()
                                .putString("NguoiDungId", userId)
                                .putString("HoTen",       hoTen != null ? hoTen : "")
                                .putString("Email",       email)
                                .putString("VaiTro",      vaiTro)
                                .apply();

                        Log.d("LOGIN", "Đăng nhập thành công – userId: " + userId + " | VaiTro: " + vaiTro);
                        Toast.makeText(LoginActivity.this,
                                "Đăng nhập thành công 🎉", Toast.LENGTH_SHORT).show();

                        // Điều hướng theo phân quyền
                        Intent intent;
                        if ("admin".equalsIgnoreCase(vaiTro)) {
                            intent = new Intent(LoginActivity.this, Admindashboardactivity.class);
                        } else {
                            intent = new Intent(LoginActivity.this, MainActivity.class);
                        }
                        intent.putExtra("NguoiDungId", userId);
                        intent.putExtra("VaiTro", vaiTro);
                        startActivity(intent);
                        finish();
                        return;
                    }
                }

                // Tìm thấy email nhưng sai mật khẩu
                Toast.makeText(LoginActivity.this,
                        "Sai mật khẩu", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onCancelled(DatabaseError error) {
                btnLogin.setEnabled(true);
                Log.e("LOGIN", "Firebase error: " + error.getMessage());
                Toast.makeText(LoginActivity.this,
                        "Lỗi kết nối Firebase", Toast.LENGTH_SHORT).show();
            }
        });
    }
}