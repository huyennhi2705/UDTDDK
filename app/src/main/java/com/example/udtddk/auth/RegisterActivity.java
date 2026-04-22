package com.example.udtddk.auth;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;

import com.example.udtddk.MainActivity;
import com.example.udtddk.R;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.Calendar;
import java.util.HashMap;

public class RegisterActivity extends AppCompatActivity {

    EditText etFullName, etEmail, etPassword, etConfirmPassword, etWeight, etHeight;
    TextView tvLogin, tvDateOfBirth;
    Button btnRegister;
    RadioGroup rgGender;
    ImageView btnTogglePassword, btnToggleConfirmPassword;
    CheckBox cbTerms;

    boolean isPasswordVisible        = false;
    boolean isConfirmPasswordVisible = false;

    DatabaseReference db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Firebase
        db = FirebaseDatabase
                .getInstance("https://udtddk-default-rtdb.firebaseio.com/")
                .getReference("NguoiDung");

        // Ánh xạ view
        etFullName            = findViewById(R.id.etFullName);
        etEmail               = findViewById(R.id.etEmail);
        etPassword            = findViewById(R.id.etPassword);
        etConfirmPassword     = findViewById(R.id.etConfirmPassword);
        etWeight              = findViewById(R.id.etWeight);
        etHeight              = findViewById(R.id.etHeight);
        tvLogin               = findViewById(R.id.tvLogin);
        tvDateOfBirth         = findViewById(R.id.tvDateOfBirth);
        btnRegister           = findViewById(R.id.btnRegister);
        rgGender              = findViewById(R.id.rgGender);
        btnTogglePassword     = findViewById(R.id.btnTogglePassword);
        btnToggleConfirmPassword = findViewById(R.id.btnToggleConfirmPassword);
        cbTerms               = findViewById(R.id.cbTerms);

