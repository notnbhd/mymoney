package com.example.mymoney.rag;

import android.util.Log;

import com.example.mymoney.CategoryTotal;
import com.example.mymoney.database.AppDatabase;
import com.example.mymoney.database.entity.Budget;
import com.example.mymoney.database.entity.Category;
import com.example.mymoney.database.entity.Transaction;
import com.example.mymoney.database.entity.Wallet;
import com.example.mymoney.model.CategoryExpense;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * FinancialContextBuilder builds comprehensive financial context from user's data.
 * Provides rich context for RAG system including current period stats, comparisons, and trends.
 */
public class FinancialContextBuilder {
    private static final String TAG = "FinancialContextBuilder";
    
    private AppDatabase database;
    private SimpleDateFormat dateFormat;
    
    public FinancialContextBuilder(AppDatabase database) {
        this.database = database;
        this.dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
    }
    
    /**
     * Build complete financial context for a user and wallet.
     */
    public RAGContext buildContext(int userId, int walletId, String query) {
        RAGContext context = new RAGContext();
        context.setOriginalQuery(query);
        context.setDetectedLanguage(detectLanguage(query));
        
        try {
            // Build financial summary
            context.setFinancialSummary(buildFinancialSummary(userId, walletId));
            
            // Build budget context
            context.setBudgetContext(buildBudgetContext(walletId));
            
            // Build comparison context (current vs previous month)
            context.setComparisonContext(buildComparisonContext(userId, walletId));
            
            // Build trend context
            context.setTrendContext(buildTrendContext(userId, walletId));
            
            Log.d(TAG, "Built context: " + context.getSummary());
            
        } catch (Exception e) {
            Log.e(TAG, "Error building context", e);
        }
        
        return context;
    }
    
    /**
     * Build summary of current financial status.
     */
    private String buildFinancialSummary(int userId, int walletId) {
        StringBuilder summary = new StringBuilder();
        
        // Get current month date range
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long monthStart = cal.getTimeInMillis();
        
        cal.add(Calendar.MONTH, 1);
        long monthEnd = cal.getTimeInMillis();
        
        // Get wallet info
        Wallet wallet = database.walletDao().getWalletById(walletId);
        if (wallet != null) {
            summary.append(String.format("💰 Số dư ví hiện tại: %,.0f VNĐ\n", wallet.getBalance()));
        }
        
        // Get current month totals
        Double totalExpenses = database.transactionDao().getTotalExpensesByDateRange(userId, monthStart, monthEnd);
        Double totalIncome = database.transactionDao().getTotalIncomeByDateRange(userId, monthStart, monthEnd);
        
        summary.append(String.format("📅 Tháng này:\n"));
        summary.append(String.format("  • Chi tiêu: %,.0f VNĐ\n", totalExpenses != null ? totalExpenses : 0));
        summary.append(String.format("  • Thu nhập: %,.0f VNĐ\n", totalIncome != null ? totalIncome : 0));
        
        // Get top spending categories this month
        List<CategoryTotal> topCategories = database.transactionDao()
                .getTopExpensesByYear(userId, walletId, monthStart, monthEnd);
        
        if (topCategories != null && !topCategories.isEmpty()) {
            summary.append("📊 Top chi tiêu tháng này:\n");
            for (int i = 0; i < Math.min(3, topCategories.size()); i++) {
                CategoryTotal cat = topCategories.get(i);
                summary.append(String.format("  • %s: %,.0f VNĐ\n", cat.category, cat.total));
            }
        }
        
        return summary.toString();
    }
    
