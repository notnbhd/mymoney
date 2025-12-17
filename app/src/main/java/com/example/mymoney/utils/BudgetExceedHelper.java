package com.example.mymoney.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AlertDialog;

import com.example.mymoney.database.AppDatabase;
import com.example.mymoney.database.dao.BudgetDao;
import com.example.mymoney.database.dao.CategoryDao;
import com.example.mymoney.database.dao.TransactionDao;
import com.example.mymoney.database.entity.Budget;
import com.example.mymoney.database.entity.Category;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;

/**
 * Helper class to check if a new expense will exceed any existing budget.
 * This check happens BEFORE saving the transaction, allowing user to confirm or dismiss.
 */
public class BudgetExceedHelper {
    
    private static final DecimalFormat df = new DecimalFormat("#,###");
    // Budget entity uses yyyy-MM-dd format
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    
    /**
     * Callback interface for budget exceed check result
     */
    public interface BudgetCheckCallback {
        void onProceed();  // User confirms to proceed with saving
        void onDismiss();  // User dismisses the expense
    }
    
    /**
     * Result class containing exceeded budget info
     */
    public static class BudgetExceedInfo {
        public String budgetName;
        public String categoryName;
        public double currentSpent;
        public double budgetLimit;
        public double newTotal;
        public String source; // "database" or "saving_goal"
        
        public BudgetExceedInfo(String budgetName, String categoryName, 
                                double currentSpent, double budgetLimit, 
                                double newTotal, String source) {
            this.budgetName = budgetName;
            this.categoryName = categoryName;
            this.currentSpent = currentSpent;
            this.budgetLimit = budgetLimit;
            this.newTotal = newTotal;
            this.source = source;
        }
    }
    
