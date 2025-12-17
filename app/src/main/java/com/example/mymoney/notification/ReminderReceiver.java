package com.example.mymoney.notification;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.mymoney.MainActivity;
import com.example.mymoney.R;

public class ReminderReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        Log.e("REMINDER", "🔥 ReminderReceiver TRIGGERED");

        String channelId = "daily_notify";

        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (nm == null) {
            Log.e("REMINDER", "❌ NotificationManager null");
            return;
        }

        // ✅ Android 8+ cần channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Nhắc nhập chi tiêu",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Nhắc người dùng nhập chi tiêu mỗi ngày");
            nm.createNotificationChannel(channel);
        }

        // 👉 Click notification mở app
        Intent openApp = new Intent(context, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, channelId)
                        .setSmallIcon(R.drawable.ic_notification) // 👉 icon của bạn
                        .setContentTitle("⏰ Nhắc nhở chi tiêu")
                        .setContentText("Đừng quên nhập chi tiêu hôm nay nhé!")
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_HIGH);

        nm.notify(1002, builder.build());

        // ===============================
        // 🔁 QUAN TRỌNG: ĐẶT LẠI ALARM CHO NGÀY MAI
        // ===============================
        SharedPreferences prefs =
                context.getSharedPreferences("notify_prefs", Context.MODE_PRIVATE);

        int hour = prefs.getInt("hour", 21);
        int minute = prefs.getInt("minute", 0);

        NotificationScheduler.scheduleDaily(context, hour, minute);
    }
}