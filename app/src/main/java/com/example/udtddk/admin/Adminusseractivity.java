package com.example.udtddk.admin;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.udtddk.R;
import com.example.udtddk.models.User;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class Adminusseractivity extends AppCompatActivity {

    private ImageView btnBack;
    private TextView tvUserCount;
    private EditText etSearch;
    private LinearLayout listContainer;
    private FloatingActionButton btnAddUser;
    private String selectedDob = "";

    private DatabaseReference usersRef;

    private final List<User> userList     = new ArrayList<>();
    private final List<User> filteredList = new ArrayList<>();
    private final HashMap<String, User> userKeyMap = new HashMap<>();

    private static final String DB_URL = "https://udtddk-default-rtdb.firebaseio.com/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_adminusseractivity);

        btnBack       = findViewById(R.id.btnBack);
        tvUserCount   = findViewById(R.id.tvUserCount);
        etSearch      = findViewById(R.id.etSearch);
        listContainer = findViewById(R.id.listContainer);
        btnAddUser    = findViewById(R.id.btnAddUser);

        usersRef = FirebaseDatabase.getInstance(DB_URL).getReference("NguoiDung");

        btnBack.setOnClickListener(v -> finish());
        loadUsers();

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
                filterUsers(s.toString().trim());
            }
        });

        btnAddUser.setOnClickListener(v -> showAddUserDialog());
    }

    // ─────────────────────────────────────────
    // LOAD
    // ─────────────────────────────────────────
    private void loadUsers() {
        usersRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                userList.clear();
                filteredList.clear();
                userKeyMap.clear();

                for (DataSnapshot ds : snapshot.getChildren()) {
                    // Bỏ qua node _schema
                    if ("_schema".equals(ds.getKey())) continue;
                    try {
                        User user = ds.getValue(User.class);
                        if (user != null) {
                            String key = ds.getKey();

                            // Đọc VaiTro – fallback "user" nếu chưa có
                            String vaiTro = ds.child("VaiTro").getValue(String.class);
                            if (vaiTro == null) vaiTro = ds.child("vaiTro").getValue(String.class);
                            if (vaiTro == null) vaiTro = "user";
                            user.setVaiTro(vaiTro);

                            userList.add(user);
                            userKeyMap.put(key, user);
                        }
                    } catch (Exception e) {
                        // bỏ qua node lỗi
                    }
                }

                filteredList.addAll(userList);
                displayUsers(filteredList);
                tvUserCount.setText("Tổng người dùng: " + userList.size());
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(Adminusseractivity.this,
                        "Lỗi tải dữ liệu: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ─────────────────────────────────────────
    // HIỂN THỊ
    // ─────────────────────────────────────────
    private void displayUsers(List<User> users) {
        listContainer.removeAllViews();

        if (users.isEmpty()) {
            TextView emptyText = new TextView(this);
            emptyText.setText("Không tìm thấy người dùng nào.");
            emptyText.setTextSize(16f);
            emptyText.setPadding(20, 40, 20, 40);
            listContainer.addView(emptyText);
            return;
        }

        for (User user : users) {
            View itemView = LayoutInflater.from(this)
                    .inflate(R.layout.item_admin_user, listContainer, false);

            ((TextView) itemView.findViewById(R.id.tvName))
                    .setText(safe(user.getHoTen()));

            ((TextView) itemView.findViewById(R.id.tvEmail))
                    .setText("Email: " + safe(user.getEmail()));

            ((TextView) itemView.findViewById(R.id.tvGender))
                    .setText("Giới tính: " + safe(user.getGioiTinh()));

            ((TextView) itemView.findViewById(R.id.tvDob))
                    .setText("Ngày sinh: " + safe(user.getNgaySinh()));

            ((TextView) itemView.findViewById(R.id.tvHeight))
                    .setText("Chiều cao: " + user.getChieuCao() + " cm");

            ((TextView) itemView.findViewById(R.id.tvWeight))
                    .setText("Cân nặng: " + user.getCanNang() + " kg");

            double bmi = calculateBMI(user.getChieuCao(), user.getCanNang());
            ((TextView) itemView.findViewById(R.id.tvBmi))
                    .setText("BMI: " + String.format(Locale.getDefault(), "%.1f", bmi)
                            + " (" + getBmiCategory(bmi) + ")");

            // ── Badge VaiTro ──────────────────────────
            TextView tvVaiTro = itemView.findViewById(R.id.tvVaiTro);
            String vaiTro = safe(user.getVaiTro()).isEmpty() ? "user" : user.getVaiTro();
            tvVaiTro.setText(vaiTro.toUpperCase(Locale.ROOT));

            if ("admin".equalsIgnoreCase(vaiTro)) {
                // Màu đỏ cam cho admin
                tvVaiTro.setBackgroundResource(R.drawable.bg_badge_admin);
            } else {
                // Màu xanh lá cho user
                tvVaiTro.setBackgroundResource(R.drawable.bg_badge_user);
            }
            // ─────────────────────────────────────────

            itemView.findViewById(R.id.btnEdit).setOnClickListener(v -> showEditUserDialog(user));
            itemView.findViewById(R.id.btnDelete).setOnClickListener(v -> deleteUser(user));

            listContainer.addView(itemView);
        }
    }


    private String findKey(User user) {
        for (HashMap.Entry<String, User> entry : userKeyMap.entrySet()) {
            if (entry.getValue() == user) return entry.getKey();
        }
        return null;
    }


    private void filterUsers(String keyword) {
        filteredList.clear();
        String lk = keyword.toLowerCase(Locale.ROOT);
        for (User u : userList) {
            if (safe(u.getHoTen()).toLowerCase(Locale.ROOT).contains(lk)
                    || safe(u.getEmail()).toLowerCase(Locale.ROOT).contains(lk)) {
                filteredList.add(u);
            }
        }
        displayUsers(filteredList);
    }


    private void showAddUserDialog() {
        View dv = getLayoutInflater().inflate(R.layout.dialog_add_edit_user, null);
        setupDobPicker(dv);
        setupPasswordToggle(dv);
        selectedDob = "";
        ((RadioGroup) dv.findViewById(R.id.rgVaiTro)).check(R.id.rbUser);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(" Thêm người dùng")
                .setView(dv)
                .setPositiveButton("Thêm", null)
                .setNegativeButton("Hủy", null)
                .create();
        dialog.setOnShowListener(d -> {
            Button btnPos = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            btnPos.setOnClickListener(v -> validateAndSave(dv, null, dialog));
        });

        dialog.show();
    }
    // SỬA
    private void showEditUserDialog(User user) {
        String userKey = findKey(user);
        if (userKey == null) {
            Toast.makeText(this, "Không tìm thấy khóa người dùng", Toast.LENGTH_SHORT).show();
            return;
        }

        View dv = LayoutInflater.from(this).inflate(R.layout.dialog_add_edit_user, null);
        setupDobPicker(dv);
        setupPasswordToggle(dv);

        // Điền dữ liệu cũ
        ((EditText) dv.findViewById(R.id.etName)).setText(safe(user.getHoTen()));
        ((EditText) dv.findViewById(R.id.etEmail)).setText(safe(user.getEmail()));
        ((EditText) dv.findViewById(R.id.etHeight)).setText(String.valueOf(user.getChieuCao()));
        ((EditText) dv.findViewById(R.id.etWeight)).setText(String.valueOf(user.getCanNang()));
        ((EditText) dv.findViewById(R.id.etPassword)).setText(safe(user.getMatKhau()));

        // Giới tính
        RadioGroup rgGender = dv.findViewById(R.id.rgGender);
        if ("Nữ".equals(user.getGioiTinh())) {
            rgGender.check(R.id.rbFemale);
        } else {
            rgGender.check(R.id.rbMale);
        }

        // Vai trò
        RadioGroup rgVaiTro = dv.findViewById(R.id.rgVaiTro);
        if ("admin".equalsIgnoreCase(user.getVaiTro())) {
            rgVaiTro.check(R.id.rbAdmin);
        } else {
            rgVaiTro.check(R.id.rbUser);
        }

        // Ngày sinh
        selectedDob = safe(user.getNgaySinh());
        if (!selectedDob.isEmpty()) {
            TextView tvDob = dv.findViewById(R.id.tvDob);
            tvDob.setText(selectedDob);
            tvDob.setTextColor(0xFF0F2D1F);
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("✏️ Sửa người dùng")
                .setView(dv)
                .setPositiveButton("Lưu", null)
                .setNegativeButton("Hủy", null)
                .create();

        dialog.setOnShowListener(d -> {
            Button btnPos = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            btnPos.setOnClickListener(v -> validateAndSave(dv, user, dialog));
        });

        dialog.show();
    }

    // ─────────────────────────────────────────
    // VALIDATE & LƯU
    // ─────────────────────────────────────────
    private boolean validateAndSave(View dv, User existingUser, AlertDialog dialog) {
        String name     = ((EditText) dv.findViewById(R.id.etName)).getText().toString().trim();
        String email    = ((EditText) dv.findViewById(R.id.etEmail)).getText().toString().trim();
        String password = ((EditText) dv.findViewById(R.id.etPassword)).getText().toString().trim();
        double height   = parseDouble(((EditText) dv.findViewById(R.id.etHeight)).getText().toString().trim());
        double weight   = parseDouble(((EditText) dv.findViewById(R.id.etWeight)).getText().toString().trim());

        RadioGroup rgGender = dv.findViewById(R.id.rgGender);
        String gender = (rgGender.getCheckedRadioButtonId() == R.id.rbFemale) ? "Nữ" : "Nam";

        // Đọc VaiTro từ RadioGroup
        RadioGroup rgVaiTro = dv.findViewById(R.id.rgVaiTro);
        String vaiTro = (rgVaiTro.getCheckedRadioButtonId() == R.id.rbAdmin) ? "admin" : "user";

        if (name.isEmpty()) {
            showError(dv, R.id.etName, "Họ tên không được để trống");
            return false;
        }
        if (name.length() < 2) {
            showError(dv, R.id.etName, "Họ tên phải có ít nhất 2 ký tự");
            return false;
        }
        if (email.isEmpty()) {
            showError(dv, R.id.etEmail, "Email không được để trống");
            return false;
        }
        if (!isValidEmail(email)) {
            showError(dv, R.id.etEmail, "Email không đúng định dạng");
            return false;
        }
        if (selectedDob.isEmpty()) {
            Toast.makeText(this, "Vui lòng chọn ngày sinh", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (password.isEmpty()) {
            showError(dv, R.id.etPassword, "Mật khẩu không được để trống");
            return false;
        }
        if (password.length() < 6) {
            showError(dv, R.id.etPassword, "Mật khẩu phải ít nhất 6 ký tự");
            return false;
        }
        if (height <= 0 || height > 250) {
            showError(dv, R.id.etHeight, "Chiều cao không hợp lệ (1–250 cm)");
            return false;
        }
        if (weight <= 0 || weight > 500) {
            showError(dv, R.id.etWeight, "Cân nặng không hợp lệ (1–500 kg)");
            return false;
        }
        // ────────────────────────────────────────

        if (existingUser == null) {

            String userId = usersRef.push().getKey();
            if (userId == null) return false;

            User newUser = new User(name, email, password, gender, selectedDob, weight, height, vaiTro);
            newUser.setVaiTro(vaiTro);

            usersRef.child(userId).setValue(newUser)
                    .addOnSuccessListener(u -> {
                        Toast.makeText(this, " Thêm thành công", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this, " Thêm thất bại: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show());
        }

        else {

            String userKey = findKey(existingUser);
            if (userKey == null) return false;

            existingUser.setHoTen(name);
            existingUser.setEmail(email);
            existingUser.setMatKhau(password);
            existingUser.setGioiTinh(gender);
            existingUser.setNgaySinh(selectedDob);
            existingUser.setCanNang(weight);
            existingUser.setChieuCao(height);
            existingUser.setVaiTro(vaiTro);

            usersRef.child(userKey).setValue(existingUser)
                    .addOnSuccessListener(u -> {
                        Toast.makeText(this, "Cập nhật thành công", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this, " Cập nhật thất bại", Toast.LENGTH_SHORT).show());
        }
        return true;
    }


    private void setupDobPicker(View dv) {
        FrameLayout layoutDob = dv.findViewById(R.id.layoutDob);
        TextView tvDob        = dv.findViewById(R.id.tvDob);

        layoutDob.setOnClickListener(v -> {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.set(2000, 0, 1);

            new android.app.DatePickerDialog(
                    this,
                    R.style.DatePickerTheme,
                    (picker, year, month, day) -> {
                        selectedDob = String.format(Locale.getDefault(),
                                "%02d/%02d/%04d", day, month + 1, year);
                        tvDob.setText(selectedDob);
                        tvDob.setTextColor(0xFF0F2D1F);
                    },
                    cal.get(java.util.Calendar.YEAR),
                    cal.get(java.util.Calendar.MONTH),
                    cal.get(java.util.Calendar.DAY_OF_MONTH)
            ).show();
        });
    }

    // ─────────────────────────────────────────
    // PASSWORD TOGGLE
    // ─────────────────────────────────────────
    private void setupPasswordToggle(View dv) {
        ImageView ivToggle = dv.findViewById(R.id.ivTogglePassword);
        EditText  etPass   = dv.findViewById(R.id.etPassword);
        final boolean[] show = {false};

        ivToggle.setOnClickListener(v -> {
            show[0] = !show[0];
            if (show[0]) {
                etPass.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                ivToggle.setAlpha(1f);
            } else {
                etPass.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                        | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
                ivToggle.setAlpha(0.5f);
            }
            etPass.setSelection(etPass.getText().length());
        });
    }

    // ─────────────────────────────────────────
    // XÓA
    // ─────────────────────────────────────────
    private void deleteUser(User user) {
        String userKey = findKey(user);
        if (userKey == null) {
            Toast.makeText(this, "Không tìm thấy khóa người dùng", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Xóa người dùng")
                .setMessage("Bạn có chắc muốn xóa \"" + safe(user.getHoTen()) + "\" không?")
                .setPositiveButton("Xóa", (dialog, which) ->
                        usersRef.child(userKey).removeValue()
                                .addOnSuccessListener(u ->
                                        Toast.makeText(this, "Đã xóa người dùng", Toast.LENGTH_SHORT).show())
                                .addOnFailureListener(e ->
                                        Toast.makeText(this, "Xóa thất bại", Toast.LENGTH_SHORT).show()))
                .setNegativeButton("Hủy", null)
                .show();
    }

    // ─────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────
    private boolean isValidEmail(String email) {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private void showError(View dv, int fieldId, String message) {
        EditText field = dv.findViewById(fieldId);
        field.setError(message);
        field.requestFocus();
    }

    private String getBmiCategory(double bmi) {
        if (bmi == 0)   return "Chưa có dữ liệu";
        if (bmi < 18.5) return "Thiếu cân";
        if (bmi < 25)   return "Bình thường";
        if (bmi < 30)   return "Thừa cân";
        return "Béo phì";
    }

    private String safe(String v) { return v == null ? "" : v; }

    private double parseDouble(String v) {
        try { return Double.parseDouble(v); } catch (Exception e) { return 0; }
    }

    private double calculateBMI(double h, double w) {
        if (h <= 0 || w <= 0) return 0;
        double hm = h / 100.0;
        return Math.round((w / (hm * hm)) * 100.0) / 100.0;
    }
}