        // Toggle hiện / ẩn mật khẩu
        btnTogglePassword.setOnClickListener(v -> {
            isPasswordVisible = !isPasswordVisible;
            etPassword.setInputType(isPasswordVisible
                    ? android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    : android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
            etPassword.setSelection(etPassword.getText().length());
        });

        btnToggleConfirmPassword.setOnClickListener(v -> {
            isConfirmPasswordVisible = !isConfirmPasswordVisible;
            etConfirmPassword.setInputType(isConfirmPasswordVisible
                    ? android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    : android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
            etConfirmPassword.setSelection(etConfirmPassword.getText().length());
        });

        // Chọn ngày sinh
        tvDateOfBirth.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            new DatePickerDialog(
                    RegisterActivity.this,
                    (view, y, m, d) -> {
                        String date = String.format("%02d/%02d/%04d", d, m + 1, y);
                        tvDateOfBirth.setText(date);
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
            ).show();
        });

        // Nút đăng ký
        btnRegister.setOnClickListener(v -> attemptRegister());

        // Chuyển sang đăng nhập
        tvLogin.setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });
    }

    // ----------------------------------------------------------------
    // XỬ LÝ ĐĂNG KÝ
    // ----------------------------------------------------------------
    private void attemptRegister() {
        String fullName    = etFullName.getText().toString().trim();
        String email       = etEmail.getText().toString().trim();
        String password    = etPassword.getText().toString().trim();
        String confirmPwd  = etConfirmPassword.getText().toString().trim();
        String dob         = tvDateOfBirth.getText().toString().trim();
        String weightStr   = etWeight.getText().toString().trim();
        String heightStr   = etHeight.getText().toString().trim();

        int selectedId = rgGender.getCheckedRadioButtonId();
        RadioButton rbSelected = (selectedId != -1) ? findViewById(selectedId) : null;
        String gender = (rbSelected != null) ? rbSelected.getText().toString() : "";

        // ===== VALIDATE =====
        if (TextUtils.isEmpty(fullName)) {
            etFullName.setError("Nhập họ và tên"); return;
        }
        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Email không hợp lệ"); return;
        }
        if (TextUtils.isEmpty(dob) || dob.equals("Chọn ngày sinh")) {
            Toast.makeText(this, "Chọn ngày sinh", Toast.LENGTH_SHORT).show(); return;
        }
        if (TextUtils.isEmpty(gender)) {
            Toast.makeText(this, "Chọn giới tính", Toast.LENGTH_SHORT).show(); return;
        }
        if (TextUtils.isEmpty(weightStr) || TextUtils.isEmpty(heightStr)) {
            Toast.makeText(this, "Nhập cân nặng và chiều cao", Toast.LENGTH_SHORT).show(); return;
        }
        if (TextUtils.isEmpty(password) || password.length() < 6) {
            etPassword.setError("Mật khẩu tối thiểu 6 ký tự"); return;
        }
        if (!password.equals(confirmPwd)) {
            etConfirmPassword.setError("Mật khẩu không khớp"); return;
        }
        if (!cbTerms.isChecked()) {
            Toast.makeText(this, "Chưa đồng ý điều khoản", Toast.LENGTH_SHORT).show(); return;
        }

        // Parse số
        double weight, height;
        try {
            weight = Double.parseDouble(weightStr);
            height = Double.parseDouble(heightStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Cân nặng / chiều cao không hợp lệ", Toast.LENGTH_SHORT).show();
            return;
        }

        // Vô hiệu nút tránh double click
        btnRegister.setEnabled(false);

        // ===== KIỂM TRA EMAIL ĐÃ TỒN TẠI CHƯA =====
        Query checkEmail = db.orderByChild("Email").equalTo(email);
        checkEmail.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists() && snapshot.getChildrenCount() > 0) {
                    btnRegister.setEnabled(true);
                    etEmail.setError("Email đã được đăng ký");
                    return;
                }
                // Email chưa tồn tại → tiến hành lưu
                saveUser(email, password, fullName, dob, gender, weight, height);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                btnRegister.setEnabled(true);
                Log.e("REGISTER", "Check email error: " + error.getMessage());
                Toast.makeText(RegisterActivity.this,
                        "Lỗi kết nối Firebase", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ----------------------------------------------------------------
    // LƯU USER LÊN FIREBASE – TOÀN BỘ FIELD CHỮ HOA (chuẩn hoá)
    // ----------------------------------------------------------------
    private void saveUser(String email, String password, String fullName,
                          String dob, String gender,
                          double weight, double height) {

        double bmi    = weight / Math.pow(height / 100.0, 2);
        String userId = db.push().getKey();

        HashMap<String, Object> userMap = new HashMap<>();
        userMap.put("NguoiDungId", userId);
        userMap.put("HoTen",       fullName);
        userMap.put("Email",       email);
        userMap.put("MatKhau",     password);
        userMap.put("GioiTinh",    gender);
        userMap.put("NgaySinh",    dob);
        userMap.put("CanNang",     weight);
        userMap.put("ChieuCao",    height);
        userMap.put("BMI",         bmi);
        userMap.put("NgayTao",     System.currentTimeMillis());

        db.child(userId).setValue(userMap).addOnCompleteListener(task -> {
            btnRegister.setEnabled(true);

            if (task.isSuccessful()) {
                Log.d("REGISTER", "Đăng ký thành công – userId: " + userId);
                Toast.makeText(this, "Đăng ký thành công 🎉", Toast.LENGTH_SHORT).show();

                // Lưu session
                getSharedPreferences("USER", MODE_PRIVATE)
                        .edit()
                        .putString("NguoiDungId", userId)
                        .putString("HoTen",       fullName)
                        .putString("Email",       email)
                        .apply();

                // Chuyển sang MainActivity, xoá backstack
                Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
                intent.putExtra("NguoiDungId", userId);
                intent.putExtra("HoTen",       fullName);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            } else {
                Log.e("REGISTER", "Firebase write failed: " +
                        (task.getException() != null ? task.getException().getMessage() : "unknown"));
                Toast.makeText(this, "Đăng ký thất bại, thử lại ❌", Toast.LENGTH_SHORT).show();
            }
        });
    }
}