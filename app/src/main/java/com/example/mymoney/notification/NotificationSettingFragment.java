package com.example.mymoney.notification;

import android.app.TimePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.mymoney.R;

import java.util.Calendar;

public class NotificationSettingFragment extends Fragment {

    private TextView tvTime;
    private Button btnPickTime, btnSave;

    private int hour = 21;
    private int minute = 0;

    private SharedPreferences prefs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_notification_setting, container, false);

        tvTime = view.findViewById(R.id.tvTime);
        btnPickTime = view.findViewById(R.id.btnPickTime);
        btnSave = view.findViewById(R.id.btnSaveNotify);

        prefs = requireContext().getSharedPreferences("notify_prefs", Context.MODE_PRIVATE);

        // load time đã lưu
        hour = prefs.getInt("hour", 21);
        minute = prefs.getInt("minute", 0);
        updateTimeText();

        btnPickTime.setOnClickListener(v -> openTimePicker());
        btnSave.setOnClickListener(v -> saveAndSchedule());

        requestNotificationPermissionIfNeeded();
        return view;
    }

    private void openTimePicker() {
        TimePickerDialog dialog = new TimePickerDialog(
                requireContext(),
                (TimePicker view, int h, int m) -> {
                    hour = h;
                    minute = m;
                    updateTimeText();
                },
                hour,
                minute,
                true
        );
        dialog.show();
    }

    private void updateTimeText() {
        tvTime.setText(String.format("%02d:%02d", hour, minute));
    }

    private void saveAndSchedule() {
        prefs.edit()
                .putInt("hour", hour)
                .putInt("minute", minute)
                .apply();

        NotificationScheduler.scheduleDaily(
                requireContext(),
                hour,
                minute
        );

        Toast.makeText(getContext(),
                "Đã đặt nhắc nhở lúc " + tvTime.getText(),
                Toast.LENGTH_SHORT).show();
    }
    private void requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (requireContext().checkSelfPermission(
                    android.Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED) {

                requestPermissions(
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                        1001
                );
            }
        }
    }


}