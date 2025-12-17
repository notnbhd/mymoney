package com.example.mymoney.budget;

import android.content.Context;

import com.example.mymoney.BudgetAdapter;
import com.example.mymoney.database.AppDatabase;
import com.example.mymoney.database.dao.BudgetDao;
import com.example.mymoney.database.dao.CategoryDao;
import com.example.mymoney.database.dao.TransactionDao;
import com.example.mymoney.database.entity.Budget;
import com.example.mymoney.database.entity.Category;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Provides budget context for AI chatbot integration.
 * Runs rule-based analysis and formats results for LLM consumption.
 */
public class BudgetContextProvider {

    public interface BudgetContextCallback {
        void onContextReady(BudgetRuleEngine.BudgetAnalysisResult result);
        void onError(String message);
    }

    private final Context context;
    private final ExecutorService executor;

    public BudgetContextProvider(Context context) {
        this.context = context;
        this.executor = Executors.newSingleThreadExecutor();
    }

    /**
     * Analyze budgets for a specific wallet and return results asynchronously
     */
    public void analyzeBudgetsForWallet(int walletId, BudgetContextCallback callback) {
        executor.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(context);
                BudgetDao budgetDao = db.budgetDao();
                TransactionDao transactionDao = db.transactionDao();
                CategoryDao categoryDao = db.categoryDao();

                // Get all budgets for this wallet
                List<Budget> budgets = budgetDao.getBudgetsByWalletId(walletId);

                if (budgets.isEmpty()) {
                    callback.onContextReady(null);
                    return;
                }

                // Build category name map for translation
                Map<Integer, String> categoryNames = new HashMap<>();
                List<Category> categories = categoryDao.getAllCategories();
                for (Category category : categories) {
                    categoryNames.put(category.getId(), category.getName());
                }

                // Calculate spent amounts for each budget (category-aware)
                Map<Integer, Double> spentAmounts = new HashMap<>();
                for (Budget budget : budgets) {
                    long[] periodRange = BudgetAdapter.calculatePeriodRange(budget);
                    double spent;
                    if (budget.getCategoryId() != null) {
                        // Category-specific budget: only count expenses in that category
                        spent = transactionDao.getTotalExpenseBetweenForWalletAndCategory(
                                periodRange[0], periodRange[1], walletId, budget.getCategoryId());
                    } else {
                        // Global budget: count all expenses
                        spent = transactionDao.getTotalExpenseBetweenForWallet(
                                periodRange[0], periodRange[1], walletId);
                    }
                    spentAmounts.put(budget.getId(), spent);
                }

                // Run rule-based analysis with category names for translation
                BudgetRuleEngine.BudgetAnalysisResult result =
                        BudgetRuleEngine.analyzeBudgets(budgets, spentAmounts, categoryNames);

                callback.onContextReady(result);

            } catch (Exception e) {
                callback.onError("Failed to analyze budgets: " + e.getMessage());
            }
        });
    }

    /**
     * Synchronous version for use in background threads
     */
    public BudgetRuleEngine.BudgetAnalysisResult analyzeBudgetsSync(int walletId) {
        try {
            AppDatabase db = AppDatabase.getInstance(context);
            BudgetDao budgetDao = db.budgetDao();
            TransactionDao transactionDao = db.transactionDao();
            CategoryDao categoryDao = db.categoryDao();

            List<Budget> budgets = budgetDao.getBudgetsByWalletId(walletId);

            if (budgets.isEmpty()) {
                return null;
            }

            // Build category name map for translation
            Map<Integer, String> categoryNames = new HashMap<>();
            List<Category> categories = categoryDao.getAllCategories();
            for (Category category : categories) {
                categoryNames.put(category.getId(), category.getName());
            }

            // Category-aware expense calculation
            Map<Integer, Double> spentAmounts = new HashMap<>();
            for (Budget budget : budgets) {
                long[] periodRange = BudgetAdapter.calculatePeriodRange(budget);
                double spent;
                if (budget.getCategoryId() != null) {
                    // Category-specific budget: only count expenses in that category
                    spent = transactionDao.getTotalExpenseBetweenForWalletAndCategory(
                            periodRange[0], periodRange[1], walletId, budget.getCategoryId());
                } else {
                    // Global budget: count all expenses
                    spent = transactionDao.getTotalExpenseBetweenForWallet(
                            periodRange[0], periodRange[1], walletId);
                }
                spentAmounts.put(budget.getId(), spent);
            }

            // Run rule-based analysis with category names for translation
            return BudgetRuleEngine.analyzeBudgets(budgets, spentAmounts, categoryNames);

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Build a prompt enhancement for the LLM based on budget analysis
     */
    public static String buildPromptEnhancement(BudgetRuleEngine.BudgetAnalysisResult result) {
        if (result == null) {
            return "Người dùng chưa thiết lập ngân sách nào.";
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("\n\n--- NGỮ CẢNH NGÂN SÁCH (từ phân tích quy tắc) ---\n");
        prompt.append(result.summaryForLLM);
        prompt.append("\n\nDựa trên phân tích này, hãy đưa ra lời khuyên cá nhân hóa:\n");
        prompt.append("1. Giải quyết các vấn đề nghiêm trọng hoặc cảnh báo trước\n");
        prompt.append("2. Đưa ra đề xuất chi tiêu cụ thể, có thể thực hiện được\n");
        prompt.append("3. Ghi nhận tiến bộ tích cực nếu có\n");
        prompt.append("4. Gợi ý cách tiết kiệm theo từng danh mục\n");
        prompt.append("5. Sử dụng ngôn ngữ khích lệ và hỗ trợ\n");
        prompt.append("--- KẾT THÚC NGỮ CẢNH ---\n\n");

        return prompt.toString();
    }
}