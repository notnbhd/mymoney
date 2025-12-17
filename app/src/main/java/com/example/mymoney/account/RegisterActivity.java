package com.example.mymoney.account;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mymoney.LoginActivity;
import com.example.mymoney.R;
import com.example.mymoney.database.AppDatabase;
import com.example.mymoney.database.entity.User;

public class RegisterActivity extends AppCompatActivity {

    EditText edtFullName, edtEmail, edtUsername, edtPassword;
    RadioGroup radioGroupGender;
    RadioButton rbMale, rbFemale;
    Button btnRegister;
    TextView tvLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Ánh xạ view
        edtFullName = findViewById(R.id.edtFullName);
        edtEmail = findViewById(R.id.edtEmail);
        edtUsername = findViewById(R.id.edtUsername);
        edtPassword = findViewById(R.id.edtPassword);
        radioGroupGender = findViewById(R.id.radioGroupGender);
        rbMale = findViewById(R.id.rbMale);
        rbFemale = findViewById(R.id.rbFemale);
        btnRegister = findViewById(R.id.btnRegister);
        tvLogin = findViewById(R.id.tvLogin);

        // Xử lý nút Register
        btnRegister.setOnClickListener(v -> {
            String fullName = edtFullName.getText().toString().trim();
            String email = edtEmail.getText().toString().trim();
            String username = edtUsername.getText().toString().trim();
            String password = edtPassword.getText().toString().trim();
            String gender = rbMale.isChecked() ? "Male" : rbFemale.isChecked() ? "Female" : "";

            // Kiểm tra nhập đủ
            if (fullName.isEmpty() || email.isEmpty() || username.isEmpty() || password.isEmpty() || gender.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập đầy đủ thông tin", Toast.LENGTH_SHORT).show();
                return;
            }

            // Kiểm tra email hợp lệ
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Email không hợp lệ", Toast.LENGTH_SHORT).show();
                return;
            }

            // Xử lý trong thread riêng
            new Thread(() -> {
                AppDatabase db = AppDatabase.getInstance(this);
                User existingUser = db.userDao().getUserByUsername(username);

                runOnUiThread(() -> {
                    if (existingUser != null) {
                        Toast.makeText(this, "Tên đăng nhập đã tồn tại!", Toast.LENGTH_SHORT).show();
                    } else {
                        new Thread(() -> {
                            // Tạo người dùng mới
                            User newUser = new User();
                            newUser.setUsername(username);
                            newUser.setPassword(password);
                            newUser.setEmail(email);
                            newUser.setFullName(fullName);
                            newUser.setGender(gender);
                            newUser.setCreatedAt(System.currentTimeMillis());

                            long userId = db.userDao().insert(newUser);

                            runOnUiThread(() -> {
                                if (userId > 0) {
                                    // ✅ Lưu đầy đủ thông tin vào SharedPreferences
                                    SharedPreferences prefs = getSharedPreferences("UserData", MODE_PRIVATE);
                                    SharedPreferences.Editor editor = prefs.edit();
                                    editor.putString("fullName", fullName);
                                    editor.putString("email", email);
                                    editor.putString("username", username);
                                    editor.putString("password", password);
                                    editor.putString("gender", gender);

                                    // ✅ Thêm các giá trị mặc định để tránh hiển thị "(not set)"
                                    editor.putString("phone", "(not set)");
                                    editor.putString("dob", "(not set)");
                                    editor.putString("job", "(not set)");
                                    editor.putString("address", "(not set)");
                                    editor.apply();

                                    Toast.makeText(this, "Đăng ký thành công!", Toast.LENGTH_SHORT).show();

                                    // Chuyển đến LoginActivity
                                    startActivity(new Intent(this, LoginActivity.class));
                                    finish();
                                } else {
                                    Toast.makeText(this, "Đăng ký thất bại. Vui lòng thử lại!", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }).start();
                    }
                });
            }).start();
        });

        // Chuyển sang LoginActivity
        tvLogin.setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });
    }
}