    /**
     * Build budget status context.
     */
    private String buildBudgetContext(int walletId) {
        StringBuilder budgetContext = new StringBuilder();
        
        List<Budget> budgets = database.budgetDao().getBudgetsByWalletId(walletId);
        if (budgets == null || budgets.isEmpty()) {
            return "";
        }
        
        budgetContext.append("[TÌNH TRẠNG NGÂN SÁCH]\n");
        
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        long now = System.currentTimeMillis();
        
        for (Budget budget : budgets) {
            try {
                // Parse end date string to check if budget is still active
                String endDateStr = budget.getEndDate();
                String startDateStr = budget.getStartDate();
                
                if (endDateStr == null || startDateStr == null) continue;
                
                long endDate = sdf.parse(endDateStr).getTime();
                long startDate = sdf.parse(startDateStr).getTime();
                
                // Skip expired budgets
                if (now > endDate) continue;
                
                // Get actual spending for this budget period
                double spent;
                if (budget.getCategoryId() != null) {
                    spent = database.transactionDao().getTotalExpenseBetweenForWalletAndCategory(
                            startDate, endDate, walletId, budget.getCategoryId());
                } else {
                    spent = database.transactionDao().getTotalExpenseBetweenForWallet(
                            startDate, endDate, walletId);
                }
                
                double budgetAmount = budget.getBudgetAmount();
                double percentUsed = budgetAmount > 0 ? (spent / budgetAmount) * 100 : 0;
                
                String status = percentUsed >= 100 ? "⚠️ VƯỢT" : 
                               percentUsed >= 80 ? "🟡 SẮP HẾT" : "✅ ỔN";
                
                String categoryName = "Tổng";
                if (budget.getCategoryId() != null) {
                    Category cat = database.categoryDao().getCategoryById(budget.getCategoryId());
                    categoryName = cat != null ? cat.getName() : "Danh mục";
                }
                
                budgetContext.append(String.format("  • %s: %,.0f/%,.0f VNĐ (%.0f%%) %s\n",
                        categoryName, spent, budgetAmount, percentUsed, status));
                        
            } catch (Exception e) {
                Log.w(TAG, "Error parsing budget dates for budget: " + budget.getName(), e);
            }
        }
        
        return budgetContext.toString();
    }
    
    /**
     * Build comparison context (current vs previous month).
     */
    private String buildComparisonContext(int userId, int walletId) {
        StringBuilder comparison = new StringBuilder();
        
        // Current month
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        long currentMonthStart = cal.getTimeInMillis();
        
        cal.add(Calendar.MONTH, 1);
        long currentMonthEnd = cal.getTimeInMillis();
        
        // Previous month
        cal.add(Calendar.MONTH, -2);
        long prevMonthStart = cal.getTimeInMillis();
        cal.add(Calendar.MONTH, 1);
        long prevMonthEnd = cal.getTimeInMillis();
        
        // Get totals
        Double currentExpenses = database.transactionDao().getTotalExpensesByDateRange(userId, currentMonthStart, currentMonthEnd);
        Double prevExpenses = database.transactionDao().getTotalExpensesByDateRange(userId, prevMonthStart, prevMonthEnd);
        
        double current = currentExpenses != null ? currentExpenses : 0;
        double prev = prevExpenses != null ? prevExpenses : 0;
        
        if (prev > 0) {
            double change = ((current - prev) / prev) * 100;
            String trend = change > 0 ? "tăng" : "giảm";
            String emoji = change > 0 ? "📈" : "📉";
            
            comparison.append(String.format("%s So với tháng trước: Chi tiêu %s %.1f%%\n", 
                    emoji, trend, Math.abs(change)));
            comparison.append(String.format("  • Tháng trước: %,.0f VNĐ\n", prev));
            comparison.append(String.format("  • Tháng này: %,.0f VNĐ\n", current));
        }
        
        return comparison.toString();
    }
    
