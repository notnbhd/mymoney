package com.example.mymoney;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.mymoney.database.AppDatabase;

import java.util.Calendar;

public class BudgetUtils {

    /**
     * LẤY TỔNG CHI TIÊU CỦA 1 CATEGORY (manual mode)
     */
    public static long getSpentForCategory(Context context, String categoryName, String goalName) {

        SharedPreferences prefs =
                context.getSharedPreferences("budget_prefs", Context.MODE_PRIVATE);

        // Lấy limit của danh mục
        long limit = prefs.getLong(goalName + "_limit_" + categoryName, 0);

        if (limit <= 0) return 0; // nếu không có limit thì coi như không tính

        // Lấy tổng chi trong tháng
        return (long) getTotalSpentThisMonthForCategory(context, categoryName);
    }

    /**
     * LẤY TỔNG CHI TIÊU AUTO MODE (tính tổng tất cả chi tiêu tháng này)
     */
    public static long getSpentAutoMode(Context context, String goalName) {

        Calendar c = Calendar.getInstance();
        c.set(Calendar.DAY_OF_MONTH, 1);
        long fromDate = c.getTimeInMillis();

        double spent = AppDatabase.getInstance(context)
                .transactionDao()
                .getTotalExpenseSince(fromDate);

        return (long) spent;
    }

    /**
     * Hàm tính tổng chi của 1 danh mục trong tháng hiện tại
     */
    private static double getTotalSpentThisMonthForCategory(Context context, String categoryName) {

        Calendar c = Calendar.getInstance();
        c.set(Calendar.DAY_OF_MONTH, 1);
        long fromDate = c.getTimeInMillis();

        return AppDatabase.getInstance(context)
                .transactionDao()
                .getTotalExpenseByCategorySince(categoryName, fromDate);
    }
}
