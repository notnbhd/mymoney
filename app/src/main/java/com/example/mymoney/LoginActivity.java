package com.example.mymoney;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.example.mymoney.account.RegisterActivity;
import com.example.mymoney.database.AppDatabase;
import com.example.mymoney.database.entity.User;

public class LoginActivity extends AppCompatActivity {

    EditText edtUsername, edtPassword;
    Button btnLogin;
    TextView tvRegister;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        edtUsername = findViewById(R.id.edtUsername);
        edtPassword = findViewById(R.id.edtPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvRegister = findViewById(R.id.tvRegister);

        btnLogin.setOnClickListener(v -> {
            String username = edtUsername.getText().toString().trim();
            String password = edtPassword.getText().toString().trim();

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập đầy đủ thông tin", Toast.LENGTH_SHORT).show();
                return;
            }

            // Kiểm tra đăng nhập trong thread riêng
            new Thread(() -> {
                AppDatabase db = AppDatabase.getInstance(this);
                User user = db.userDao().login(username, password);

                runOnUiThread(() -> {
                    if (user != null) {
                        // ✅ Lưu thông tin đăng nhập (session)
                        SharedPreferences session = getSharedPreferences("MyMoneyPrefs", MODE_PRIVATE);
                        session.edit()
                                .putInt("userId", user.getId())
                                .putString("username", user.getUsername())
                                .putBoolean("isLoggedIn", true)
                                .apply();

                        // ✅ Lưu thông tin người dùng để hiển thị ở AccountActivity
                        SharedPreferences prefs = getSharedPreferences("UserData", MODE_PRIVATE);
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putString("fullName", user.getFullName() == null ? "(not set)" : user.getFullName());
                        editor.putString("email", user.getEmail() == null ? "(not set)" : user.getEmail());
                        editor.putString("username", user.getUsername());
                        editor.putString("gender", user.getGender() == null ? "(not set)" : user.getGender());
                        editor.putString("phone", user.getPhone() == null ? "(not set)" : user.getPhone());
                        editor.putString("dob", user.getDob() == null ? "(not set)" : user.getDob());
                        editor.putString("job", user.getJob() == null ? "(not set)" : user.getJob());
                        editor.putString("address", user.getAddress() == null ? "(not set)" : user.getAddress());
                        editor.apply();

                        Toast.makeText(this, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show();

                        // Chuyển đến MainActivity
                        startActivity(new Intent(this, MainActivity.class));
                        finish();
                    } else {
                        Toast.makeText(this, "Sai tên đăng nhập hoặc mật khẩu", Toast.LENGTH_SHORT).show();
                    }
                });
            }).start();
        });

        // Chuyển đến trang đăng ký
        tvRegister.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
        });
    }
}
