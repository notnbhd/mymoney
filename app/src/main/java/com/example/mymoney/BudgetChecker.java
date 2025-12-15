package com.example.mymoney;

import static com.example.mymoney.MainActivity.getCurrentUserId;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AlertDialog;

import com.example.mymoney.database.AppDatabase;
import com.example.mymoney.database.dao.TransactionDao;

import java.util.HashSet;
import java.util.Set;

public class BudgetChecker {

    public static void checkAllGoalsBackground(Context context, String category, double amount) {
        new Thread(() -> checkAllGoals(context, category, amount)).start();
    }

    private static void checkAllGoals(Context context, String category, double amount) {

        SharedPreferences prefs = context.getSharedPreferences("budget_prefs", Context.MODE_PRIVATE);
        SharedPreferences savingPrefs = context.getSharedPreferences("SAVING_GOALS", Context.MODE_PRIVATE);

        Set<String> set = savingPrefs.getStringSet("goal_list", new HashSet<>());
        if (set == null || set.isEmpty()) return;

        TransactionDao dao = AppDatabase.getInstance(context).transactionDao();

        for (String item : set) {
            String[] arr = item.split("\\|");
            if (arr.length < 1) continue;

            String goalName = arr[0].trim();
            String type = (arr.length > 3 && arr[3] != null) ? arr[3].trim() : "manual";

            if ("manual".equals(type)) {

                // ✅ KEY LIMIT ĐÚNG
                long limit = prefs.getLong(goalName + "_limit_" + category, 0);

                // ✅ Không check nếu chưa đặt limit
                if (limit <= 0) continue;

                // ✅ Lấy startTime của goal
                long start = prefs.getLong(goalName + "_start", -1);
                if (start <= 0) continue;

                // ✅ Tính spent từ lúc bắt đầu tiết kiệm (DAO đúng)
                double spent = dao.getTotalExpenseByCategorySince(category, start);

                long newTotal = Math.round(spent + amount);

                if (newTotal > limit) {
                    showWarningOnUI(context, goalName, category, newTotal, limit);
                }

            } else {
                // ✅ limit theo danh mục (auto mode bạn vẫn đang lưu _limit_Food,...)
                long limit = prefs.getLong(goalName + "_limit_" + category, 0);
                if (limit <= 0) continue;

                long start = prefs.getLong(goalName + "_start", -1);
                if (start <= 0) continue;

                // ✅ spent theo danh mục, từ lúc start (NHỚ lọc userId)
                int userId = getCurrentUserId();
                double spent = dao.getTotalExpenseByCategorySinceForUser(category, start, userId);

                long newTotal = Math.round(spent); // ✅ KHÔNG + amount nếu đã insert vào DB
                if (newTotal > limit) {
                    showWarningOnUI(context, goalName, category, newTotal, limit);
                }
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
