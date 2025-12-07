package com.example.mymoney;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AlertDialog;

import java.util.HashSet;

public class BudgetChecker {

    /**
     * GỌI HÀM NÀY từ ImportFragment (CHẠY NỀN, KHÔNG CRASH)
     */
    public static void checkAllGoalsBackground(Context context, String category, double amount) {
        new Thread(() -> checkAllGoals(context, category, amount)).start();
    }

    /**
     * HÀM NÀY CHẠY TRONG BACKGROUND THREAD
     */
    private static void checkAllGoals(Context context, String category, double amount) {

        SharedPreferences prefs = context.getSharedPreferences("budget_prefs", Context.MODE_PRIVATE);
        SharedPreferences savingPrefs = context.getSharedPreferences("SAVING_GOALS", Context.MODE_PRIVATE);

        var set = savingPrefs.getStringSet("goal_list", new HashSet<>());

        for (String item : set) {

            String[] arr = item.split("\\|");
            String goalName = arr[0];
            String type = arr.length > 3 ? arr[3] : "manual";

            if (type.equals("manual")) {

                long limit = prefs.getLong(goalName + "_" + category + "_limit", 0);
                long spent = BudgetUtils.getSpentForCategory(context, category, goalName);
                long newTotal = Math.round(spent + amount);

                if (newTotal > limit) {
                    showWarningOnUI(context, goalName, category, newTotal, limit);
                }

            } else {

                long limit = prefs.getLong(goalName + "_maxExpensePerMonth", 0);
                long spent = BudgetUtils.getSpentAutoMode(context, goalName);
                long newTotal = Math.round(spent + amount);

                if (newTotal > limit) {
                    showWarningOnUI(context, goalName, "Tổng chi tiêu", newTotal, limit);
                }
            }
        }
    }

    /**
     * 🟢 SHOW ALERT TRÊN UI THREAD — AN TOÀN
     */
    private static void showWarningOnUI(Context context, String goalName,
                                        String category, long spent, long limit) {

        android.os.Handler handler = new android.os.Handler(context.getMainLooper());
        handler.post(() -> {
            new AlertDialog.Builder(context)
                    .setTitle("⚠ Chi tiêu vượt mức!")
                    .setMessage(
                            "Mục tiết kiệm: " + goalName +
                                    "\nDanh mục: " + category +
                                    "\nĐã tiêu: " + spent + " VND" +
                                    "\nGiới hạn: " + limit + " VND")
                    .setPositiveButton("OK", null)
                    .show();
        });
    }
}
