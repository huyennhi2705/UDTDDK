package com.example.udtddk.profile;

import android.app.DatePickerDialog;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Patterns;

import com.example.udtddk.BaseActivity;
import com.example.udtddk.R;
import com.example.udtddk.auth.LoginActivity;
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
import java.util.Map;
import java.util.TreeMap;

public class ProfileActivity extends BaseActivity {

    private TextView tvAvatarLarge, tvProfileName, tvProfileEmail, tvProfileBmi;
    private TextView tvStatWeight, tvStatHeight, tvStatAge;
    private TextView tvBmiValue, tvBmiStatus, tvNotifSummary;
    private TextView tvProfileWater, tvProfileSleep, tvProfileHealthStatus;

    private DatabaseReference db;
    private String userId;
    private float  currentBmi = 0f;

    private String cachedWeight   = "--";
    private String cachedHeight   = "--";
    private String cachedDob      = null;
    private String cachedPassword = "";

    private static final String PREFS_NOTIF = "notif_prefs";

    private static final int COLOR_GREEN_DARK   = 0xFF15803D;
    private static final int COLOR_GREEN_MID    = 0xFF166534;
    private static final int COLOR_GREEN_LIGHT  = 0xFFDCFCE7;
    private static final int COLOR_GREEN_ACCENT = 0xFF22C55E;
    private static final int COLOR_DIVIDER      = 0xFFBBF7D0;
    private static final int COLOR_TEXT_MUTED   = 0xFF6B7280;
    private static final int COLOR_WHITE        = 0xFFFFFFFF;

    // ════════════════════════════════════════════════════════════════════════════
    //  Lifecycle
    // ════════════════════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        userId     = getIntent().getStringExtra("userId");
        currentBmi = getIntent().getFloatExtra("bmi", 0f);

        bindViews();
        setupBottomNav(R.id.nav_profile);
        attachListeners();

