package com.example.mymoney.account;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.mymoney.R;

public class AccountActivity extends AppCompatActivity {

    private TextView tvName, tvEmailTop, tvFullName, tvGender, tvEmail, tvPhone, tvDOB, tvJob, tvAddress;
    private Button btnEdit;
    private ActivityResultLauncher<Intent> editProfileLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account);

        // Ánh xạ view
        tvName = findViewById(R.id.tvName);
        tvEmailTop = findViewById(R.id.tvEmailTop);
        tvFullName = findViewById(R.id.tvFullName);
        tvGender = findViewById(R.id.tvGender);
        tvEmail = findViewById(R.id.tvEmail);
        tvPhone = findViewById(R.id.tvPhone);
        tvDOB = findViewById(R.id.tvDOB);
        tvJob = findViewById(R.id.tvJob);
        tvAddress = findViewById(R.id.tvAddress);
        btnEdit = findViewById(R.id.btnEdit);

        // Tải dữ liệu ban đầu
        loadUserData();

        // Tạo launcher để nhận kết quả sau khi chỉnh sửa
        editProfileLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        // Khi EditProfileActivity bấm Save -> cập nhật lại
                        loadUserData();
                    }
                }
        );

        // Nút Edit mở EditProfileActivity
        btnEdit.setOnClickListener(v -> {
            Intent intent = new Intent(AccountActivity.this, EditProfileActivity.class);
            editProfileLauncher.launch(intent);
        });
    }

    // Hàm tải dữ liệu từ SharedPreferences và hiển thị
    private void loadUserData() {
        SharedPreferences prefs = getSharedPreferences("UserData", MODE_PRIVATE);

        String name = prefs.getString("fullName", "(not set)");
        String gender = prefs.getString("gender", "(not set)");
        String email = prefs.getString("email", "(not set)");
        String phone = prefs.getString("phone", "(not set)");
        String dob = prefs.getString("dob", "(not set)");
        String job = prefs.getString("job", "(not set)");
        String address = prefs.getString("address", "(not set)");

        tvName.setText(name);
        tvEmailTop.setText(email);
        tvFullName.setText("Fullname: " + name);
        tvGender.setText("Gender: " + gender);
        tvEmail.setText("Email: " + email);
        tvPhone.setText("Phone Number: " + phone);
        tvDOB.setText("Date of Birth: " + dob);
        tvJob.setText("Job: " + job);
        tvAddress.setText("Address: " + address);
    }
}
