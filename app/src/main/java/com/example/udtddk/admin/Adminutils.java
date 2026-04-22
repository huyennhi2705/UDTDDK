package com.example.udtddk.admin;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.udtddk.R;
import com.example.udtddk.adapter.AdminUserAdapter;
import com.example.udtddk.models.User;
import com.google.firebase.database.*;

import java.util.ArrayList;
import java.util.List;

public class Adminutils extends AppCompatActivity {

    private RecyclerView recyclerUsers;
    private EditText etSearch;
    private ImageView btnBack;

    private AdminUserAdapter adapter;
    private List<User> userList;
    private List<String> userKeyList;
    private List<User> filteredList;
    private List<String> filteredKeyList;

    private DatabaseReference userRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_adminutils);

        recyclerUsers = findViewById(R.id.recyclerUsers);
        etSearch = findViewById(R.id.etSearch);
        btnBack = findViewById(R.id.btnBack);

        recyclerUsers.setLayoutManager(new LinearLayoutManager(this));

        userList = new ArrayList<>();
        userKeyList = new ArrayList<>();
        filteredList = new ArrayList<>();
        filteredKeyList = new ArrayList<>();

        adapter = new AdminUserAdapter(filteredList, filteredKeyList, new AdminUserAdapter.OnUserActionListener() {
            @Override
            public void onDelete(String userKey, User user) {
                showDeleteDialog(userKey, user);
            }
        });

        recyclerUsers.setAdapter(adapter);

        FirebaseDatabase database = FirebaseDatabase.getInstance("https://udtddk-default-rtdb.firebaseio.com/");
        userRef = database.getReference("nguoi_dung");

        loadUsers();

        btnBack.setOnClickListener(v -> finish());

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterUsers(s.toString().trim());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void loadUsers() {
        userRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                userList.clear();
                userKeyList.clear();

                for (DataSnapshot data : snapshot.getChildren()) {
                    User user = data.getValue(User.class);
                    String key = data.getKey();

                    if (user != null && key != null) {
                        userList.add(user);
                        userKeyList.add(key);
                    }
                }

                filterUsers(etSearch.getText().toString().trim());
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(Adminutils.this, "Không tải được danh sách user", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void filterUsers(String keyword) {
        filteredList.clear();
        filteredKeyList.clear();

        for (int i = 0; i < userList.size(); i++) {
            User user = userList.get(i);
            String key = userKeyList.get(i);

            String name = user.getHoTen()!= null ? user.getHoTen().toLowerCase() : "";
            String email = user.getEmail() != null ? user.getEmail().toLowerCase() : "";

            if (keyword.isEmpty() || name.contains(keyword.toLowerCase()) || email.contains(keyword.toLowerCase())) {
                filteredList.add(user);
                filteredKeyList.add(key);
            }
        }

        adapter.notifyDataSetChanged();
    }

    private void showDeleteDialog(String userKey, User user) {
        String displayName = user.getHoTen() != null ? user.getHoTen() : user.getEmail();

        new AlertDialog.Builder(this)
                .setTitle("Xóa người dùng")
                .setMessage("Bạn có chắc muốn xóa:\n" + displayName + " ?")
                .setPositiveButton("Xóa", (dialog, which) -> {
                    userRef.child(userKey).removeValue()
                            .addOnSuccessListener(unused ->
                                    Toast.makeText(this, "Đã xóa người dùng", Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e ->
                                    Toast.makeText(this, "Xóa thất bại", Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("Hủy", null)
                .show();
    }
}