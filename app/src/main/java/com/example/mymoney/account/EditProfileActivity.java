package com.example.mymoney.account;

import android.app.DatePickerDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mymoney.R;

import java.util.Calendar;

public class EditProfileActivity extends AppCompatActivity {

    private EditText edtFullName, edtEmail, edtPhone, edtDob, edtJob, edtAddress;
    private RadioGroup radioGroupGender;
    private RadioButton rbMale, rbFemale;
    private Button btnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        // Ánh xạ view
        edtFullName = findViewById(R.id.edtFullName);
        edtEmail = findViewById(R.id.edtEmail);
        edtPhone = findViewById(R.id.edtPhone);
        edtDob = findViewById(R.id.edtDob);
        edtJob = findViewById(R.id.edtJob);
        edtAddress = findViewById(R.id.edtAddress);
        radioGroupGender = findViewById(R.id.radioGroupGender);
        rbMale = findViewById(R.id.rbMale);
        rbFemale = findViewById(R.id.rbFemale);
        btnSave = findViewById(R.id.btnSave);

        // Lấy dữ liệu từ SharedPreferences
        SharedPreferences prefs = getSharedPreferences("UserData", MODE_PRIVATE);
        edtFullName.setText(prefs.getString("fullName", ""));
        edtEmail.setText(prefs.getString("email", ""));
        edtPhone.setText(prefs.getString("phone", ""));
        edtDob.setText(prefs.getString("dob", ""));
        edtJob.setText(prefs.getString("job", ""));
        edtAddress.setText(prefs.getString("address", ""));

        // Hiển thị giới tính đã lưu
        String gender = prefs.getString("gender", "");
        if (gender.equalsIgnoreCase("Male")) {
            rbMale.setChecked(true);
        } else if (gender.equalsIgnoreCase("Female")) {
            rbFemale.setChecked(true);
        }

        // Khi click vào ô DOB => mở lịch chọn ngày
        edtDob.setOnClickListener(v -> showDatePickerDialog());

        // Xử lý khi nhấn Save
        btnSave.setOnClickListener(v -> {
            String fullName = edtFullName.getText().toString().trim();
            String email = edtEmail.getText().toString().trim();
            String phone = edtPhone.getText().toString().trim();
            String dob = edtDob.getText().toString().trim();
            String job = edtJob.getText().toString().trim();
            String address = edtAddress.getText().toString().trim();

            // Xác định giới tính
            int selectedId = radioGroupGender.getCheckedRadioButtonId();
            String genderSelected = "";
            if (selectedId == R.id.rbMale) {
                genderSelected = "Male";
            } else if (selectedId == R.id.rbFemale) {
                genderSelected = "Female";
            }

            // Lưu dữ liệu lại vào SharedPreferences
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("fullName", fullName);
            editor.putString("email", email);
            editor.putString("phone", phone);
            editor.putString("dob", dob);
            editor.putString("job", job);
            editor.putString("address", address);
            editor.putString("gender", genderSelected);

            // ❗ Dùng commit() để đảm bảo lưu xong ngay lập tức
            editor.commit();

            // Thông báo
            Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_SHORT).show();

            // Gửi kết quả OK về cho AccountActivity để reload dữ liệu
            setResult(RESULT_OK);

            // Quay lại AccountActivity
            finish();
        });
    }

    // Hàm hiển thị lịch chọn ngày
    private void showDatePickerDialog() {
        final Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, selectedYear, selectedMonth, selectedDay) -> {
                    String selectedDate = String.format("%02d/%02d/%04d",
                            selectedDay, selectedMonth + 1, selectedYear);
                    edtDob.setText(selectedDate);
                },
                year, month, day
        );
        dialog.show();
    }
}
