package com.example.mymoney.savingGoal;

import static com.example.mymoney.MainActivity.getCurrentUserId;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AlertDialog;

import com.example.mymoney.database.AppDatabase;
import com.example.mymoney.database.dao.TransactionDao;

import java.util.HashSet;
import java.util.Set;

public class SavingGoalChecker {

    public static void checkAllGoalsBackground(Context context, String category, double amount) {
        new Thread(() -> checkAllGoals(context, category, amount)).start();
    }

    private static void checkAllGoals(Context context, String category, double amount) {

        SharedPreferences prefs = context.getSharedPreferences("budget_prefs", Context.MODE_PRIVATE);
        SharedPreferences savingPrefs = context.getSharedPreferences("SAVING_GOALS", Context.MODE_PRIVATE);

        Set<String> set = savingPrefs.getStringSet("goal_list", new HashSet<>());
        if (set == null || set.isEmpty()) return;

        TransactionDao dao = AppDatabase.getInstance(context).transactionDao();
        boolean warned = false;


        for (String item : set) {

            if (warned) break; // ⛔ đã cảnh báo thì dừng luôn

            String[] arr = item.split("\\|");
            if (arr.length < 1) continue;

            String goalName = arr[0].trim();
            String type = (arr.length > 3 && arr[3] != null) ? arr[3].trim() : "manual";

            long limit = prefs.getLong(goalName + "_limit_" + category, 0);
            if (limit <= 0) continue;

            long start = prefs.getLong(goalName + "_start", -1);
            if (start <= 0) continue;

            double spent;

            if ("manual".equals(type)) {
                spent = dao.getTotalExpenseByCategorySince(category, start);
                // ❌ KHÔNG + amount nữa
            } else {
                int userId = getCurrentUserId();
                spent = dao.getTotalExpenseByCategorySinceForUser(category, start, userId);
            }

            if (spent > limit) {
                warned = true;
                showWarningOnUI(context, goalName, category, Math.round(spent), limit);
            }
        }

    }

    private static void showWarningOnUI(Context context, String goalName,
                                        String category, long spent, long limit) {

        android.os.Handler handler = new android.os.Handler(context.getMainLooper());
        handler.post(() -> {
            new AlertDialog.Builder(context)
                    .setTitle("⚠ Chi tiêu vượt mức!")
                    .setMessage(
                            "Mục tiết kiệm: " + goalName +
                                    "\nDanh mục: " + category +
                                    "\nĐã tiêu (từ lúc bắt đầu tiết kiệm): " + spent + " VND" +
                                    "\nGiới hạn: " + limit + " VND")
                    .setPositiveButton("OK", null)
                    .show();
        });
    }
}