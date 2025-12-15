package com.example.mymoney.model;

import androidx.annotation.NonNull;

/**
 * Model dùng cho thống kê chi tiêu theo danh mục.
 * Dùng để nhận dữ liệu từ Room (Projection từ Transaction + Category).
 */
public class CategoryExpense {
    @NonNull
    public String category;

    public double total;

    public CategoryExpense(@NonNull String category, double total) {
        this.category = category;
        this.total = total;
    }

    @NonNull
    @Override
    public String toString() {
        return category + ": " + total;
    }
}