        if (userId != null) {
            loadProfile();
            loadHealthMetrics();
        }
        if (currentBmi > 0) renderBmi(currentBmi);
        updateNotifSummary();
    }

    @Override protected String getUserId()     { return userId;     }
    @Override protected float  getCurrentBmi() { return currentBmi; }

    // ════════════════════════════════════════════════════════════════════════════
    //  View binding
    // ════════════════════════════════════════════════════════════════════════════

    private void bindViews() {
        tvAvatarLarge         = findViewById(R.id.tvAvatarLarge);
        tvProfileName         = findViewById(R.id.tvProfileName);
        tvProfileEmail        = findViewById(R.id.tvProfileEmail);
        tvProfileBmi          = findViewById(R.id.tvProfileBmi);
        tvStatWeight          = findViewById(R.id.tvStatWeight);
        tvStatHeight          = findViewById(R.id.tvStatHeight);
        tvStatAge             = findViewById(R.id.tvStatAge);
        tvBmiValue            = findViewById(R.id.tvBmiValue);
        tvBmiStatus           = findViewById(R.id.tvBmiStatus);
        tvNotifSummary        = findViewById(R.id.tvNotifSummary);
        tvProfileWater        = findViewById(R.id.tvProfileWater);
        tvProfileSleep        = findViewById(R.id.tvProfileSleep);
        tvProfileHealthStatus = findViewById(R.id.tvProfileHealthStatus);
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  Listeners
    // ════════════════════════════════════════════════════════════════════════════

    private void attachListeners() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        findViewById(R.id.statWeight).setOnClickListener(v ->
                showHealthEditDialog("⚖️  Cập nhật cân nặng", "kg",
                        cachedWeight, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL,
                        val -> updateField("CanNang", val, "Đã cập nhật cân nặng!")));

        findViewById(R.id.statHeight).setOnClickListener(v ->
                showHealthEditDialog("📏  Cập nhật chiều cao", "cm",
                        cachedHeight, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL,
                        val -> updateField("ChieuCao", val, "Đã cập nhật chiều cao!")));

        findViewById(R.id.btnViewHealthReport).setOnClickListener(v -> showHealthReport());
        findViewById(R.id.btnChangePassword).setOnClickListener(v -> showPasswordDialog());
        findViewById(R.id.btnNotificationSettings).setOnClickListener(v -> showNotificationSettingsDialog());
        findViewById(R.id.btnAbout).setOnClickListener(v -> showAboutDialog());

        findViewById(R.id.btnLogout).setOnClickListener(v ->
                showStyledConfirmDialog("🚪", "Đăng xuất", "Bạn có chắc muốn đăng xuất không?",
                        "Đăng xuất", "#DC2626", () -> {
                            Intent intent = new Intent(this, LoginActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                        }));
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  Firebase – load profile
    // ════════════════════════════════════════════════════════════════════════════

    // ── Đọc field hỗ trợ cả PascalCase lẫn camelCase ──────────────────────────
    private String snapStr(DataSnapshot snap, String... keys) {
        for (String k : keys) {
            Object v = snap.child(k).getValue();
            if (v != null) return v.toString();
        }
        return null;
    }

    private void loadProfile() {
        db = FirebaseDatabase.getInstance("https://udtddk-default-rtdb.firebaseio.com/")
                .getReference("NguoiDung");

        db.child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snap) {
                if (!snap.exists()) return;

                String fullName = snapStr(snap, "HoTen",    "hoTen");
                String email    = snapStr(snap, "Email",    "email");
                String dob      = snapStr(snap, "NgaySinh", "ngaySinh");
                String gender   = snapStr(snap, "GioiTinh", "gioiTinh");
                String password = snapStr(snap, "MatKhau",  "matKhau");

                Object wObj = snap.child("CanNang").getValue();
                if (wObj == null) wObj = snap.child("canNang").getValue();
                Object hObj = snap.child("ChieuCao").getValue();
                if (hObj == null) hObj = snap.child("chieuCao").getValue();

                String weight = wObj != null ? wObj.toString() : "--";
                String height = hObj != null ? hObj.toString() : "--";

                cachedWeight   = weight;
                cachedHeight   = height;
                cachedDob      = dob;
                cachedPassword = password != null ? password : "";

                String name = fullName != null ? fullName : (email != null ? email : "Người dùng");
                tvProfileName.setText(name);
                tvProfileEmail.setText(email != null ? email : "--");

                String initial = name.length() > 0
                        ? String.valueOf(name.charAt(0)).toUpperCase(Locale.getDefault()) : "A";
                tvAvatarLarge.setText(initial);

                tvStatWeight.setText(weight);
                tvStatHeight.setText(height);

                int age = calculateAge(dob);
                tvStatAge.setText(age > 0 ? String.valueOf(age) : "--");

                if (currentBmi == 0f && wObj != null && hObj != null) {
                    try {
                        float w  = Float.parseFloat(weight.replace(',', '.'));
                        float hm = Float.parseFloat(height.replace(',', '.')) / 100f;
                        currentBmi = w / (hm * hm);
                    } catch (Exception ignored) {}
                }
                if (currentBmi > 0) renderBmi(currentBmi);

                setupRow(R.id.rowFullName, "👤", "Họ và tên", name,
                        val -> updateField("HoTen", val, "Đã cập nhật họ tên!"));
                setupRow(R.id.rowEmail, "📧", "Email", email != null ? email : "--",
                        val -> updateEmail(val));
                setupDateRow(R.id.rowDob, "🎂", "Ngày sinh", dob != null ? dob : "Chưa cập nhật");
                setupRowWithCustomAction(R.id.rowGender, "⚧", "Giới tính",
                        gender != null ? gender : "Chưa cập nhật",
                        () -> showGenderPickerDialog(gender != null ? gender : ""));
                setupRow(R.id.rowWeight, "⚖️", "Cân nặng (kg)", weight,
                        val -> updateField("CanNang", val, "Đã cập nhật cân nặng!"));
                setupRow(R.id.rowHeight, "📏", "Chiều cao (cm)", height,
                        val -> updateField("ChieuCao", val, "Đã cập nhật chiều cao!"));
            }

            @Override public void onCancelled(DatabaseError e) {
                Toast.makeText(ProfileActivity.this, "Lỗi tải dữ liệu", Toast.LENGTH_SHORT).show();
            }
        });
    }


    private void loadHealthMetrics() {
        if (userId == null || userId.isEmpty()) return;
        DatabaseReference ref = FirebaseDatabase.getInstance("https://udtddk-default-rtdb.firebaseio.com/")
                .getReference("NguoiDung").child(userId);
        ref.child("ChiSoSucKhoe").orderByKey().limitToLast(5)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snap) {
                        if (!snap.exists()) {

                            ref.child("chiSoSucKhoe").orderByKey().limitToLast(5)
                                    .addListenerForSingleValueEvent(new ValueEventListener() {
                                        @Override
                                        public void onDataChange(DataSnapshot snap2) {
                                            parseHealthSnapshot(snap2);
                                        }
                                        @Override public void onCancelled(DatabaseError e) {}
                                    });
                            return;
                        }
                        parseHealthSnapshot(snap);
                    }
                    @Override
                    public void onCancelled(DatabaseError error) {
                        Toast.makeText(ProfileActivity.this,
                                "Lỗi tải sức khoẻ: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void parseHealthSnapshot(DataSnapshot snap) {
        if (snap == null || !snap.exists()) {
            if (tvProfileWater != null) tvProfileWater.setText("0");
            if (tvProfileSleep != null) tvProfileSleep.setText("0.0");
            return;
        }

        DataSnapshot latest = null;
        for (DataSnapshot child : snap.getChildren()) latest = child;
        if (latest == null) return;

        try {
            // Hỗ trợ cả "LuongNuoc" lẫn "luongNuoc"
            Object waterObj = latest.child("LuongNuoc").getValue();
            if (waterObj == null) waterObj = latest.child("luongNuoc").getValue();
            if (tvProfileWater != null) {
                tvProfileWater.setText(waterObj != null
                        ? String.valueOf(Math.round(Float.parseFloat(waterObj.toString()))) : "0");
            }

            // Hỗ trợ cả "ThoiGianNgu" lẫn "thoiGianNgu"
            Object sleepObj = latest.child("ThoiGianNgu").getValue();
            if (sleepObj == null) sleepObj = latest.child("thoiGianNgu").getValue();
            if (tvProfileSleep != null) {
                tvProfileSleep.setText(sleepObj != null
                        ? String.format(Locale.getDefault(), "%.1f",
                        Float.parseFloat(sleepObj.toString()))
                        : "0.0");
            }

            // Hỗ trợ cả "BMI" lẫn "bmi"
            Object bmiObj = latest.child("BMI").getValue();
            if (bmiObj == null) bmiObj = latest.child("bmi").getValue();
            if (bmiObj != null) {
                float bmiVal = Float.parseFloat(bmiObj.toString());
                if (currentBmi == 0f) {
                    currentBmi = bmiVal;
                    renderBmi(currentBmi);
                }
                if (tvProfileHealthStatus != null)
                    tvProfileHealthStatus.setText(classifyBmi(bmiVal));
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (tvProfileWater != null) tvProfileWater.setText("0");
            if (tvProfileSleep != null) tvProfileSleep.setText("0.0");
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  Row builders
    // ════════════════════════════════════════════════════════════════════════════

    private void setupRow(int rowId, String icon, String label,
                          String value, OnSaveListener listener) {
        View row = findViewById(rowId);
        if (row == null) return;
        ((TextView) row.findViewById(R.id.tvFieldIcon )).setText(icon);
        ((TextView) row.findViewById(R.id.tvFieldLabel)).setText(label);
        ((TextView) row.findViewById(R.id.tvFieldValue)).setText(value);
        TextView btnEdit = row.findViewById(R.id.tvFieldEdit);
        if (listener == null) {
            btnEdit.setVisibility(View.GONE);
        } else {
            btnEdit.setVisibility(View.VISIBLE);
            btnEdit.setOnClickListener(v ->
                    showStyledEditDialog(label, icon, value, InputType.TYPE_CLASS_TEXT, listener));
        }
    }

    private void setupRowWithCustomAction(int rowId, String icon, String label,
                                          String value, Runnable action) {
        View row = findViewById(rowId);
        if (row == null) return;
        ((TextView) row.findViewById(R.id.tvFieldIcon )).setText(icon);
        ((TextView) row.findViewById(R.id.tvFieldLabel)).setText(label);
        ((TextView) row.findViewById(R.id.tvFieldValue)).setText(value);
        TextView btnEdit = row.findViewById(R.id.tvFieldEdit);
        btnEdit.setVisibility(View.VISIBLE);
        btnEdit.setOnClickListener(v -> action.run());
    }

    private void setupDateRow(int rowId, String icon, String label, String value) {
        View row = findViewById(rowId);
        if (row == null) return;
        ((TextView) row.findViewById(R.id.tvFieldIcon )).setText(icon);
        ((TextView) row.findViewById(R.id.tvFieldLabel)).setText(label);
        ((TextView) row.findViewById(R.id.tvFieldValue)).setText(value);
        TextView btnEdit = row.findViewById(R.id.tvFieldEdit);
        btnEdit.setVisibility(View.VISIBLE);
        btnEdit.setOnClickListener(v -> showDatePickerDialog());
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  Dialogs
    // ════════════════════════════════════════════════════════════════════════════

    private void showDatePickerDialog() {
        Calendar calendar = Calendar.getInstance();
        if (cachedDob != null && !cachedDob.trim().isEmpty()) {
            for (String fmt : new String[]{"dd/MM/yyyy", "yyyy-MM-dd", "dd-MM-yyyy"}) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat(fmt, Locale.getDefault());
                    sdf.setLenient(false);
                    Date date = sdf.parse(cachedDob.trim());
                    if (date != null) { calendar.setTime(date); break; }
                } catch (Exception ignored) {}
            }
        }
        DatePickerDialog dialog = new DatePickerDialog(this,
                R.style.GreenDatePickerDialog,
                (view, y, m, d) -> {
                    String selectedDate = String.format(Locale.getDefault(), "%02d/%02d/%04d", d, m + 1, y);
                    cachedDob = selectedDate;
                    updateField("NgaySinh", selectedDate, "Đã cập nhật ngày sinh!");
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        dialog.show();
    }

    private void showGenderPickerDialog(String current) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, 8, 0, 8);
        root.addView(makeDialogHeader("⚧", "Chọn giới tính"));

        String[] options  = {"Nam", "Nữ", "Khác"};
        String[] emojis   = {"👨", "👩", "🧑"};
        int[] optColors   = {0xFFDBEAFE, 0xFFFFE4E6, 0xFFDCFCE7};
        int[] textColors  = {0xFF1D4ED8, 0xFFBE185D, 0xFF15803D};
        final String[] selected = {current};

        LinearLayout optContainer = new LinearLayout(this);
        optContainer.setOrientation(LinearLayout.VERTICAL);
        optContainer.setPadding(dp(24), dp(12), dp(24), dp(8));

        LinearLayout[] optRows    = new LinearLayout[options.length];
        TextView[]     labelViews = new TextView[options.length];
        RadioButton[]  radioViews = new RadioButton[options.length];

        for (int i = 0; i < options.length; i++) {
            final int idx = i;
            LinearLayout optRow = new LinearLayout(this);
            optRow.setOrientation(LinearLayout.HORIZONTAL);
            optRow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(56));
            lp.setMargins(0, dp(4), 0, dp(4));
            optRow.setLayoutParams(lp);
            optRow.setPadding(dp(12), 0, dp(16), 0);
            optRow.setClickable(true);
            optRow.setFocusable(true);

            boolean isSelected = options[i].equals(current);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(isSelected ? optColors[i] : 0xFFF9FAFB);
            bg.setCornerRadius(dp(12));
            bg.setStroke(dp(1), isSelected ? textColors[i] : COLOR_DIVIDER);
            optRow.setBackground(bg);

            TextView emojiTv = new TextView(this);
            emojiTv.setText(emojis[i]);
            emojiTv.setTextSize(22f);
            emojiTv.setPadding(0, 0, dp(12), 0);

            TextView labelTv = new TextView(this);
            labelTv.setText(options[i]);
            labelTv.setTextSize(15f);
            labelTv.setTextColor(isSelected ? textColors[i] : 0xFF374151);
            labelTv.setTypeface(null, isSelected ? Typeface.BOLD : Typeface.NORMAL);
            labelTv.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            RadioButton rb = new RadioButton(this);
            rb.setChecked(isSelected);
            rb.setClickable(false);
            rb.setFocusable(false);
            rb.setButtonTintList(android.content.res.ColorStateList.valueOf(textColors[idx]));

            optRow.addView(emojiTv);
            optRow.addView(labelTv);
            optRow.addView(rb);
            optRows[i]    = optRow;
            labelViews[i] = labelTv;
            radioViews[i] = rb;

            optRow.setOnClickListener(v -> {
                selected[0] = options[idx];
                for (int j = 0; j < options.length; j++) {
                    boolean sel = (j == idx);
                    GradientDrawable rowBg = new GradientDrawable();
                    rowBg.setColor(sel ? optColors[j] : 0xFFF9FAFB);
                    rowBg.setCornerRadius(dp(12));
                    rowBg.setStroke(dp(1), sel ? textColors[j] : COLOR_DIVIDER);
                    optRows[j].setBackground(rowBg);
                    labelViews[j].setTextColor(sel ? textColors[j] : 0xFF374151);
                    labelViews[j].setTypeface(null, sel ? Typeface.BOLD : Typeface.NORMAL);
                    radioViews[j].setChecked(sel);
                }
            });
            optContainer.addView(optRow);
        }
        root.addView(optContainer);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(dp(24), dp(12), dp(24), dp(8));
        btnRow.setGravity(Gravity.END);
        TextView btnCancel = makeDialogBtn("Huỷ", 0xFF6B7280, 0xFFF3F4F6);
        TextView btnSave   = makeDialogBtn("Lưu lại", COLOR_WHITE, COLOR_GREEN_DARK);
        btnRow.addView(btnCancel);
        btnRow.addView(makeHSpacer(dp(8)));
        btnRow.addView(btnSave);
        root.addView(btnRow);

        AlertDialog dialog = buildStyledDialog(root);
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            updateField("GioiTinh", selected[0], "Đã cập nhật giới tính!");
            dialog.dismiss();
        });
        dialog.show();
    }

    private void showStyledEditDialog(String label, String icon, String currentVal,
                                      int inputType, OnSaveListener listener) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, 0, 0, dp(8));
        root.addView(makeDialogHeader(icon, "Cập nhật " + label));

        LinearLayout inputWrap = new LinearLayout(this);
        inputWrap.setOrientation(LinearLayout.VERTICAL);
        inputWrap.setPadding(dp(24), dp(8), dp(24), dp(8));

        EditText et = new EditText(this);
        et.setText(currentVal);
        et.setInputType(inputType);
        et.setTextSize(15f);
        et.setTextColor(0xFF111827);
        et.setPadding(dp(14), dp(14), dp(14), dp(14));
        et.setSelectAllOnFocus(true);
        GradientDrawable etBg = new GradientDrawable();
        etBg.setColor(0xFFF0FDF4);
        etBg.setCornerRadius(dp(12));
        etBg.setStroke(dp(1), COLOR_DIVIDER);
        et.setBackground(etBg);
        inputWrap.addView(et);
        root.addView(inputWrap);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(dp(24), dp(4), dp(24), dp(8));
        btnRow.setGravity(Gravity.END);
        TextView btnCancel = makeDialogBtn("Huỷ", 0xFF6B7280, 0xFFF3F4F6);
        TextView btnSave   = makeDialogBtn("💾  Lưu", COLOR_WHITE, COLOR_GREEN_DARK);
        btnRow.addView(btnCancel);
        btnRow.addView(makeHSpacer(dp(8)));
        btnRow.addView(btnSave);
        root.addView(btnRow);

        AlertDialog dialog = buildStyledDialog(root);
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            String val = et.getText().toString().trim();
            if (val.isEmpty()) { et.setError("Vui lòng nhập giá trị"); return; }
            listener.onSave(val);
            dialog.dismiss();
        });
        dialog.show();
    }

    private void showHealthEditDialog(String title, String unit,
                                      String current, int inputType, OnSaveListener listener) {
        String[] parts = title.split("  ", 2);
        String emoji   = parts.length > 1 ? parts[0] : "📝";
        String lblText = parts.length > 1 ? parts[1] : title;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, 0, 0, dp(8));
        root.addView(makeDialogHeader(emoji, lblText));

        LinearLayout inputWrap = new LinearLayout(this);
        inputWrap.setOrientation(LinearLayout.HORIZONTAL);
        inputWrap.setGravity(Gravity.CENTER_VERTICAL);
        inputWrap.setPadding(dp(24), dp(12), dp(24), dp(8));

        EditText et = new EditText(this);
        et.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        et.setInputType(inputType);
        et.setText(current.equals("--") ? "" : current);
        et.setTextSize(22f);
        et.setTextColor(0xFF111827);
        et.setPadding(dp(14), dp(14), dp(14), dp(14));
        et.setSelectAllOnFocus(true);
        et.setHint("0.0");
        et.setHintTextColor(0xFFBBF7D0);
        GradientDrawable etBg = new GradientDrawable();
        etBg.setColor(0xFFF0FDF4);
        etBg.setCornerRadius(dp(12));
        etBg.setStroke(dp(1), COLOR_DIVIDER);
        et.setBackground(etBg);

        TextView tvUnit = new TextView(this);
        tvUnit.setText(" " + unit);
        tvUnit.setTextSize(18f);
        tvUnit.setTextColor(0xFF4ADE80);
        tvUnit.setTypeface(null, Typeface.BOLD);
        tvUnit.setPadding(dp(8), 0, 0, 0);

        inputWrap.addView(et);
        inputWrap.addView(tvUnit);
        root.addView(inputWrap);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(dp(24), dp(4), dp(24), dp(8));
        btnRow.setGravity(Gravity.END);
        TextView btnCancel = makeDialogBtn("Huỷ", 0xFF6B7280, 0xFFF3F4F6);
        TextView btnSave   = makeDialogBtn("💾  Lưu", COLOR_WHITE, COLOR_GREEN_DARK);
        btnRow.addView(btnCancel);
        btnRow.addView(makeHSpacer(dp(8)));
        btnRow.addView(btnSave);
        root.addView(btnRow);

        AlertDialog dialog = buildStyledDialog(root);
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            String val = et.getText().toString().trim();
            if (val.isEmpty()) { et.setError("Vui lòng nhập giá trị"); return; }
            listener.onSave(val);
            dialog.dismiss();
        });
        dialog.show();
    }

    private void showPasswordDialog() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, 0, 0, dp(8));
        root.addView(makeDialogHeader("🔑", "Đổi mật khẩu"));

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(24), dp(8), dp(24), dp(8));
        EditText etCurrent = makePasswordField("🔒  Mật khẩu hiện tại");
        EditText etNew     = makePasswordField("🔑  Mật khẩu mới (ít nhất 6 ký tự)");
        EditText etConfirm = makePasswordField("✅  Xác nhận mật khẩu mới");
        form.addView(etCurrent);
        form.addView(makeVSpacer(dp(10)));
        form.addView(etNew);
        form.addView(makeVSpacer(dp(10)));
        form.addView(etConfirm);
        root.addView(form);

        TextView hint = new TextView(this);
        hint.setText("Mật khẩu phải có ít nhất 6 ký tự.");
        hint.setTextSize(11f);
        hint.setTextColor(0xFF9CA3AF);
        hint.setPadding(dp(24), 0, dp(24), dp(4));
        root.addView(hint);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(dp(24), dp(8), dp(24), dp(8));
        btnRow.setGravity(Gravity.END);
        TextView btnCancel = makeDialogBtn("Huỷ", 0xFF6B7280, 0xFFF3F4F6);
        TextView btnSave   = makeDialogBtn("🔐  Đổi mật khẩu", COLOR_WHITE, 0xFF1E40AF);
        btnRow.addView(btnCancel);
        btnRow.addView(makeHSpacer(dp(8)));
        btnRow.addView(btnSave);
        root.addView(btnRow);

        AlertDialog dialog = buildStyledDialog(root);
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            String currentPw = etCurrent.getText().toString().trim();
            String newPw     = etNew    .getText().toString().trim();
            String confirmPw = etConfirm.getText().toString().trim();
            if (currentPw.isEmpty()) { etCurrent.setError("Nhập mật khẩu hiện tại"); etCurrent.requestFocus(); return; }
            if (!currentPw.equals(cachedPassword)) { etCurrent.setError("Mật khẩu hiện tại không đúng"); etCurrent.requestFocus(); shake(etCurrent); return; }
            if (newPw.isEmpty()) { etNew.setError("Nhập mật khẩu mới"); etNew.requestFocus(); return; }
            if (newPw.length() < 6) { etNew.setError("Cần ít nhất 6 ký tự"); etNew.requestFocus(); return; }
            if (newPw.equals(currentPw)) { etNew.setError("Mật khẩu mới phải khác mật khẩu cũ"); etNew.requestFocus(); return; }
            if (!newPw.equals(confirmPw)) { etConfirm.setError("Mật khẩu xác nhận không khớp"); etConfirm.requestFocus(); shake(etConfirm); return; }
            updateField("MatKhau", newPw, "Đã đổi mật khẩu thành công!");
            dialog.dismiss();
        });
        dialog.show();
    }

    private void showNotificationSettingsDialog() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NOTIF, Context.MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, 0, 0, dp(8));
        root.addView(makeDialogHeader("🔔", "Cài đặt thông báo"));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(4), dp(20), dp(4));

        Switch swWater    = addNotifRow(body, "💧", "Nhắc uống nước",      "Nhắc định kỳ uống đủ nước mỗi ngày",          0xFFDBEAFE, 0xFF1D4ED8, prefs.getBoolean("notif_water",    true));
        Switch swSleep    = addNotifRow(body, "🌙", "Nhắc đi ngủ",         "Cảnh báo giờ đi ngủ theo lịch",               0xFFEDE9FE, 0xFF6D28D9, prefs.getBoolean("notif_sleep",    true));
        Switch swExercise = addNotifRow(body, "🏃", "Nhắc vận động",       "Nhắc tập thể dục hằng ngày",                  0xFFFEF3C7, 0xFFB45309, prefs.getBoolean("notif_exercise", true));
        Switch swBmi      = addNotifRow(body, "📊", "Nhắc cập nhật BMI",   "Nhắc nhập cân nặng & chiều cao định kỳ",      0xFFDCFCE7, COLOR_GREEN_DARK, prefs.getBoolean("notif_bmi", false));
        Switch swAll      = addNotifRow(body, "🔕", "Tắt tất cả thông báo","Tạm thời tắt toàn bộ nhắc nhở",              0xFFFEE2E2, 0xFFDC2626, prefs.getBoolean("notif_mute_all", false));
        root.addView(body);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(dp(24), dp(8), dp(24), dp(8));
        btnRow.setGravity(Gravity.END);
        TextView btnCancel = makeDialogBtn("Huỷ", 0xFF6B7280, 0xFFF3F4F6);
        TextView btnSave   = makeDialogBtn("💾  Lưu", COLOR_WHITE, COLOR_GREEN_DARK);
        btnRow.addView(btnCancel);
        btnRow.addView(makeHSpacer(dp(8)));
        btnRow.addView(btnSave);
        root.addView(btnRow);

        AlertDialog dialog = buildStyledDialog(root);
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            SharedPreferences.Editor ed = prefs.edit();
            ed.putBoolean("notif_water",    swWater   .isChecked());
            ed.putBoolean("notif_sleep",    swSleep   .isChecked());
            ed.putBoolean("notif_exercise", swExercise.isChecked());
            ed.putBoolean("notif_bmi",      swBmi     .isChecked());
            ed.putBoolean("notif_mute_all", swAll     .isChecked());
            ed.apply();
            updateNotifSummary();
            Toast.makeText(this, "✅ Đã lưu cài đặt thông báo", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        dialog.show();
    }

    private Switch addNotifRow(LinearLayout container, String emoji, String title, String subtitle,
                               int bgColor, int accentColor, boolean checked) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(8));
        row.setLayoutParams(lp);
        row.setPadding(dp(12), dp(14), dp(12), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(dp(14));
        row.setBackground(bg);

        LinearLayout iconBox = new LinearLayout(this);
        iconBox.setLayoutParams(new LinearLayout.LayoutParams(dp(38), dp(38)));
        iconBox.setGravity(Gravity.CENTER);
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setColor(COLOR_WHITE);
        iconBg.setCornerRadius(dp(10));
        iconBox.setBackground(iconBg);
        iconBox.setAlpha(0.85f);
        TextView emojiTv = new TextView(this);
        emojiTv.setText(emoji);
        emojiTv.setTextSize(17f);
        iconBox.addView(emojiTv);

        LinearLayout textBlock = new LinearLayout(this);
        textBlock.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tbLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tbLp.setMargins(dp(10), 0, dp(8), 0);
        textBlock.setLayoutParams(tbLp);
        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextSize(13.5f);
        tvTitle.setTextColor(accentColor);
        tvTitle.setTypeface(null, Typeface.BOLD);
        TextView tvSub = new TextView(this);
        tvSub.setText(subtitle);
        tvSub.setTextSize(11f);
        tvSub.setTextColor(accentColor);
        tvSub.setAlpha(0.7f);
        textBlock.addView(tvTitle);
        textBlock.addView(tvSub);

        Switch sw = new Switch(this);
        sw.setChecked(checked);
        sw.setThumbTintList(android.content.res.ColorStateList.valueOf(accentColor));
        sw.setTrackTintList(android.content.res.ColorStateList.valueOf(checked ? accentColor : 0xFFD1D5DB));

        row.addView(iconBox);
        row.addView(textBlock);
        row.addView(sw);
        container.addView(row);
        return sw;
    }

    private void updateNotifSummary() {
        if (tvNotifSummary == null) return;
        SharedPreferences prefs = getSharedPreferences(PREFS_NOTIF, Context.MODE_PRIVATE);
        if (prefs.getBoolean("notif_mute_all", false)) {
            tvNotifSummary.setText("🔕 Tất cả thông báo đã tắt");
            return;
        }
        int count = 0;
        if (prefs.getBoolean("notif_water",    true))  count++;
        if (prefs.getBoolean("notif_sleep",    true))  count++;
        if (prefs.getBoolean("notif_exercise", true))  count++;
        if (prefs.getBoolean("notif_bmi",      false)) count++;
        tvNotifSummary.setText(count > 0
                ? count + " loại thông báo đang bật"
                : "Tất cả thông báo đang tắt");
    }

    private void showAboutDialog() {
        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, 0, 0, dp(8));
        sv.addView(root);

        // Header gradient
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER_HORIZONTAL);
        header.setPadding(dp(24), dp(28), dp(24), dp(28));
        GradientDrawable headerBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF15803D, 0xFF22C55E});
        headerBg.setCornerRadii(new float[]{dp(20), dp(20), 0, 0, 0, 0, 0, 0});
        header.setBackground(headerBg);

        TextView iconTv = new TextView(this);
        iconTv.setText("🌿");
        iconTv.setTextSize(52f);
        iconTv.setGravity(Gravity.CENTER);
        header.addView(iconTv);

        TextView appName = new TextView(this);
        appName.setText("BIOCARE");
        appName.setTextSize(24f);
        appName.setTextColor(COLOR_WHITE);
        appName.setTypeface(null, Typeface.BOLD);
        appName.setGravity(Gravity.CENTER);
        appName.setPadding(0, dp(8), 0, dp(2));
        header.addView(appName);

        TextView version = new TextView(this);
        version.setText("Phiên bản 1.0.0");
        version.setTextSize(12f);
        version.setTextColor(0xFFBBF7D0);
        version.setGravity(Gravity.CENTER);
        header.addView(version);
        root.addView(header);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(16), dp(20), dp(8));

        TextView featureTitle = new TextView(this);
        featureTitle.setText("Tính năng nổi bật");
        featureTitle.setTextSize(12f);
        featureTitle.setTextColor(0xFF9CA3AF);
        featureTitle.setTypeface(null, Typeface.BOLD);
        featureTitle.setPadding(0, 0, 0, dp(12));
        body.addView(featureTitle);

        String[][] features = {
                {"📊", "Theo dõi chỉ số BMI",  "Tính toán & phân tích BMI cá nhân"},
                {"💧", "Nhắc uống nước",        "Nhắc nhở uống đủ 2000ml mỗi ngày"},
                {"🌙", "Quản lý giấc ngủ",      "Theo dõi thời gian ngủ hằng ngày"},
                {"🏃", "Gợi ý bài tập",         "Bài tập phù hợp với chỉ số BMI"},
                {"🥗", "Tư vấn dinh dưỡng",     "Thực đơn cá nhân hoá theo mục tiêu"},
        };
        for (String[] f : features) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rlp.setMargins(0, 0, 0, dp(10));
            row.setLayoutParams(rlp);
            row.setPadding(dp(12), dp(14), dp(12), dp(14));
            GradientDrawable rowBg = new GradientDrawable();
            rowBg.setColor(0xFFF0FDF4);
            rowBg.setCornerRadius(dp(14));
            row.setBackground(rowBg);

            TextView eTv = new TextView(this);
            eTv.setText(f[0]);
            eTv.setTextSize(22f);
            eTv.setPadding(0, 0, dp(12), 0);

            LinearLayout textCol = new LinearLayout(this);
            textCol.setOrientation(LinearLayout.VERTICAL);
            textCol.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            TextView lbl = new TextView(this);
            lbl.setText(f[1]);
            lbl.setTextSize(13.5f);
            lbl.setTextColor(0xFF166534);
            lbl.setTypeface(null, Typeface.BOLD);
            TextView sub = new TextView(this);
            sub.setText(f[2]);
            sub.setTextSize(11f);
            sub.setTextColor(0xFF4ADE80);
            textCol.addView(lbl);
            textCol.addView(sub);

            row.addView(eTv);
            row.addView(textCol);
            body.addView(row);
        }

        View div = new View(this);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        divLp.setMargins(0, dp(8), 0, dp(12));
        div.setLayoutParams(divLp);
        div.setBackgroundColor(COLOR_DIVIDER);
        body.addView(div);

        TextView credits = new TextView(this);
        credits.setText("© 2025 – Nhóm phát triển UDTDDK\nsupport@biocares.vn");
        credits.setTextSize(11f);
        credits.setTextColor(0xFF9CA3AF);
        credits.setGravity(Gravity.CENTER);
        body.addView(credits);
        root.addView(body);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(dp(24), dp(4), dp(24), dp(8));
        btnRow.setGravity(Gravity.CENTER);
        TextView btnClose = makeDialogBtn("Đóng", COLOR_WHITE, COLOR_GREEN_DARK);
        btnClose.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        btnClose.setGravity(Gravity.CENTER);
        btnRow.addView(btnClose);
        root.addView(btnRow);

        AlertDialog dialog = buildStyledDialog(sv);
        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showStyledConfirmDialog(String emoji, String title, String message,
                                         String confirmLabel, String confirmHex, Runnable onConfirm) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(24), dp(24), dp(16));
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView iconTv = new TextView(this);
        iconTv.setText(emoji);
        iconTv.setTextSize(40f);
        iconTv.setGravity(Gravity.CENTER);
        root.addView(iconTv);

        TextView titleTv = new TextView(this);
        titleTv.setText(title);
        titleTv.setTextSize(18f);
        titleTv.setTextColor(0xFF111827);
        titleTv.setTypeface(null, Typeface.BOLD);
        titleTv.setGravity(Gravity.CENTER);
        titleTv.setPadding(0, dp(8), 0, dp(4));
        root.addView(titleTv);

        TextView msgTv = new TextView(this);
        msgTv.setText(message);
        msgTv.setTextSize(13f);
        msgTv.setTextColor(0xFF6B7280);
        msgTv.setGravity(Gravity.CENTER);
        msgTv.setPadding(0, 0, 0, dp(20));
        root.addView(msgTv);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);

        TextView btnCancel = makeDialogBtn("Huỷ", 0xFF6B7280, 0xFFF3F4F6);
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        bLp.setMargins(0, 0, dp(8), 0);
        btnCancel.setLayoutParams(bLp);
        btnCancel.setGravity(Gravity.CENTER);

        TextView btnConfirm = makeDialogBtn(confirmLabel, COLOR_WHITE, Color.parseColor(confirmHex));
        LinearLayout.LayoutParams bLp2 = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        btnConfirm.setLayoutParams(bLp2);
        btnConfirm.setGravity(Gravity.CENTER);

        btnRow.addView(btnCancel);
        btnRow.addView(btnConfirm);
        root.addView(btnRow);

        AlertDialog dialog = buildStyledDialog(root);
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> { onConfirm.run(); dialog.dismiss(); });
        dialog.show();
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  Helpers – age, BMI render, field update
    // ════════════════════════════════════════════════════════════════════════════

    private int calculateAge(String dob) {
        if (dob == null || dob.trim().isEmpty()) return -1;
        Date birthDate = null;
        for (String fmt : new String[]{"dd/MM/yyyy", "yyyy-MM-dd", "dd-MM-yyyy", "MM/dd/yyyy"}) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(fmt, Locale.getDefault());
                sdf.setLenient(false);
                birthDate = sdf.parse(dob.trim());
                break;
            } catch (ParseException ignored) {}
        }
        if (birthDate == null) return -1;
        Calendar birth = Calendar.getInstance();
        birth.setTime(birthDate);
        Calendar today = Calendar.getInstance();
        int age = today.get(Calendar.YEAR) - birth.get(Calendar.YEAR);
        if (today.get(Calendar.MONTH) < birth.get(Calendar.MONTH) ||
                (today.get(Calendar.MONTH) == birth.get(Calendar.MONTH) &&
                        today.get(Calendar.DAY_OF_MONTH) < birth.get(Calendar.DAY_OF_MONTH))) age--;
        return age > 0 && age < 150 ? age : -1;
    }

    private void renderBmi(float bmi) {
        String category;
        int badgeColor, textColor;
        if      (bmi < 18.5f) { category = "Thiếu cân";   badgeColor = 0xFFE3F2FD; textColor = 0xFF1565C0; }
        else if (bmi < 25f)   { category = "Bình thường"; badgeColor = 0xFFE8F5E9; textColor = 0xFF27500A; }
        else if (bmi < 30f)   { category = "Thừa cân";    badgeColor = 0xFFFFF8E1; textColor = 0xFFE65100; }
        else                  { category = "Béo phì";      badgeColor = 0xFFFFEBEE; textColor = 0xFFC62828; }

        if (tvProfileBmi != null)
            tvProfileBmi.setText(String.format("BMI: %.1f · %s", bmi, category));
        if (tvBmiValue  != null)
            tvBmiValue.setText(String.format("%.1f", bmi));
        if (tvBmiStatus != null) {
            tvBmiStatus.setText(category);
            tvBmiStatus.setBackgroundColor(badgeColor);
            tvBmiStatus.setTextColor(textColor);
        }
        if (tvProfileHealthStatus != null)
            tvProfileHealthStatus.setText(category);
    }

    private void updateEmail(String email) {
        if (email.isEmpty()) { Toast.makeText(this, "Vui lòng nhập email", Toast.LENGTH_SHORT).show(); return; }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { Toast.makeText(this, "Email không đúng định dạng", Toast.LENGTH_SHORT).show(); return; }
        updateField("Email", email, "Đã cập nhật email!");
    }

    private void updateField(String field, String value, String successMsg) {
        if (db == null)
            db = FirebaseDatabase.getInstance("https://udtddk-default-rtdb.firebaseio.com/")
                    .getReference("NguoiDung");
        if (field.equals("CanNang") || field.equals("ChieuCao")) {
            try {
                float number = Float.parseFloat(value.replace(',', '.'));
                if (number <= 0) { Toast.makeText(this, "Giá trị phải lớn hơn 0", Toast.LENGTH_SHORT).show(); return; }
            } catch (Exception e) {
                Toast.makeText(this, "Vui lòng nhập số hợp lệ", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        db.child(userId).child(field).setValue(value)
                .addOnSuccessListener(a -> {
                    Toast.makeText(this, "✅ " + successMsg, Toast.LENGTH_SHORT).show();
                    if (field.equals("CanNang"))  cachedWeight   = value;
                    if (field.equals("ChieuCao")) cachedHeight   = value;
                    if (field.equals("NgaySinh")) cachedDob      = value;
                    if (field.equals("MatKhau"))  cachedPassword = value;
                    loadProfile();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "❌ Lỗi cập nhật", Toast.LENGTH_SHORT).show());
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  Health Report – entry point
    // ════════════════════════════════════════════════════════════════════════════

    private void showHealthReport() {
        if (userId == null) {
            Toast.makeText(this, "Chưa đăng nhập", Toast.LENGTH_SHORT).show();
            return;
        }

        DatabaseReference ref = FirebaseDatabase.getInstance("https://udtddk-default-rtdb.firebaseio.com/")
                .getReference("NguoiDung").child(userId);

        // Thử ChiSoSucKhoe trước, fallback chiSoSucKhoe
        ref.child("ChiSoSucKhoe").orderByKey()
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        if (!snapshot.exists()) {
                            ref.child("chiSoSucKhoe").orderByKey()
                                    .addListenerForSingleValueEvent(new ValueEventListener() {
                                        @Override
                                        public void onDataChange(DataSnapshot s2) {
                                            processReportSnapshot(s2);
                                        }
                                        @Override public void onCancelled(DatabaseError e) {}
                                    });
                            return;
                        }
                        processReportSnapshot(snapshot);
                    }
                    @Override
                    public void onCancelled(DatabaseError error) {
                        Toast.makeText(ProfileActivity.this,
                                "Lỗi tải báo cáo: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void processReportSnapshot(DataSnapshot snapshot) {
        if (!snapshot.exists()) {
            Toast.makeText(ProfileActivity.this, "Chưa có dữ liệu sức khoẻ", Toast.LENGTH_SHORT).show();
            return;
        }

        float wt = 0f, ht = 0f;
        try { wt = Float.parseFloat(cachedWeight.replace(',', '.')); } catch (Exception ignored) {}
        try { ht = Float.parseFloat(cachedHeight.replace(',', '.')); } catch (Exception ignored) {}

        List<MetricEntry> entries = new ArrayList<>();
        for (DataSnapshot child : snapshot.getChildren()) {
            String date = child.getKey();
            float bmi = 0f, water = 0f, sleep = 0f;

            // Hỗ trợ cả PascalCase lẫn camelCase trong ChiSoSucKhoe
            try {
                Object o = child.child("BMI").getValue();
                if (o == null) o = child.child("bmi").getValue();
                if (o != null) bmi = Float.parseFloat(o.toString());
            } catch (Exception ignored) {}
            try {
                Object o = child.child("LuongNuoc").getValue();
                if (o == null) o = child.child("luongNuoc").getValue();
                if (o != null) water = Float.parseFloat(o.toString());
            } catch (Exception ignored) {}
            try {
                Object o = child.child("ThoiGianNgu").getValue();
                if (o == null) o = child.child("thoiGianNgu").getValue();
                if (o != null) sleep = Float.parseFloat(o.toString());
            } catch (Exception ignored) {}

            if (bmi > 0) entries.add(new MetricEntry(date, bmi, water, sleep, classifyBmi(bmi), wt, ht));
        }

        if (entries.isEmpty()) {
            Toast.makeText(ProfileActivity.this, "Chưa có dữ liệu hợp lệ", Toast.LENGTH_SHORT).show();
            return;
        }
        buildAndShowReportDialog(entries);
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  MetricEntry model
    // ════════════════════════════════════════════════════════════════════════════

    private static class MetricEntry {
        String date, bmiCategory;
        float bmi, water, sleepHours, weight, height;
        MetricEntry(String d, float b, float w, float s, String cat, float wt, float ht) {
            date = d; bmi = b; water = w; sleepHours = s; bmiCategory = cat; weight = wt; height = ht;
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  Health Report – build dialog
    // ════════════════════════════════════════════════════════════════════════════

    private void buildAndShowReportDialog(List<MetricEntry> entries) {
        Collections.sort(entries, (a, b) -> a.date.compareTo(b.date));
        MetricEntry latest = entries.get(entries.size() - 1);
        int n = entries.size();

        float sumBmi = 0, sumWater = 0, sumSleep = 0,
                maxBmi = Float.MIN_VALUE, minBmi = Float.MAX_VALUE;
        for (MetricEntry e : entries) {
            sumBmi   += e.bmi;
            sumWater += e.water;
            sumSleep += e.sleepHours;
            if (e.bmi > maxBmi) maxBmi = e.bmi;
            if (e.bmi < minBmi) minBmi = e.bmi;
        }
        float avgBmi = sumBmi / n, avgWater = sumWater / n, avgSleep = sumSleep / n;
        String firstDate = entries.get(0).date, lastDate = latest.date;

        if (cachedWeight.equals("--") && latest.weight > 0) cachedWeight = String.valueOf(latest.weight);
        if (cachedHeight.equals("--") && latest.height > 0) cachedHeight = String.valueOf(latest.height);

        Map<String, float[]> byMonth = new java.util.LinkedHashMap<>();
        for (MetricEntry e : entries) {
            String mk = e.date.length() >= 7 ? e.date.substring(0, 7) : e.date;
            if (!byMonth.containsKey(mk)) byMonth.put(mk, new float[]{0, 0, 0, 0});
            float[] v = byMonth.get(mk);
            v[0] += e.bmi; v[1] += e.water; v[2] += e.sleepHours; v[3]++;
        }


        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.parseColor("#F0F7F2"));
        sv.setPadding(dp(14), dp(14), dp(14), dp(14));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        sv.addView(root);

        // ── HEADER ──
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER);
        header.setPadding(dp(20), dp(22), dp(20), dp(22));
        GradientDrawable hBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{0xFF1B5E20, 0xFF388E3C});
        hBg.setCornerRadius(dp(18));
        header.setBackground(hBg);

        TextView tvTitle = makeReportTv("🌿  BÁO CÁO SỨC KHOẺ", 18f, Color.WHITE, Typeface.BOLD, Gravity.CENTER);
        header.addView(tvTitle);

        LinearLayout dateChip = new LinearLayout(this);
        dateChip.setOrientation(LinearLayout.HORIZONTAL);
        dateChip.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        chipLp.topMargin = dp(8);
        dateChip.setLayoutParams(chipLp);
        dateChip.setPadding(dp(14), dp(6), dp(14), dp(6));
        GradientDrawable chipBg = new GradientDrawable();
        chipBg.setColor(0x33FFFFFF);
        chipBg.setCornerRadius(dp(20));
        dateChip.setBackground(chipBg);
        dateChip.addView(makeReportTv(
                "📅  " + firstDate + "  –  " + lastDate + "   ·   " + n + " lần ghi nhận",
                11f, Color.parseColor("#C8E6C9"), Typeface.NORMAL, Gravity.CENTER));
        header.addView(dateChip);
        root.addView(header);
        root.addView(rSpacer(12));

        // ── TỔNG QUAN ──
        root.addView(rSectionLabel("📊  TỔNG QUAN"));
        root.addView(rSpacer(8));

        String dispWeight = latest.weight > 0 ? latest.weight + " kg" : cachedWeight + " kg";
        String dispHeight = latest.height > 0 ? latest.height + " cm" : cachedHeight + " cm";

        LinearLayout cards1 = makeRHRow();
        cards1.addView(rMiniCard("BMI mới nhất",
                String.format(Locale.getDefault(), "%.1f", latest.bmi),
                latest.bmiCategory, bmiCardBg(latest.bmi), bmiCardAccent(latest.bmi), 1f));
        cards1.addView(rSpacer2(8));
        cards1.addView(rMiniCard("BMI trung bình",
                String.format(Locale.getDefault(), "%.1f", avgBmi),
                classifyBmi(avgBmi), bmiCardBg(avgBmi), bmiCardAccent(avgBmi), 1f));
        root.addView(cards1);
        root.addView(rSpacer(8));

        LinearLayout cards2 = makeRHRow();
        cards2.addView(rMiniCard("TB nước / ngày",
                (int) avgWater + " ml", waterStatus(avgWater), "#E3F2FD", "#1565C0", 1f));
        cards2.addView(rSpacer2(8));
        cards2.addView(rMiniCard("TB giấc ngủ",
                String.format(Locale.getDefault(), "%.1f h", avgSleep),
                sleepStatus(avgSleep), "#EDE9FE", "#6D28D9", 1f));
        root.addView(cards2);
        root.addView(rSpacer(8));

        LinearLayout cards3 = makeRHRow();
        cards3.addView(rMiniCard("Cân nặng", dispWeight, "Gần nhất", "#FFF3E0", "#E65100", 1f));
        cards3.addView(rSpacer2(8));
        cards3.addView(rMiniCard("Chiều cao", dispHeight, "Hồ sơ", "#F0FDF4", "#15803D", 1f));
        root.addView(cards3);
        root.addView(rSpacer(14));

        // ── BMI ──
        root.addView(rSectionLabel("⚖️  CHỈ SỐ BMI"));
        root.addView(rSpacer(8));
        LinearLayout bmiCard = rWhiteCard();
        bmiCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        bmiCard.addView(drawBmiScaleView(latest.bmi));
        bmiCard.addView(rSpacer(12));
        addRDetailRow(bmiCard, "BMI mới nhất",    String.format("%.1f  (%s)", latest.bmi, latest.bmiCategory), bmiTextColor(latest.bmi));
        addRDetailRow(bmiCard, "BMI trung bình",  String.format("%.1f", avgBmi),  bmiTextColor(avgBmi));
        addRDetailRow(bmiCard, "BMI cao nhất",    String.format("%.1f", maxBmi),  bmiTextColor(maxBmi));
        addRDetailRow(bmiCard, "BMI thấp nhất",   String.format("%.1f", minBmi),  bmiTextColor(minBmi));
        root.addView(bmiCard);
        root.addView(rSpacer(12));

        // ── NƯỚC ──
        root.addView(rSectionLabel("💧  LƯỢNG NƯỚC UỐNG"));
        root.addView(rSpacer(8));
        LinearLayout waterCard = rWhiteCard();
        waterCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        waterCard.addView(drawProgressBar(latest.water, 2000f,
                Color.parseColor("#1565C0"), Color.parseColor("#E3F2FD"),
                (int) latest.water + " ml / 2000 ml mục tiêu"));
        waterCard.addView(rSpacer(12));
        addRDetailRow(waterCard, "Hôm nay",           (int) latest.water + " ml", waterTextColor(latest.water));
        addRDetailRow(waterCard, "Trung bình / ngày",  (int) avgWater + " ml",    waterTextColor(avgWater));
        addRDetailRow(waterCard, "Đánh giá",           waterStatus(avgWater),      waterTextColor(avgWater));
        root.addView(waterCard);
        root.addView(rSpacer(12));

        // ── NGỦ ──
        root.addView(rSectionLabel("🌙  GIẤC NGỦ"));
        root.addView(rSpacer(8));
        LinearLayout sleepCard = rWhiteCard();
        sleepCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        sleepCard.addView(drawProgressBar(latest.sleepHours, 9f,
                Color.parseColor("#6D28D9"), Color.parseColor("#EDE9FE"),
                String.format("%.1f giờ / 8 giờ khuyến nghị", latest.sleepHours)));
        sleepCard.addView(rSpacer(12));
        addRDetailRow(sleepCard, "Gần nhất",           String.format("%.1f giờ", latest.sleepHours), sleepTextColor(latest.sleepHours));
        addRDetailRow(sleepCard, "Trung bình / ngày",  String.format("%.1f giờ", avgSleep),          sleepTextColor(avgSleep));
        addRDetailRow(sleepCard, "Đánh giá",           sleepStatus(avgSleep),                         sleepTextColor(avgSleep));
        root.addView(sleepCard);
        root.addView(rSpacer(12));

        // ── THEO THÁNG ──
        root.addView(rSectionLabel("📅  TỔNG KẾT THEO THÁNG"));
        root.addView(rSpacer(8));
        LinearLayout monthCard = rWhiteCard();
        monthCard.setPadding(dp(12), dp(10), dp(12), dp(10));
        TreeMap<String, float[]> sorted = new TreeMap<>(Collections.reverseOrder());
        sorted.putAll(byMonth);
        boolean firstMonth = true;
        for (Map.Entry<String, float[]> me : sorted.entrySet()) {
            float[] v = me.getValue();
            int cnt = (int) v[3];
            if (!firstMonth) {
                View divider = new View(this);
                LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1);
                dLp.setMargins(0, dp(8), 0, dp(8));
                divider.setLayoutParams(dLp);
                divider.setBackgroundColor(Color.parseColor("#F0F5F2"));
                monthCard.addView(divider);
            }
            monthCard.addView(buildMonthRow(me.getKey(), cnt, v[0]/cnt, v[1]/cnt, v[2]/cnt));
            firstMonth = false;
        }
        root.addView(monthCard);
        root.addView(rSpacer(12));

        // ── NHẬN XÉT ──
        root.addView(rSectionLabel("💡  NHẬN XÉT & GỢI Ý"));
        root.addView(rSpacer(8));
        LinearLayout adviceCard = new LinearLayout(this);
        adviceCard.setOrientation(LinearLayout.VERTICAL);
        adviceCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable advBg = new GradientDrawable();
        advBg.setColor(Color.parseColor("#F0FDF4"));
        advBg.setCornerRadius(dp(14));
        advBg.setStroke(dp(1), Color.parseColor("#BBF7D0"));
        adviceCard.setBackground(advBg);
        for (String advice : buildAdviceLines(latest.bmi, avgSleep, avgWater)) {
            LinearLayout advRow = new LinearLayout(this);
            advRow.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams advLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            advLp.bottomMargin = dp(6);
            advRow.setLayoutParams(advLp);
            TextView bullet = makeReportTv("●", 13f, Color.parseColor("#22C55E"), Typeface.BOLD, Gravity.TOP);
            bullet.setPadding(0, dp(1), dp(8), 0);
            advRow.addView(bullet);
            TextView advTv = makeReportTv(advice, 12.5f, Color.parseColor("#166534"), Typeface.NORMAL, Gravity.NO_GRAVITY);
            advTv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            advTv.setLineSpacing(dp(2), 1f);
            advRow.addView(advTv);
            adviceCard.addView(advRow);
        }
        root.addView(adviceCard);
        root.addView(rSpacer(20));

        // ── BUTTONS ──
        LinearLayout btnRow = makeRHRow();
        btnRow.setGravity(Gravity.CENTER);
        TextView btnPdf = makeDialogBtn("📄  Xuất PDF", COLOR_WHITE, 0xFF1B5E20);
        LinearLayout.LayoutParams pdfLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        pdfLp.setMarginEnd(dp(8));
        btnPdf.setLayoutParams(pdfLp);
        btnPdf.setGravity(Gravity.CENTER);
        TextView btnClose = makeDialogBtn("✕  Đóng", 0xFF374151, 0xFFE5E7EB);
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        btnClose.setLayoutParams(closeLp);
        btnClose.setGravity(Gravity.CENTER);
        btnRow.addView(btnPdf);
        btnRow.addView(btnClose);
        root.addView(btnRow);
        root.addView(rSpacer(4));

        final List<MetricEntry> fEntries  = entries;
        final float fAvgBmi = avgBmi, fAvgWater = avgWater, fAvgSleep = avgSleep,
                fMaxBmi = maxBmi, fMinBmi = minBmi;
        final String fFirst = firstDate, fLast = lastDate;
        final Map<String, float[]> fByMonth = byMonth;

        AlertDialog dialog = new AlertDialog.Builder(this).setView(sv).create();
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        btnPdf.setOnClickListener(v -> {
            dialog.dismiss();
            exportHealthReportPdf(fEntries, fAvgBmi, fAvgWater, fAvgSleep,
                    fMaxBmi, fMinBmi, fFirst, fLast, fByMonth);
        });
        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();

        if (dialog.getWindow() != null)
            dialog.getWindow().setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.94f),
                    android.view.WindowManager.LayoutParams.WRAP_CONTENT);
    }




    private View drawBmiScaleView(float bmi) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout scaleBar = new LinearLayout(this);
        scaleBar.setOrientation(LinearLayout.HORIZONTAL);
        scaleBar.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(14)));

        int[][] segColors = {{0xFF1565C0, 0xFFE3F2FD}, {0xFF2E7D32, 0xFFE8F5E9},
                {0xFFF57F17, 0xFFFFFDE7}, {0xFFC62828, 0xFFFFEBEE}};
        float[] weights   = {0.25f, 0.3f, 0.25f, 0.2f};

        for (int i = 0; i < 4; i++) {
            View seg = new View(this);
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, dp(14), weights[i]);
            if (i > 0) sp.setMarginStart(dp(2));
            seg.setLayoutParams(sp);
            GradientDrawable segBg = new GradientDrawable();
            segBg.setColor(segColors[i][1]);
            if (i == 0)      segBg.setCornerRadii(new float[]{dp(7), dp(7), 0, 0, 0, 0, dp(7), dp(7)});
            else if (i == 3) segBg.setCornerRadii(new float[]{0, 0, dp(7), dp(7), dp(7), dp(7), 0, 0});
            seg.setBackground(segBg);
            scaleBar.addView(seg);
        }
        container.addView(scaleBar);

        float clampedBmi = Math.max(14f, Math.min(bmi, 40f));
        float pct = (clampedBmi - 14f) / (40f - 14f);
        TextView pointer = new TextView(this);
        pointer.setText("▲ " + String.format(Locale.getDefault(), "%.1f", bmi));
        pointer.setTextSize(11f);
        pointer.setTextColor(bmiTextColor(bmi));
        pointer.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams ptLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ptLp.topMargin = dp(2);
        ptLp.setMarginStart((int) (pct * getResources().getDisplayMetrics().widthPixels * 0.55f));
        pointer.setLayoutParams(ptLp);
        container.addView(pointer);

        LinearLayout labRow = makeRHRow();
        String[] scaleLabels = {"Thiếu cân", "Bình thường", "Thừa cân", "Béo phì"};
        int[]    scaleTxt    = {0xFF1565C0, 0xFF2E7D32, 0xFFF57F17, 0xFFC62828};
        for (int i = 0; i < 4; i++) {
            TextView tv = makeReportTv(scaleLabels[i], 9f, scaleTxt[i], Typeface.BOLD, Gravity.CENTER);
            tv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weights[i]));
            labRow.addView(tv);
            if (i < 3) labRow.addView(rSpacer2(2));
        }
        LinearLayout.LayoutParams labLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        labLp.topMargin = dp(2);
        labRow.setLayoutParams(labLp);
        container.addView(labRow);
        return container;
    }

    private View drawProgressBar(float value, float max, int fillColor, int bgColor, String label) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView tvLabel = makeReportTv(label, 11.5f, fillColor, Typeface.BOLD, Gravity.NO_GRAVITY);
        LinearLayout.LayoutParams lblLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lblLp.bottomMargin = dp(6);
        tvLabel.setLayoutParams(lblLp);
        container.addView(tvLabel);

        LinearLayout barBg = new LinearLayout(this);
        barBg.setOrientation(LinearLayout.HORIZONTAL);
        barBg.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(12)));
        GradientDrawable barBgBg = new GradientDrawable();
        barBgBg.setColor(bgColor);
        barBgBg.setCornerRadius(dp(6));
        barBg.setBackground(barBgBg);
        barBg.setPadding(dp(2), dp(2), dp(2), dp(2));

        float ratio = Math.min(value / max, 1f);
        View fill = new View(this);
        fill.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, ratio));
        GradientDrawable fillBg = new GradientDrawable();
        fillBg.setColor(fillColor);
        fillBg.setCornerRadius(dp(5));
        fill.setBackground(fillBg);
        barBg.addView(fill);

        View fillSpacer = new View(this);
        fillSpacer.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f - ratio));
        barBg.addView(fillSpacer);
        container.addView(barBg);
        return container;
    }

    private View buildMonthRow(String month, int cnt, float avgBmi, float avgWater, float avgSleep) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout top = makeRHRow();
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView tvMonth = makeReportTv("📅  " + month, 12.5f,
                Color.parseColor("#0F2D1F"), Typeface.BOLD, Gravity.NO_GRAVITY);
        tvMonth.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(tvMonth);
        top.addView(makeReportTv(cnt + " ngày", 11f, Color.parseColor("#9E9E9E"), Typeface.NORMAL, Gravity.NO_GRAVITY));
        row.addView(top);

        LinearLayout chips = makeRHRow();
        LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        chipLp.topMargin = dp(6);
        chips.setLayoutParams(chipLp);
        chips.addView(rChip(String.format("BMI %.1f", avgBmi), bmiCardBg(avgBmi), bmiCardAccent(avgBmi)));
        chips.addView(rSpacer2(6));
        chips.addView(rChip(String.format("💧 %.0fml", avgWater), "#E3F2FD", "#1565C0"));
        chips.addView(rSpacer2(6));
        chips.addView(rChip(String.format("🌙 %.1fh", avgSleep), "#EDE9FE", "#6D28D9"));
        row.addView(chips);
        return row;
    }

    private View rChip(String text, String bgHex, String textHex) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(11f);
        tv.setTextColor(Color.parseColor(textHex));
        tv.setTypeface(null, Typeface.BOLD);
        tv.setPadding(dp(10), dp(5), dp(10), dp(5));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor(bgHex));
        bg.setCornerRadius(dp(20));
        tv.setBackground(bg);
        return tv;
    }

    private void addRDetailRow(LinearLayout parent, String label, String value, int valueColor) {
        LinearLayout row = makeRHRow();
        row.setPadding(0, dp(6), 0, dp(6));
        TextView tvLabel = makeReportTv(label, 12.5f, Color.parseColor("#546E7A"), Typeface.NORMAL, Gravity.NO_GRAVITY);
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(tvLabel);
        row.addView(makeReportTv(value, 12.5f, valueColor, Typeface.BOLD, Gravity.END));
        parent.addView(row);
        View div = new View(this);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        div.setLayoutParams(divLp);
        div.setBackgroundColor(Color.parseColor("#F0F5F2"));
        parent.addView(div);
    }

    private String[] buildAdviceLines(float bmi, float sleep, float water) {
        List<String> lines = new ArrayList<>();
        if      (bmi < 18.5f) lines.add("BMI dưới ngưỡng lý tưởng – tăng cường dinh dưỡng và bài tập xây cơ để cải thiện.");
        else if (bmi < 25f)   lines.add("BMI hoàn toàn lý tưởng – duy trì lối sống và chế độ ăn uống hiện tại.");
        else if (bmi < 30f)   lines.add("Chỉ số BMI hơi cao – tăng cardio và kiểm soát khẩu phần sẽ giúp cải thiện đáng kể.");
        else                  lines.add("BMI ở mức béo phì – nên tham vấn chuyên gia dinh dưỡng để có kế hoạch phù hợp.");
        if      (sleep < 6)   lines.add("Thời gian ngủ trung bình dưới 6 giờ – cần ưu tiên cải thiện chất lượng giấc ngủ.");
        else if (sleep > 9)   lines.add("Thời gian ngủ trung bình khá dài – duy trì lịch ngủ – thức đều đặn sẽ tốt hơn.");
        else                  lines.add("Thời gian ngủ trung bình đạt chuẩn khuyến nghị – tiếp tục duy trì thói quen tốt này.");
        if      (water < 1500)  lines.add("Lượng nước uống trung bình chưa đủ – cố đạt 2000 ml / ngày để hỗ trợ trao đổi chất.");
        else if (water <= 3000) lines.add("Lượng nước uống trung bình rất tốt – duy trì mức này mỗi ngày.");
        else                    lines.add("Bạn uống nước khá nhiều – mức đang đủ tốt, không cần ép thêm.");
        return lines.toArray(new String[0]);
    }



    private void exportHealthReportPdf(List<MetricEntry> entries, float avgBmi, float avgWater,
                                       float avgSleep, float maxBmi, float minBmi,
                                       String firstDate, String lastDate,
                                       Map<String, float[]> byMonth) {
        MetricEntry latest = entries.get(entries.size() - 1);
        int n = entries.size();
        String today = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date());
        String dispWeight = latest.weight > 0 ? latest.weight + " kg" : cachedWeight + " kg";
        String dispHeight = latest.height > 0 ? latest.height + " cm" : cachedHeight + " cm";

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>")
                .append("<style>")
                .append("*{box-sizing:border-box;margin:0;padding:0}")
                .append("body{font-family:Arial,sans-serif;color:#0F2D1F;font-size:13px;line-height:1.6;background:#F0F7F2}")
                .append(".page{max-width:700px;margin:0 auto;background:white}")
                .append(".header{background:linear-gradient(135deg,#1B5E20,#388E3C);color:white;padding:28px 40px;text-align:center}")
                .append(".header h1{font-size:20px;letter-spacing:1px;margin-bottom:6px}")
                .append(".header .meta{font-size:11px;color:#A5D6A7;background:rgba(255,255,255,.15);display:inline-block;padding:5px 18px;border-radius:20px}")
                .append(".body{padding:28px 40px}")
                .append(".section{margin-bottom:22px}")
                .append(".sec-title{font-size:12px;font-weight:bold;color:#1B5E20;background:#E8F5E9;padding:7px 14px;border-left:4px solid #2E7D32;margin-bottom:12px}")
                .append(".cards{display:flex;gap:10px;margin-bottom:10px}")
                .append(".card{flex:1;border-radius:10px;padding:14px 10px;text-align:center}")
                .append(".card .val{font-size:22px;font-weight:bold;margin:4px 0 2px}")
                .append(".card .sub{font-size:10px;color:#888;margin-top:2px}")
                .append(".card .cat{font-size:10px;font-weight:bold;margin-top:3px}")
                .append("table{width:100%;border-collapse:collapse}")
                .append("td{padding:8px 10px;border-bottom:1px solid #EAF5EE;font-size:12.5px}")
                .append("td:last-child{font-weight:bold;text-align:right}")
                .append(".bar-bg{background:#EEE;border-radius:4px;height:8px;margin:6px 0 2px;overflow:hidden}")
                .append(".bar-fill{height:8px;border-radius:4px}")
                .append(".month-row{padding:8px 10px;border-bottom:1px solid #F0F5F2}")
                .append(".chip{display:inline-block;padding:3px 10px;border-radius:12px;font-size:10px;font-weight:bold;margin-right:5px}")
                .append(".advice{background:#F0FDF4;border:1px solid #BBF7D0;border-radius:10px;padding:14px 16px}")
                .append(".advice li{font-size:12px;color:#166534;margin-bottom:5px;list-style:disc;margin-left:14px}")
                .append(".footer{background:#E8F5E9;padding:12px;text-align:center;font-size:10.5px;color:#2E7D32}")
                .append("</style></head><body><div class='page'>")
                .append("<div class='header'><h1>&#127807; BAO CAO SUC KHOE CA NHAN</h1>")
                .append("<div class='meta'>").append(firstDate).append(" – ").append(lastDate)
                .append("  ·  ").append(n).append(" lan ghi nhan  ·  Xuat: ").append(today).append("</div></div>")
                .append("<div class='body'>")
                .append("<div class='section'><div class='sec-title'>TONG QUAN</div>")
                .append("<div class='cards'>")
                .append(htmlCard(String.format("%.1f", latest.bmi), "BMI moi nhat",   latest.bmiCategory,    bmiCardBg(latest.bmi), bmiCardAccent(latest.bmi)))
                .append(htmlCard(String.format("%.1f", avgBmi),     "BMI trung binh", classifyBmi(avgBmi),   bmiCardBg(avgBmi),     bmiCardAccent(avgBmi)))
                .append(htmlCard((int)avgWater + " ml",             "TB nuoc/ngay",   waterStatus(avgWater), "#E3F2FD", "#1565C0"))
                .append(htmlCard(String.format("%.1fh", avgSleep),  "TB giac ngu",    sleepStatus(avgSleep), "#EDE9FE", "#6D28D9"))
                .append("</div><div class='cards'>")
                .append(htmlCard(dispWeight, "Can nang", "Gan nhat", "#FFF3E0", "#E65100"))
                .append(htmlCard(dispHeight, "Chieu cao", "Ho so",   "#F0FDF4", "#15803D"))
                .append("</div></div>")
                .append("<div class='section'><div class='sec-title'>CHI SO BMI</div><table>")
                .append(tr("BMI moi nhat",   String.format("%.1f (%s)", latest.bmi, latest.bmiCategory)))
                .append(tr("BMI trung binh", String.format("%.1f", avgBmi)))
                .append(tr("BMI cao nhat",   String.format("%.1f", maxBmi)))
                .append(tr("BMI thap nhat",  String.format("%.1f", minBmi)))
                .append(tr("Can nang", dispWeight))
                .append(tr("Chieu cao", dispHeight))
                .append("</table></div>")
                .append("<div class='section'><div class='sec-title'>LUONG NUOC UONG</div>")
                .append("<div class='bar-bg'><div class='bar-fill' style='width:")
                .append(Math.min((int)(latest.water / 2000f * 100), 100))
                .append("%;background:#1565C0'></div></div><table>")
                .append(tr("Hom nay",         (int)latest.water + " ml"))
                .append(tr("Trung binh/ngay",  (int)avgWater + " ml"))
                .append(tr("Danh gia",         waterStatus(avgWater)))
                .append("</table></div>")
                .append("<div class='section'><div class='sec-title'>GIAC NGU</div>")
                .append("<div class='bar-bg'><div class='bar-fill' style='width:")
                .append(Math.min((int)(latest.sleepHours / 9f * 100), 100))
                .append("%;background:#6D28D9'></div></div><table>")
                .append(tr("Gan nhat",        String.format("%.1f gio", latest.sleepHours)))
                .append(tr("Trung binh/ngay", String.format("%.1f gio", avgSleep)))
                .append(tr("Danh gia",        sleepStatus(avgSleep)))
                .append("</table></div>");

        html.append("<div class='section'><div class='sec-title'>TONG KET THEO THANG</div>");
        List<String> mKeys = new ArrayList<>(byMonth.keySet());
        Collections.sort(mKeys, Collections.reverseOrder());
        for (String mk : mKeys) {
            float[] v   = byMonth.get(mk);
            int     cnt = (int) v[3];
            html.append("<div class='month-row'><b>").append(mk).append("</b>  ")
                    .append("<span style='color:#9E9E9E;font-size:11px'>").append(cnt).append(" ngay</span><br>")
                    .append("<span class='chip' style='background:").append(bmiCardBg(v[0]/cnt))
                    .append(";color:").append(bmiCardAccent(v[0]/cnt)).append("'>BMI ")
                    .append(String.format("%.1f", v[0]/cnt)).append("</span>")
                    .append("<span class='chip' style='background:#E3F2FD;color:#1565C0'>&#128167; ")
                    .append((int)(v[1]/cnt)).append("ml</span>")
                    .append("<span class='chip' style='background:#EDE9FE;color:#6D28D9'>&#127769; ")
                    .append(String.format("%.1f", v[2]/cnt)).append("h</span>")
                    .append("</div>");
        }
        html.append("</div>");

        html.append("<div class='section'><div class='sec-title'>NHAN XET & GOI Y</div>")
                .append("<div class='advice'><ul>");
        for (String a : buildAdviceLines(latest.bmi, avgSleep, avgWater))
            html.append("<li>").append(a).append("</li>");
        html.append("</ul></div></div>")
                .append("</div>")
                .append("<div class='footer'>BIOCARE &nbsp;·&nbsp; © 2025 Nhom UDTDDK &nbsp;·&nbsp; ")
                .append(today).append("</div>")
                .append("</div></body></html>");

        WebView webView = new WebView(this);
        webView.loadDataWithBaseURL(null, html.toString(), "text/html", "UTF-8", null);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                PrintManager pm = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                String jobName = "BaoCao" + today.replace("/", "-");
                pm.print(jobName, view.createPrintDocumentAdapter(jobName),
                        new PrintAttributes.Builder().build());
            }
        });
    }

    private String htmlCard(String val, String label, String sub, String bg, String accent) {
        return "<div class='card' style='background:" + bg + "'>" +
                "<div class='val' style='color:" + accent + "'>" + val + "</div>" +
                "<div style='font-size:10px;color:#555'>" + label + "</div>" +
                "<div class='cat' style='color:" + accent + "'>" + sub + "</div>" +
                "</div>";
    }

    private String tr(String label, String value) {
        return "<tr><td>" + label + "</td><td>" + value + "</td></tr>";
    }



    private TextView makeReportTv(String text, float sizeSp, int color, int style, int gravity) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(sizeSp);
        tv.setTextColor(color);
        tv.setTypeface(null, style);
        tv.setGravity(gravity);
        return tv;
    }

    private LinearLayout makeRHRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private LinearLayout rWhiteCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(14));
        card.setBackground(bg);
        card.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private LinearLayout rMiniCard(String label, String value, String sub,
                                   String bgHex, String accentHex, float weight) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(10), dp(14), dp(10), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor(bgHex));
        bg.setCornerRadius(dp(14));
        card.setBackground(bg);
        card.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, weight));

        TextView tvVal = makeReportTv(value, 20f, Color.parseColor(accentHex), Typeface.BOLD, Gravity.CENTER);
        card.addView(tvVal);

        TextView tvLabel = makeReportTv(label, 9.5f, Color.parseColor("#757575"), Typeface.NORMAL, Gravity.CENTER);
        card.addView(tvLabel);

        if (sub != null && !sub.isEmpty()) {
            TextView tvSub = makeReportTv(sub, 9f, Color.parseColor(accentHex), Typeface.BOLD, Gravity.CENTER);
            LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            subLp.topMargin = dp(3);
            tvSub.setLayoutParams(subLp);
            GradientDrawable subBg = new GradientDrawable();
            subBg.setColor(0x22000000);
            subBg.setCornerRadius(dp(8));
            tvSub.setPadding(dp(8), dp(2), dp(8), dp(2));
            tvSub.setBackground(subBg);
            card.addView(tvSub);
        }
        return card;
    }

    private TextView rSectionLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(12.5f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setTextColor(Color.parseColor("#1B5E20"));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#E8F5E9"));
        bg.setCornerRadius(dp(8));
        tv.setBackground(bg);
        tv.setPadding(dp(12), dp(8), dp(12), dp(8));
        tv.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return tv;
    }

    private View rSpacer(int heightDp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(heightDp)));
        return v;
    }

    private View rSpacer2(int widthDp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                dp(widthDp), LinearLayout.LayoutParams.MATCH_PARENT));
        return v;
    }


    private LinearLayout makeDialogHeader(String emoji, String title) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(20), dp(20), dp(16));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFF15803D, 0xFF22C55E});
        bg.setCornerRadii(new float[]{dp(20), dp(20), 0, 0, 0, 0, 0, 0});
        header.setBackground(bg);

        LinearLayout iconBox = new LinearLayout(this);
        iconBox.setLayoutParams(new LinearLayout.LayoutParams(dp(44), dp(44)));
        iconBox.setGravity(Gravity.CENTER);
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setColor(0x33FFFFFF);
        iconBg.setCornerRadius(dp(22));
        iconBox.setBackground(iconBg);
        TextView emojiTv = new TextView(this);
        emojiTv.setText(emoji);
        emojiTv.setTextSize(20f);
        iconBox.addView(emojiTv);

        TextView titleTv = new TextView(this);
        titleTv.setText(title);
        titleTv.setTextSize(16f);
        titleTv.setTextColor(COLOR_WHITE);
        titleTv.setTypeface(null, Typeface.BOLD);
        titleTv.setPadding(dp(12), 0, 0, 0);

        header.addView(iconBox);
        header.addView(titleTv);
        return header;
    }

    private TextView makeDialogBtn(String label, int textColor, int bgColor) {
        TextView btn = new TextView(this);
        btn.setText(label);
        btn.setTextSize(13.5f);
        btn.setTextColor(textColor);
        btn.setTypeface(null, Typeface.BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(20), dp(12), dp(20), dp(12));
        btn.setClickable(true);
        btn.setFocusable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(dp(12));
        btn.setBackground(bg);
        return btn;
    }

    private EditText makePasswordField(String hint) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setHintTextColor(0xFFBBF7D0);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        et.setTextSize(14f);
        et.setTextColor(0xFF111827);
        et.setPadding(dp(14), dp(14), dp(14), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFF0FDF4);
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), COLOR_DIVIDER);
        et.setBackground(bg);
        return et;
    }

    private AlertDialog buildStyledDialog(View content) {
        AlertDialog dialog = new AlertDialog.Builder(this).setView(content).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            GradientDrawable dialogBg = new GradientDrawable();
            dialogBg.setColor(COLOR_WHITE);
            dialogBg.setCornerRadius(dp(20));
            dialog.getWindow().getDecorView().setBackground(dialogBg);
        }
        return dialog;
    }

    private View makeHSpacer(int widthPx) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(widthPx, 0));
        return v;
    }

    private View makeVSpacer(int heightPx) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, heightPx));
        return v;
    }

    private void shake(View view) {
        android.view.animation.TranslateAnimation anim =
                new android.view.animation.TranslateAnimation(-10, 10, 0, 0);
        anim.setDuration(80);
        anim.setRepeatMode(android.view.animation.Animation.REVERSE);
        anim.setRepeatCount(4);
        view.startAnimation(anim);
    }

    private int dp(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  Classification helpers
    // ════════════════════════════════════════════════════════════════════════════

    private String classifyBmi(float b) {
        if (b < 18.5f) return "Thiếu cân";
        if (b < 25f)   return "Bình thường";
        if (b < 30f)   return "Thừa cân";
        return "Béo phì";
    }

    private String waterStatus(float ml) {
        if (ml < 1500)  return "Chưa đủ";
        if (ml <= 3000) return "Tốt – đạt chuẩn";
        return "Quá mức";
    }

    private String sleepStatus(float h) {
        if (h < 6)  return "Thiếu ngủ";
        if (h <= 9) return "Đủ giấc";
        return "Ngủ nhiều";
    }

    private int bmiTextColor(float b) {
        if (b < 18.5f) return Color.parseColor("#0369A1");
        if (b < 25f)   return Color.parseColor("#2E8B57");
        if (b < 30f)   return Color.parseColor("#D97706");
        return Color.parseColor("#DC2626");
    }

    private String bmiCardBg(float b) {
        if (b < 18.5f) return "#E3F2FD";
        if (b < 25f)   return "#E8F5E9";
        if (b < 30f)   return "#FFFDE7";
        return "#FFEBEE";
    }

    private String bmiCardAccent(float b) {
        if (b < 18.5f) return "#1565C0";
        if (b < 25f)   return "#2E7D32";
        if (b < 30f)   return "#F57F17";
        return "#C62828";
    }

    private int waterTextColor(float ml) {
        if (ml < 1500)  return Color.parseColor("#DC2626");
        if (ml <= 3000) return Color.parseColor("#2E8B57");
        return Color.parseColor("#0369A1");
    }

    private int sleepTextColor(float h) {
        if (h < 6)  return Color.parseColor("#DC2626");
        if (h <= 9) return Color.parseColor("#2E8B57");
        return Color.parseColor("#D97706");
    }


    interface OnSaveListener {
        void onSave(String value);
    }
}