package com.example.udtddk.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.example.udtddk.R;
import com.example.udtddk.models.User;

import java.util.List;
import java.util.Locale;

public class AdminUserAdapter extends RecyclerView.Adapter<AdminUserAdapter.UserViewHolder> {

    private final List<User> userList;
    private final List<String> keyList;
    private final OnUserActionListener listener;
    private Button  btnDelete;

    public interface OnUserActionListener {
        void onDelete(String userKey, User user);
    }

    public AdminUserAdapter(List<User> userList, List<String> keyList, OnUserActionListener listener) {
        this.userList = userList;
        this.keyList = keyList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_admin_user, parent, false);
        return new UserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        User user = userList.get(position);
        String key = keyList.get(position);

        String name = user.getHoTen() != null ? user.getHoTen() : "Chưa có tên";
        String email = user.getEmail() != null ? user.getEmail() : "Không có email";
        String gender = user.getGioiTinh() != null ? user.getGioiTinh() : "Không rõ";
       // String bmiCategory = getBmiCategory(user.ge));

        holder.tvName.setText(name);
        holder.tvEmail.setText(email);
        holder.tvGender.setText("Giới tính: " + gender);
       // holder.tvBmi.setText("BMI: " + String.format(Locale.getDefault(), "%.1f", user.getBmi()) + " (" + bmiCategory + ")");

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDelete(key, user);
            }
        });
    }

    @Override
    public int getItemCount() {
        return userList.size();
    }

    private String getBmiCategory(double bmi) {
        if (bmi == 0) return "Chưa có dữ liệu";
        if (bmi < 18.5) return "Thiếu cân";
        if (bmi < 25) return "Bình thường";
        if (bmi < 30) return "Thừa cân";
        return "Béo phì";
    }

    public static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvEmail, tvGender, tvBmi;
        ImageView btnDelete;
        CardView cardUser;

        public UserViewHolder(@NonNull View itemView) {
            super(itemView);

            tvEmail = itemView.findViewById(R.id.textView6);
            tvGender = itemView.findViewById(R.id.tvGender);
            tvBmi = itemView.findViewById(R.id.tvBmi);
           // btnDelete = itemView.findViewById(R.id.btnDelete);
            cardUser = itemView.findViewById(R.id.cardUser);
        }
    }
}