    /**
     * Check if the expense will exceed any budget and show confirmation dialog if needed.
     * This method checks both:
     * 1. Database Budget entities
     * 2. Saving Goals from SharedPreferences
     * 
     * @param context Application context
     * @param categoryId Category ID of the expense
     * @param amount Amount of the expense
     * @param walletId Wallet ID for the expense
     * @param userId User ID
     * @param callback Callback to handle user's decision
     */
    public static void checkAndConfirm(Context context, int categoryId, double amount, 
                                        int walletId, int userId, BudgetCheckCallback callback) {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<BudgetExceedInfo> exceededBudgets = checkBudgets(context, categoryId, amount, walletId, userId);
            
            new Handler(Looper.getMainLooper()).post(() -> {
                if (exceededBudgets.isEmpty()) {
                    // No budget exceeded, proceed directly
                    callback.onProceed();
                } else {
                    // Show confirmation dialog
                    showExceedConfirmationDialog(context, exceededBudgets, amount, callback);
                }
            });
        });
    }
    
    /**
     * Check budgets without showing dialog - returns list of exceeded budgets
     * This is for batch processing where you want to check all at once
     */
    public static List<BudgetExceedInfo> checkBudgetsSync(Context context, int categoryId, 
                                                          double amount, int walletId, int userId) {
        return checkBudgets(context, categoryId, amount, walletId, userId);
    }
    
    private static List<BudgetExceedInfo> checkBudgets(Context context, int categoryId, 
                                                        double amount, int walletId, int userId) {
        List<BudgetExceedInfo> exceededBudgets = new ArrayList<>();
        
        AppDatabase db = AppDatabase.getInstance(context);
        BudgetDao budgetDao = db.budgetDao();
        TransactionDao transactionDao = db.transactionDao();
        CategoryDao categoryDao = db.categoryDao();
        
        // Get category name
        Category category = categoryDao.getCategoryById(categoryId);
        String categoryName = category != null ? category.getName() : "Unknown";
        
        // =====================================================
        // 1. Check Database Budget entities
        // =====================================================
        List<Budget> budgets = budgetDao.getBudgetsByCategoryId(categoryId);
        
        for (Budget budget : budgets) {
            // Check if budget is for the same wallet
            if (budget.getWalletId() != walletId) continue;
            
            // Check if budget is still valid (within date range)
            if (!isBudgetActive(budget)) continue;
            
            // Calculate current spending for this budget
            double currentSpent = calculateCurrentSpent(transactionDao, budget, categoryId, walletId);
            double newTotal = currentSpent + amount;
            
            if (newTotal > budget.getBudgetAmount()) {
                exceededBudgets.add(new BudgetExceedInfo(
                    budget.getName(),
                    categoryName,
                    currentSpent,
                    budget.getBudgetAmount(),
                    newTotal,
                    "database"
                ));
            }
        }
        
        // =====================================================
        // 2. Check Saving Goals from SharedPreferences
        // =====================================================
        checkSavingGoalBudgets(context, categoryName, amount, userId, transactionDao, exceededBudgets);
        
        return exceededBudgets;
    }
    
    private static boolean isBudgetActive(Budget budget) {
        try {
            long now = System.currentTimeMillis();
            
            // Parse start and end dates
            String startDateStr = budget.getStartDate();
            String endDateStr = budget.getEndDate();
            
            if (startDateStr == null || endDateStr == null) {
                return true; // No date limits, always active
            }
            
            java.util.Date startDate = dateFormat.parse(startDateStr);
            java.util.Date endDate = dateFormat.parse(endDateStr);
            
            if (startDate == null || endDate == null) return true;
            
            return now >= startDate.getTime() && now <= endDate.getTime();
        } catch (Exception e) {
            return true; // If parsing fails, assume budget is active
        }
    }
    
    private static double calculateCurrentSpent(TransactionDao transactionDao, Budget budget, 
                                                 int categoryId, int walletId) {
        try {
            String startDateStr = budget.getStartDate();
            String endDateStr = budget.getEndDate();
            
            if (startDateStr == null || endDateStr == null) {
                // No date limits, use current month
                Calendar cal = Calendar.getInstance();
                cal.set(Calendar.DAY_OF_MONTH, 1);
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                long startOfMonth = cal.getTimeInMillis();
                
                return transactionDao.getTotalExpenseBetweenForWalletAndCategory(
                    startOfMonth, System.currentTimeMillis(), walletId, categoryId
                );
            }
            
            java.util.Date startDate = dateFormat.parse(startDateStr);
            java.util.Date endDate = dateFormat.parse(endDateStr);
            
            if (startDate == null || endDate == null) return 0;
            
            // Extend end date to include the whole day (23:59:59.999)
            Calendar endCal = Calendar.getInstance();
            endCal.setTime(endDate);
            endCal.set(Calendar.HOUR_OF_DAY, 23);
            endCal.set(Calendar.MINUTE, 59);
            endCal.set(Calendar.SECOND, 59);
            endCal.set(Calendar.MILLISECOND, 999);
            
            return transactionDao.getTotalExpenseBetweenForWalletAndCategory(
                startDate.getTime(), endCal.getTimeInMillis(), walletId, categoryId
            );
        } catch (Exception e) {
            android.util.Log.e("BudgetExceedHelper", "Error calculating spent: " + e.getMessage());
            return 0;
        }
    }
    
    private static void checkSavingGoalBudgets(Context context, String categoryName, double amount,
                                                int userId, TransactionDao transactionDao,
                                                List<BudgetExceedInfo> exceededBudgets) {
        SharedPreferences budgetPrefs = context.getSharedPreferences("budget_prefs", Context.MODE_PRIVATE);
        SharedPreferences savingPrefs = context.getSharedPreferences("SAVING_GOALS", Context.MODE_PRIVATE);
        
        Set<String> goalSet = savingPrefs.getStringSet("goal_list", new HashSet<>());
        if (goalSet == null || goalSet.isEmpty()) return;
        
        for (String item : goalSet) {
            String[] arr = item.split("\\|");
            if (arr.length < 1) continue;
            
            String goalName = arr[0].trim();
            
            // Check if saving is active
            boolean isSaving = budgetPrefs.getBoolean(goalName + "_isSaving", false);
            if (!isSaving) continue;
            
            // Get limit for this category
            long limit = budgetPrefs.getLong(goalName + "_limit_" + categoryName, 0);
            if (limit <= 0) continue;
            
            // Get start time of the goal
            long startTime = budgetPrefs.getLong(goalName + "_start", -1);
            if (startTime <= 0) continue;
            
            // Calculate current spent
            double currentSpent = transactionDao.getTotalExpenseByCategorySinceForUser(
                categoryName, startTime, userId
            );
            
            double newTotal = currentSpent + amount;
            
            if (newTotal > limit) {
                exceededBudgets.add(new BudgetExceedInfo(
                    "Mục tiêu: " + goalName,
                    categoryName,
                    currentSpent,
                    limit,
                    newTotal,
                    "saving_goal"
                ));
            }
        }
    }
    
    private static void showExceedConfirmationDialog(Context context, 
                                                      List<BudgetExceedInfo> exceededBudgets,
                                                      double expenseAmount,
                                                      BudgetCheckCallback callback) {
        StringBuilder message = new StringBuilder();
        message.append("Khoản chi tiêu ").append(df.format(expenseAmount)).append(" VND ");
        message.append("sẽ vượt ngân sách!\n\n");
        
        for (BudgetExceedInfo info : exceededBudgets) {
            message.append("📊 ").append(info.budgetName).append("\n");
            message.append("   • Danh mục: ").append(info.categoryName).append("\n");
            message.append("   • Đã chi: ").append(df.format(info.currentSpent)).append(" VND\n");
            message.append("   • Giới hạn: ").append(df.format(info.budgetLimit)).append(" VND\n");
            message.append("   • Tổng mới: ").append(df.format(info.newTotal)).append(" VND");
            
            double overAmount = info.newTotal - info.budgetLimit;
            message.append(" (vượt ").append(df.format(overAmount)).append(" VND)\n\n");
        }
        
        message.append("Bạn có muốn tiếp tục lưu khoản chi tiêu này không?");
        
        new AlertDialog.Builder(context)
            .setTitle("⚠️ Vượt ngân sách")
            .setMessage(message.toString())
            .setPositiveButton("Tiếp tục lưu", (dialog, which) -> {
                callback.onProceed();
            })
            .setNegativeButton("Hủy bỏ", (dialog, which) -> {
                callback.onDismiss();
            })
            .setCancelable(false)
            .show();
    }
    
    /**
     * Show batch exceed confirmation dialog for multiple transactions
     */
    public static void showBatchExceedConfirmationDialog(Context context,
                                                          List<BatchExceedItem> exceedItems,
                                                          BatchBudgetCheckCallback callback) {
        if (exceedItems.isEmpty()) {
            callback.onProceedAll();
            return;
        }
        
        StringBuilder message = new StringBuilder();
        message.append("Một số khoản chi tiêu sẽ vượt ngân sách:\n\n");
        
        for (BatchExceedItem item : exceedItems) {
            message.append("💰 ").append(df.format(item.amount)).append(" VND - ");
            message.append(item.categoryName).append("\n");
            for (BudgetExceedInfo info : item.exceededBudgets) {
                message.append("   ⚠ Vượt ").append(info.budgetName);
                double over = info.newTotal - info.budgetLimit;
                message.append(" (").append(df.format(over)).append(" VND)\n");
            }
            message.append("\n");
        }
        
        message.append("Bạn muốn xử lý như thế nào?");
        
        new AlertDialog.Builder(context)
            .setTitle("⚠️ Nhiều khoản vượt ngân sách")
            .setMessage(message.toString())
            .setPositiveButton("Lưu tất cả", (dialog, which) -> callback.onProceedAll())
            .setNeutralButton("Bỏ qua vượt mức", (dialog, which) -> callback.onSkipExceeded())
            .setNegativeButton("Hủy tất cả", (dialog, which) -> callback.onDismissAll())
            .setCancelable(false)
            .show();
    }
    
    /**
     * Batch item containing transaction info and exceeded budgets
     */
    public static class BatchExceedItem {
        public int index;
        public String categoryName;
        public double amount;
        public List<BudgetExceedInfo> exceededBudgets;
        
        public BatchExceedItem(int index, String categoryName, double amount, 
                               List<BudgetExceedInfo> exceededBudgets) {
            this.index = index;
            this.categoryName = categoryName;
            this.amount = amount;
            this.exceededBudgets = exceededBudgets;
        }
    }
    
    /**
     * Callback for batch budget check
     */
    public interface BatchBudgetCheckCallback {
        void onProceedAll();      // Save all transactions
        void onSkipExceeded();    // Skip transactions that exceed budget
        void onDismissAll();      // Cancel all transactions
    }
}