    /**
     * Build spending trend context (last 3 months).
     */
    private String buildTrendContext(int userId, int walletId) {
        StringBuilder trend = new StringBuilder();
        
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        
        // Get 3-month totals
        double[] monthlyTotals = new double[3];
        String[] monthNames = new String[3];
        SimpleDateFormat monthFormat = new SimpleDateFormat("MM/yyyy", Locale.getDefault());
        
        for (int i = 0; i < 3; i++) {
            long monthStart = cal.getTimeInMillis();
            monthNames[2 - i] = monthFormat.format(new Date(monthStart));
            
            cal.add(Calendar.MONTH, 1);
            long monthEnd = cal.getTimeInMillis();
            
            Double expenses = database.transactionDao().getTotalExpensesByDateRange(userId, monthStart, monthEnd);
            monthlyTotals[2 - i] = expenses != null ? expenses : 0;
            
            cal.add(Calendar.MONTH, -2); // Move back
        }
        
        // Determine trend
        if (monthlyTotals[0] > 0 && monthlyTotals[1] > 0 && monthlyTotals[2] > 0) {
            boolean increasing = monthlyTotals[2] > monthlyTotals[1] && monthlyTotals[1] > monthlyTotals[0];
            boolean decreasing = monthlyTotals[2] < monthlyTotals[1] && monthlyTotals[1] < monthlyTotals[0];
            
            if (increasing) {
                trend.append("📈 Xu hướng: Chi tiêu đang TĂNG liên tục 3 tháng\n");
            } else if (decreasing) {
                trend.append("📉 Xu hướng: Chi tiêu đang GIẢM liên tục 3 tháng\n");
            } else {
                trend.append("↔️ Xu hướng: Chi tiêu ổn định\n");
            }
            
            // Show 3-month data
            double avg = (monthlyTotals[0] + monthlyTotals[1] + monthlyTotals[2]) / 3;
            trend.append(String.format("  • Trung bình 3 tháng: %,.0f VNĐ/tháng\n", avg));
        }
        
        return trend.toString();
    }
    
    /**
     * Get spending by category for a specific time range.
     */
    public Map<String, Double> getCategorySpending(int userId, int walletId, long startDate, long endDate) {
        Map<String, Double> categorySpending = new HashMap<>();
        
        List<CategoryTotal> categories = database.transactionDao()
                .getExpensesByDateRange(userId, walletId, startDate, endDate);
        
        if (categories != null) {
            for (CategoryTotal cat : categories) {
                categorySpending.put(cat.category, cat.total);
            }
        }
        
        return categorySpending;
    }
    
    /**
     * Get average daily spending for current month.
     */
    public double getAverageDailySpending(int userId, int walletId) {
        Calendar cal = Calendar.getInstance();
        int dayOfMonth = cal.get(Calendar.DAY_OF_MONTH);
        
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        long monthStart = cal.getTimeInMillis();
        
        long now = System.currentTimeMillis();
        
        Double totalExpenses = database.transactionDao().getTotalExpensesByDateRange(userId, monthStart, now);
        
        if (totalExpenses == null || totalExpenses == 0 || dayOfMonth == 0) {
            return 0;
        }
        
        return totalExpenses / dayOfMonth;
    }
    
    /**
     * Detect query language (simple heuristic).
     */
    private String detectLanguage(String query) {
        // Check for Vietnamese diacritics
        String vietnameseChars = "àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđ";
        for (char c : query.toLowerCase().toCharArray()) {
            if (vietnameseChars.indexOf(c) >= 0) {
                return "vi";
            }
        }
        return "en";
    }
    
    /**
     * Build context specifically for a category query.
     */
    public String buildCategoryContext(int userId, int walletId, String categoryName) {
        StringBuilder context = new StringBuilder();
        
        Category category = database.categoryDao().getCategoryByName(categoryName);
        if (category == null) return "";
        
        // Current month spending for category
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        long monthStart = cal.getTimeInMillis();
        
        double spending = database.transactionDao().getTotalExpenseByCategorySinceForUser(
                categoryName, monthStart, userId);
        
        context.append(String.format("📌 Chi tiêu %s tháng này: %,.0f VNĐ\n", categoryName, spending));
        
        // Get recent transactions in this category
        List<Transaction> recentTxns = database.transactionDao()
                .getTransactionsByCategoryId(category.getId());
        
        if (recentTxns != null && !recentTxns.isEmpty()) {
            context.append("Giao dịch gần đây:\n");
            int count = 0;
            for (Transaction txn : recentTxns) {
                if (count >= 3) break;
                context.append(String.format("  • %s: %,.0f VNĐ\n", 
                        dateFormat.format(new Date(txn.getCreatedAt())), txn.getAmount()));
                count++;
            }
        }
        
        return context.toString();
    }
}
