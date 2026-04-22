package com.example.udtddk.admin;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.udtddk.R;
import com.example.udtddk.models.Workout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.database.*;

import java.util.*;

public class Adminworkoutsactivity extends AppCompatActivity {

    private static final String DB_URL = "https://udtddk-default-rtdb.firebaseio.com/";

    private static final String[] BMI_CATEGORIES =
            {"Tất cả", "Thiếu cân", "Bình thường", "Thừa cân", "Béo phì"};
    private static final String[] BMI_OPTIONS =
            {"Thiếu cân", "Bình thường", "Thừa cân", "Béo phì"};
    private static final String[] LEVEL_OPTIONS =
            {"Dễ", "Trung bình", "Khó"};

    private ImageView btnBack;
    private TextView tvWorkoutCount;
    private EditText etSearch;
    private Spinner spinnerFilter;
    private LinearLayout listContainer;
    private FloatingActionButton btnAddWorkout;

    private DatabaseReference workoutsRef;

    private final List<Workout> workoutList = new ArrayList<>();
    private final List<Workout> filteredList = new ArrayList<>();
    private final HashMap<String, Workout> keyMap = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_adminworkoutsactivity);

        btnBack = findViewById(R.id.btnBack);
        tvWorkoutCount = findViewById(R.id.tvWorkoutCount);
        etSearch = findViewById(R.id.etSearch);
        spinnerFilter = findViewById(R.id.spinnerFilter);
        listContainer = findViewById(R.id.listContainer);
        btnAddWorkout = findViewById(R.id.btnAddWorkout);

        workoutsRef = FirebaseDatabase.getInstance(DB_URL).getReference("BaiTap");

        setupSpinner();
        setupEvents();
        loadWorkouts();
    }

    private void setupSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, BMI_CATEGORIES);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFilter.setAdapter(adapter);

        spinnerFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onNothingSelected(AdapterView<?> parent) {}
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                applyFilter();
            }
        });
    }

    private void setupEvents() {
        btnBack.setOnClickListener(v -> finish());
        btnAddWorkout.setOnClickListener(v -> showAddDialog());

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                applyFilter();
            }
        });
    }

    // ================= LOAD =================
    private void loadWorkouts() {
        workoutsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                workoutList.clear();
                keyMap.clear();

                for (DataSnapshot ds : snapshot.getChildren()) {

                    // 🔥 Bỏ qua dữ liệu schema (varchar, integer...)
                    if (!(ds.getValue() instanceof Map)) continue;

                    try {
                        Workout w = ds.getValue(Workout.class);

                        if (w != null && !safe(w.getTenBaiTap()).isEmpty()) {
                            workoutList.add(w);
                            keyMap.put(ds.getKey(), w);
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                tvWorkoutCount.setText("Tổng bài tập: " + workoutList.size());
                applyFilter();
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(Adminworkoutsactivity.this,
                        "Lỗi: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ================= FILTER =================
    private void applyFilter() {
        String keyword = etSearch.getText().toString().toLowerCase().trim();
        String category = (String) spinnerFilter.getSelectedItem();

        filteredList.clear();

        for (Workout w : workoutList) {
            boolean matchCat = category.equals("Tất cả") ||
                    category.equals(safe(w.getPhanLoaiTheoBMI()));

            boolean matchName = safe(w.getTenBaiTap())
                    .toLowerCase().contains(keyword);

            if (matchCat && matchName) {
                filteredList.add(w);
            }
        }

        displayWorkouts(filteredList);
    }

    // ================= DISPLAY =================
    private void displayWorkouts(List<Workout> list) {
        listContainer.removeAllViews();

        if (list.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("Không có bài tập");
            listContainer.addView(tv);
            return;
        }

        for (Workout w : list) {
            View item = LayoutInflater.from(this)
                    .inflate(R.layout.item_admin_workout, listContainer, false);

            ((TextView) item.findViewById(R.id.tvWorkoutName))
                    .setText(safe(w.getTenBaiTap()));

            ((TextView) item.findViewById(R.id.tvWorkoutCategory))
                    .setText(getBmiEmoji(w.getPhanLoaiTheoBMI()) + " " + safe(w.getPhanLoaiTheoBMI()));

            ((TextView) item.findViewById(R.id.tvWorkoutLevel))
                    .setText("Độ khó: " + safe(w.getMucDo()));

            ((TextView) item.findViewById(R.id.tvWorkoutDuration))
                    .setText("⏱ " + w.getThoiGianTap() + " phút");

            ((TextView) item.findViewById(R.id.tvWorkoutCalories))
                    .setText("🔥 " + w.getLuongCaloTieuHao() + " kcal");

            ((TextView) item.findViewById(R.id.tvWorkoutDesc))
                    .setText(safe(w.getMoTa()));

            item.findViewById(R.id.btnEdit).setOnClickListener(v -> showEditDialog(w));
            item.findViewById(R.id.btnDelete).setOnClickListener(v -> deleteWorkout(w));

            listContainer.addView(item);
        }
    }


    private void showAddDialog() {
        View dv = LayoutInflater.from(this)
                .inflate(R.layout.dialog_add_edit_workout, null);

        fillSpinners(dv, null);

        new AlertDialog.Builder(this)
                .setTitle("Thêm bài tập")
                .setView(dv)
                .setPositiveButton("Thêm", (d, w) -> {
                    Workout workout = readDialog(dv);
                    if (workout == null) return;

                    String key = workoutsRef.push().getKey();
                    workoutsRef.child(key).setValue(workout);
                })
                .setNegativeButton("Hủy", null)
                .show();
    }


    private void showEditDialog(Workout w) {
        String key = findKey(w);
        if (key == null) return;

        View dv = LayoutInflater.from(this)
                .inflate(R.layout.dialog_add_edit_workout, null);

        fillSpinners(dv, w);

        ((EditText) dv.findViewById(R.id.etWorkoutName)).setText(safe(w.getTenBaiTap()));
        ((EditText) dv.findViewById(R.id.etWorkoutDesc)).setText(safe(w.getMoTa()));
        ((EditText) dv.findViewById(R.id.etDuration)).setText(String.valueOf(w.getThoiGianTap()));
        ((EditText) dv.findViewById(R.id.etCalories)).setText(String.valueOf(w.getLuongCaloTieuHao()));
        ((EditText) dv.findViewById(R.id.etVideoUrl)).setText(safe(w.getDuongDanVideo()));

        new AlertDialog.Builder(this)
                .setTitle("Sửa bài tập")
                .setView(dv)
                .setPositiveButton("Lưu", (d, i) -> {
                    Workout updated = readDialog(dv);
                    if (updated == null) return;

                    workoutsRef.child(key).setValue(updated);
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    // ================= DELETE =================
    private void deleteWorkout(Workout w) {
        String key = findKey(w);
        if (key == null) return;

        workoutsRef.child(key).removeValue();
    }


    private Workout readDialog(View dv) {
        String name = ((EditText) dv.findViewById(R.id.etWorkoutName)).getText().toString();
        String desc = ((EditText) dv.findViewById(R.id.etWorkoutDesc)).getText().toString();
        String video = ((EditText) dv.findViewById(R.id.etVideoUrl)).getText().toString();

        String bmi = (String) ((Spinner) dv.findViewById(R.id.spinnerBmiCategory)).getSelectedItem();
        String level = (String) ((Spinner) dv.findViewById(R.id.spinnerLevel)).getSelectedItem();

        int duration = parseInt(((EditText) dv.findViewById(R.id.etDuration)).getText().toString());
        int calories = parseInt(((EditText) dv.findViewById(R.id.etCalories)).getText().toString());

        if (name.isEmpty()) {
            Toast.makeText(this, "Nhập tên bài tập", Toast.LENGTH_SHORT).show();
            return null;
        }

        return new Workout(name, desc, bmi, level, duration, calories, video);
    }


    private void fillSpinners(View dv, Workout w) {
        Spinner spinBmi = dv.findViewById(R.id.spinnerBmiCategory);
        Spinner spinLevel = dv.findViewById(R.id.spinnerLevel);

        spinBmi.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, BMI_OPTIONS));

        spinLevel.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, LEVEL_OPTIONS));

        if (w != null) {
            setSpinner(spinBmi, BMI_OPTIONS, w.getPhanLoaiTheoBMI());
            setSpinner(spinLevel, LEVEL_OPTIONS, w.getMucDo());
        }
    }

    private void setSpinner(Spinner sp, String[] arr, String value) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i].equals(value)) {
                sp.setSelection(i);
                return;
            }
        }
    }


    private String findKey(Workout w) {
        for (Map.Entry<String, Workout> e : keyMap.entrySet()) {
            if (safe(e.getValue().getTenBaiTap())
                    .equals(safe(w.getTenBaiTap()))) {
                return e.getKey();
            }
        }
        return null;
    }

    private String getBmiEmoji(String cat) {
        switch (cat) {
            case "Thiếu cân": return "🔵";
            case "Bình thường": return "🟢";
            case "Thừa cân": return "🟡";
            case "Béo phì": return "🔴";
            default: return "⚪";
        }
    }

    private String safe(String v) { return v == null ? "" : v; }

    private int parseInt(String v) {
        try { return Integer.parseInt(v); }
        catch (Exception e) { return 0; }
    }
}